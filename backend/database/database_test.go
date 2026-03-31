package main

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	_ "modernc.org/sqlite"
)

func setupDatabaseTestEnv(t *testing.T) func() {
	t.Helper()
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd failed: %v", err)
	}
	tmp := t.TempDir()
	if err := os.Chdir(tmp); err != nil {
		t.Fatalf("chdir failed: %v", err)
	}

	authDB, err := sql.Open("sqlite", AuthDB)
	if err != nil {
		t.Fatalf("open auth db failed: %v", err)
	}
	_, err = authDB.Exec(`
		CREATE TABLE users (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			name TEXT,
			username TEXT UNIQUE,
			email TEXT UNIQUE,
			password_hash TEXT,
			salt TEXT
		);
	`)
	if err != nil {
		t.Fatalf("create users failed: %v", err)
	}
	_, err = authDB.Exec(`INSERT INTO users(name, username, email, password_hash, salt) VALUES ('John','john','j@e.com','hash1','salt1')`)
	if err != nil {
		t.Fatalf("seed users failed: %v", err)
	}
	_ = authDB.Close()

	raceDB, err := sql.Open("sqlite", RaceDB)
	if err != nil {
		t.Fatalf("open race db failed: %v", err)
	}
	_, err = raceDB.Exec(`
		CREATE TABLE dog_races (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			name TEXT UNIQUE,
			folderName TEXT,
			timesSearched INTEGER DEFAULT 0,
			timesQuestioned INTEGER DEFAULT 0
		);
	`)
	if err != nil {
		t.Fatalf("create dog_races failed: %v", err)
	}
	_, err = raceDB.Exec(`INSERT INTO dog_races(name, folderName, timesSearched, timesQuestioned) VALUES ('beagle','beagle',0,0)`)
	if err != nil {
		t.Fatalf("seed dog_races failed: %v", err)
	}
	_ = raceDB.Close()

	breedDir := filepath.Join("racesFolder", "beagle")
	if err := os.MkdirAll(breedDir, 0o755); err != nil {
		t.Fatalf("mkdir breed dir failed: %v", err)
	}
	if err := os.WriteFile(filepath.Join(breedDir, "summary"), []byte("FullName: Beagle Dog\nDescription: Friendly"), 0o644); err != nil {
		t.Fatalf("write summary failed: %v", err)
	}
	if err := os.WriteFile(filepath.Join(breedDir, "mainPhoto.jpg"), []byte{1, 2, 3, 4}, 0o644); err != nil {
		t.Fatalf("write photo failed: %v", err)
	}

	return func() {
		_ = os.Chdir(cwd)
	}
}

func TestDatabaseFunctions(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	salt, err := requestSaltFromDB("john")
	if err != nil || salt != "salt1" {
		t.Fatalf("requestSaltFromDB failed: %v salt=%s", err, salt)
	}
	_, err = requestSaltFromDB("missing")
	if err == nil {
		t.Fatalf("expected error for missing user")
	}

	ok, err := verifyPasswordFromDB("john", "hash1")
	if err != nil || !ok {
		t.Fatalf("verifyPasswordFromDB true failed: %v ok=%v", err, ok)
	}
	ok, err = verifyPasswordFromDB("john", "wrong")
	if err != nil || ok {
		t.Fatalf("verifyPasswordFromDB false failed: %v ok=%v", err, ok)
	}

	taken, err := isUsernameOrEmailTakenFromDB("john")
	if err != nil || !taken {
		t.Fatalf("isUsernameOrEmailTaken true failed: %v taken=%v", err, taken)
	}
	taken, err = isUsernameOrEmailTakenFromDB("new")
	if err != nil || taken {
		t.Fatalf("isUsernameOrEmailTaken false failed: %v taken=%v", err, taken)
	}

	err = registerUserInDB("Alice", "alice", "a@e.com", "h", "s")
	if err != nil {
		t.Fatalf("registerUserInDB failed: %v", err)
	}
	err = registerUserInDB("Alice2", "alice", "a2@e.com", "h", "s")
	if err == nil {
		t.Fatalf("expected duplicate registration error")
	}

	race, err := getDogRaceInfoFromDB(nil, "beagle")
	if err != nil || race.Name != "beagle" {
		t.Fatalf("getDogRaceInfoFromDB by name failed: %v race=%+v", err, race)
	}
	id := race.ID
	race, err = getDogRaceInfoFromDB(&id, "")
	if err != nil || race.ID != id {
		t.Fatalf("getDogRaceInfoFromDB by id failed: %v race=%+v", err, race)
	}
	_, err = getDogRaceInfoFromDB(nil, "")
	if err == nil {
		t.Fatalf("expected invalid request data error")
	}

	if err := incrementTimesSearchedInDB(id); err != nil {
		t.Fatalf("incrementTimesSearchedInDB failed: %v", err)
	}
	if err := incrementQuestionedCountInDB("beagle"); err != nil {
		t.Fatalf("incrementQuestionedCountInDB failed: %v", err)
	}
	if err := addRaceToDB("pug", "pug"); err != nil {
		t.Fatalf("addRaceToDB failed: %v", err)
	}
	if err := addRaceToDB("pug", "pug2"); err == nil {
		t.Fatalf("expected addRaceToDB duplicate error")
	}
}

func TestHandleRequestSalt(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	req := httptest.NewRequest(http.MethodPost, "/requestSalt", bytes.NewBufferString(`{"usernameData":{"username":"john"}}`))
	w := httptest.NewRecorder()
	handleRequestSalt(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/requestSalt", bytes.NewBufferString(`bad`))
	w = httptest.NewRecorder()
	handleRequestSalt(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/requestSalt", bytes.NewBufferString(`{"usernameData":{"username":"none"}}`))
	w = httptest.NewRecorder()
	handleRequestSalt(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodGet, "/requestSalt", nil)
	w = httptest.NewRecorder()
	handleRequestSalt(w, req)
	if w.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected 405 got %d", w.Code)
	}
}

func TestHandleVerifyAndTakenAndRegister(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	verifyReq := httptest.NewRequest(http.MethodPost, "/verifyUsernamePassword", bytes.NewBufferString(`{"loginData":{"username":"john","password":"hash1"}}`))
	w := httptest.NewRecorder()
	handleVerifyUsernamePassword(w, verifyReq)
	if w.Code != http.StatusOK {
		t.Fatalf("verify expected 200 got %d", w.Code)
	}

	verifyReq = httptest.NewRequest(http.MethodPost, "/verifyUsernamePassword", bytes.NewBufferString(`{"loginData":{"username":"john","password":"wrong"}}`))
	w = httptest.NewRecorder()
	handleVerifyUsernamePassword(w, verifyReq)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("verify expected 401 got %d", w.Code)
	}

	takenReq := httptest.NewRequest(http.MethodPost, "/isUsernameOrEmailTaken", bytes.NewBufferString(`{"usernameData":{"username":"john"}}`))
	w = httptest.NewRecorder()
	handleIsUsernameOrEmailTaken(w, takenReq)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "true") {
		t.Fatalf("taken expected true, got code=%d body=%s", w.Code, w.Body.String())
	}

	regReq := httptest.NewRequest(http.MethodPost, "/registerUser", bytes.NewBufferString(`{"registrationData":{"name":"N","username":"nuser","email":"n@e.com","password_hash":"h","salt":"s"}}`))
	w = httptest.NewRecorder()
	handleRegisterUser(w, regReq)
	if w.Code != http.StatusCreated {
		t.Fatalf("register expected 201 got %d", w.Code)
	}

	dupReq := httptest.NewRequest(http.MethodPost, "/registerUser", bytes.NewBufferString(`{"registrationData":{"name":"N","username":"john","email":"x@e.com","password_hash":"h","salt":"s"}}`))
	w = httptest.NewRecorder()
	handleRegisterUser(w, dupReq)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("dup register expected 400 got %d", w.Code)
	}
}

func TestHandleRaceHandlers(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	nameReq := httptest.NewRequest(http.MethodPost, "/getDogRaceInfo", bytes.NewBufferString(`{"raceRequestData":{"name":"beagle","confidence":0.8}}`))
	w := httptest.NewRecorder()
	handleGetDogRaceInfo(w, nameReq)
	if w.Code != http.StatusOK {
		t.Fatalf("race by name expected 200 got %d", w.Code)
	}
	if !strings.Contains(w.Header().Get("Content-Type"), "multipart/mixed") {
		t.Fatalf("expected multipart response, got %s", w.Header().Get("Content-Type"))
	}

	// Remove photo to hit JSON-only branch
	_ = os.Remove(filepath.Join("racesFolder", "beagle", "mainPhoto.jpg"))
	nameReq = httptest.NewRequest(http.MethodPost, "/getDogRaceInfo", bytes.NewBufferString(`{"raceRequestData":{"name":"beagle","confidence":0.8}}`))
	w = httptest.NewRecorder()
	handleGetDogRaceInfo(w, nameReq)
	if w.Code != http.StatusOK || !strings.Contains(w.Header().Get("Content-Type"), "application/json") {
		t.Fatalf("expected json branch got code=%d ct=%s", w.Code, w.Header().Get("Content-Type"))
	}

	badReq := httptest.NewRequest(http.MethodPost, "/getDogRaceInfo", bytes.NewBufferString(`{"raceRequestData":{}}`))
	w = httptest.NewRecorder()
	handleGetDogRaceInfo(w, badReq)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for invalid query got %d", w.Code)
	}

	addReq := httptest.NewRequest(http.MethodPost, "/addDogRace", bytes.NewBufferString(`{"raceData":{"name":"husky","folderName":"husky"}}`))
	w = httptest.NewRecorder()
	handleAddDogRace(w, addReq)
	if w.Code != http.StatusCreated {
		t.Fatalf("add race expected 201 got %d", w.Code)
	}

	incReq := httptest.NewRequest(http.MethodPost, "/incrementDogRaceQuestionedCount", bytes.NewBufferString(`{"raceNameData":{"raceName":"beagle"}}`))
	w = httptest.NewRecorder()
	handleIncrementDogRaceQuestionedCount(w, incReq)
	if w.Code != http.StatusOK {
		t.Fatalf("increment expected 200 got %d", w.Code)
	}

	badJSON := httptest.NewRequest(http.MethodPost, "/incrementDogRaceQuestionedCount", bytes.NewBufferString(`bad`))
	w = httptest.NewRecorder()
	handleIncrementDogRaceQuestionedCount(w, badJSON)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %d", w.Code)
	}
}

func TestMainAndRootDatabase(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	w := httptest.NewRecorder()
	handleRoot(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/", nil)
	w = httptest.NewRecorder()
	handleRoot(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %d", w.Code)
	}

	oldStart := startServer
	oldFatal := fatalf
	defer func() {
		startServer = oldStart
		fatalf = oldFatal
	}()

	called := false
	startServer = func(addr string, handler http.Handler) error {
		called = true
		if addr != ":8020" {
			t.Fatalf("unexpected addr %s", addr)
		}
		return errors.New("stop")
	}
	fatalf = func(v ...interface{}) {}

	main()
	if !called {
		t.Fatalf("expected startServer to be called")
	}
}

func TestHandlerMethodAndJSONErrorsDatabase(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	cases := []struct {
		name    string
		handler func(http.ResponseWriter, *http.Request)
		method  string
		body    string
		status  int
	}{
		{"verify method", handleVerifyUsernamePassword, http.MethodGet, "", http.StatusMethodNotAllowed},
		{"verify bad json", handleVerifyUsernamePassword, http.MethodPost, "bad", http.StatusBadRequest},
		{"taken method", handleIsUsernameOrEmailTaken, http.MethodGet, "", http.StatusMethodNotAllowed},
		{"taken bad json", handleIsUsernameOrEmailTaken, http.MethodPost, "bad", http.StatusBadRequest},
		{"register method", handleRegisterUser, http.MethodGet, "", http.StatusMethodNotAllowed},
		{"register bad json", handleRegisterUser, http.MethodPost, "bad", http.StatusBadRequest},
		{"race method", handleGetDogRaceInfo, http.MethodGet, "", http.StatusMethodNotAllowed},
		{"race bad json", handleGetDogRaceInfo, http.MethodPost, "bad", http.StatusBadRequest},
		{"add method", handleAddDogRace, http.MethodGet, "", http.StatusMethodNotAllowed},
		{"add bad json", handleAddDogRace, http.MethodPost, "bad", http.StatusBadRequest},
		{"inc method", handleIncrementDogRaceQuestionedCount, http.MethodGet, "", http.StatusMethodNotAllowed},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, "/x", bytes.NewBufferString(tc.body))
			w := httptest.NewRecorder()
			tc.handler(w, req)
			if w.Code != tc.status {
				t.Fatalf("expected %d got %d body=%s", tc.status, w.Code, w.Body.String())
			}
		})
	}
}

func TestRaceInfoIDQueryPath(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	id := 1
	payload := map[string]interface{}{"raceRequestData": map[string]interface{}{"raceId": id}}
	b, _ := json.Marshal(payload)
	req := httptest.NewRequest(http.MethodPost, "/getDogRaceInfo", bytes.NewReader(b))
	w := httptest.NewRecorder()
	handleGetDogRaceInfo(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d", w.Code)
	}
}

func TestDatabaseAdditionalErrorBranches(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	// Duplicate race insertion triggers handler error branch.
	req := httptest.NewRequest(http.MethodPost, "/addDogRace", bytes.NewBufferString(`{"raceData":{"name":"beagle","folderName":"beagle2"}}`))
	w := httptest.NewRecorder()
	handleAddDogRace(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %d", w.Code)
	}

	// Force DB error in increment handler by dropping table.
	db, err := sql.Open("sqlite", RaceDB)
	if err != nil {
		t.Fatalf("open race db failed: %v", err)
	}
	_, _ = db.Exec("DROP TABLE dog_races")
	_ = db.Close()

	req = httptest.NewRequest(http.MethodPost, "/incrementDogRaceQuestionedCount", bytes.NewBufferString(`{"raceNameData":{"raceName":"beagle"}}`))
	w = httptest.NewRecorder()
	handleIncrementDogRaceQuestionedCount(w, req)
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 got %d", w.Code)
	}
}

func TestHandleIsTakenDatabaseError(t *testing.T) {
	teardown := setupDatabaseTestEnv(t)
	defer teardown()

	auth, err := sql.Open("sqlite", AuthDB)
	if err != nil {
		t.Fatalf("open auth db failed: %v", err)
	}
	_, _ = auth.Exec("DROP TABLE users")
	_ = auth.Close()

	req := httptest.NewRequest(http.MethodPost, "/isUsernameOrEmailTaken", bytes.NewBufferString(`{"usernameData":{"username":"john"}}`))
	w := httptest.NewRecorder()
	handleIsUsernameOrEmailTaken(w, req)
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 got %d", w.Code)
	}
}
