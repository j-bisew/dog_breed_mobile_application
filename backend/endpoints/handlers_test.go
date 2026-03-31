package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func withIPs(auth, db, recog string) func() {
	oldAuth, oldDB, oldRec := authIP, databaseIP, recognitionIP
	authIP, databaseIP, recognitionIP = auth, db, recog
	return func() {
		authIP, databaseIP, recognitionIP = oldAuth, oldDB, oldRec
	}
}

func addrFromURL(u string) string {
	return strings.TrimPrefix(u, "http://")
}

func makePhotoMultipart(t *testing.T, field string, data []byte) (string, []byte) {
	t.Helper()
	var b bytes.Buffer
	w := multipart.NewWriter(&b)
	fw, err := w.CreateFormFile(field, "photo.jpg")
	if err != nil {
		t.Fatalf("CreateFormFile failed: %v", err)
	}
	_, _ = fw.Write(data)
	_ = w.Close()
	return w.FormDataContentType(), b.Bytes()
}

func TestHandleRootEndpoints(t *testing.T) {
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
}

func TestRegisterAndLoginHandlers(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/register":
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte("ok"))
		case "/login":
			w.WriteHeader(http.StatusUnauthorized)
			_, _ = w.Write([]byte("bad creds"))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer authSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodPost, "/registerUser", bytes.NewBufferString(`{"registrationData":{"name":"n","username":"u","email":"e","password":"p"}}`))
	w := httptest.NewRecorder()
	handleRegisterUser(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/loginUser", bytes.NewBufferString(`{"loginData":{"username":"u","password":"p"}}`))
	w = httptest.NewRecorder()
	handleLoginUser(w, req)
	if w.Code != http.StatusUnauthorized || !strings.Contains(w.Body.String(), "bad creds") {
		t.Fatalf("expected 401 propagated, got code=%d body=%s", w.Code, w.Body.String())
	}

	req = httptest.NewRequest(http.MethodGet, "/loginUser", nil)
	w = httptest.NewRecorder()
	handleLoginUser(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/registerUser", bytes.NewBufferString(`bad`))
	w = httptest.NewRecorder()
	handleRegisterUser(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %d", w.Code)
	}
}

func TestAuthorizationCheck(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/verifyToken" {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte("ok"))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer authSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodPost, "/x", nil)
	req.Header.Set("Authorization", "Bearer token")
	if !authorizationCheck(req) {
		t.Fatalf("expected authorizationCheck true")
	}

	req = httptest.NewRequest(http.MethodPost, "/x", nil)
	if authorizationCheck(req) {
		t.Fatalf("expected false when header missing")
	}
}

func TestGetDogBreedInfoValidationPaths(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()
	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodGet, "/getDogBreedInfo", nil)
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", nil)
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", nil)
	req.Header.Set("Authorization", "Bearer t")
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 content-type missing got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewBufferString("x"))
	req.Header.Set("Authorization", "Bearer t")
	req.Header.Set("Content-Type", "application/json")
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 unsupported type got %d", w.Code)
	}
}

func TestRegisterLoginUnavailableAndSubmitPropagation(t *testing.T) {
	restore := withIPs("127.0.0.1:1", "127.0.0.1:1", recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodPost, "/registerUser", bytes.NewBufferString(`{"registrationData":{"name":"n","username":"u","email":"e","password":"p"}}`))
	w := httptest.NewRecorder()
	handleRegisterUser(w, req)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/loginUser", bytes.NewBufferString(`{"loginData":{"username":"u","password":"p"}}`))
	w = httptest.NewRecorder()
	handleLoginUser(w, req)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 got %d", w.Code)
	}

	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()
	dbSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusConflict)
		_, _ = w.Write([]byte("conflict"))
	}))
	defer dbSrv.Close()
	restore2 := withIPs(addrFromURL(authSrv.URL), addrFromURL(dbSrv.URL), recognitionIP)
	defer restore2()

	req = httptest.NewRequest(http.MethodPost, "/submitDogBreedFeedback", bytes.NewBufferString(`{"raceNameData":{"raceName":"beagle"}}`))
	req.Header.Set("Authorization", "Bearer token")
	w = httptest.NewRecorder()
	handleSubmitDogBreedFeedback(w, req)
	if w.Code != http.StatusConflict || !strings.Contains(w.Body.String(), "conflict") {
		t.Fatalf("expected propagated 409, got code=%d body=%s", w.Code, w.Body.String())
	}
}

func TestGetDogBreedInfoHappyAndProxyErrors(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	}))
	defer authSrv.Close()

	recogSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/submitDogPhoto" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"breedName":"beagle","confidence":"0.9"}`))
	}))
	defer recogSrv.Close()

	dbSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/getDogRaceInfo" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"breedName":"beagle"}`))
	}))
	defer dbSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), addrFromURL(dbSrv.URL), addrFromURL(recogSrv.URL))
	defer restore()

	ct, body := makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req := httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", ct)
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Header().Get("Content-Type"), "application/json") {
		t.Fatalf("expected propagated content-type, got %s", w.Header().Get("Content-Type"))
	}

	// Recognition non-200 should be propagated.
	badRecog := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("bad photo"))
	}))
	defer badRecog.Close()
	restore2 := withIPs(addrFromURL(authSrv.URL), addrFromURL(dbSrv.URL), addrFromURL(badRecog.URL))
	defer restore2()

	ct, body = makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", ct)
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 from recognition got %d", w.Code)
	}
}

func TestGetDogBreedInfoAdditionalBranches(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()
	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, recognitionIP)
	defer restore()

	// No photo field
	var b bytes.Buffer
	mw := multipart.NewWriter(&b)
	fw, _ := mw.CreateFormField("notphoto")
	_, _ = fw.Write([]byte("x"))
	_ = mw.Close()

	req := httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(b.Bytes()))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", mw.FormDataContentType())
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for missing photo got %d", w.Code)
	}

	// Oversized request
	huge := bytes.Repeat([]byte("a"), 21*1024*1024)
	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(huge))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", "multipart/form-data; boundary=abc")
	req.ContentLength = int64(len(huge))
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("expected 413 got %d", w.Code)
	}
}

func TestSubmitFeedbackPaths(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()

	dbSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/incrementDogRaceQuestionedCount" {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte("ok"))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer dbSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), addrFromURL(dbSrv.URL), recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodPost, "/submitDogBreedFeedback", bytes.NewBufferString(`{"raceNameData":{"raceName":"beagle"}}`))
	req.Header.Set("Authorization", "Bearer token")
	w := httptest.NewRecorder()
	handleSubmitDogBreedFeedback(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/submitDogBreedFeedback", bytes.NewBufferString(`bad`))
	req.Header.Set("Authorization", "Bearer token")
	w = httptest.NewRecorder()
	handleSubmitDogBreedFeedback(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/submitDogBreedFeedback", nil)
	w = httptest.NewRecorder()
	handleSubmitDogBreedFeedback(w, req)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 got %d", w.Code)
	}

	req = httptest.NewRequest(http.MethodGet, "/submitDogBreedFeedback", nil)
	w = httptest.NewRecorder()
	handleSubmitDogBreedFeedback(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %d", w.Code)
	}
}

func TestPostJSONAndMainEndpoints(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte("payload"))
	}))
	defer srv.Close()

	resp, body, err := postJSON(srv.URL, []byte(`{"a":1}`))
	if err != nil || resp.StatusCode != http.StatusCreated || string(body) != "payload" {
		t.Fatalf("postJSON unexpected result: err=%v code=%d body=%s", err, resp.StatusCode, string(body))
	}
	copyBody, _ := io.ReadAll(resp.Body)
	if string(copyBody) != "payload" {
		t.Fatalf("expected resp body reset")
	}

	_, _, err = postJSON("http://127.0.0.1:1", []byte("{}"))
	if err == nil {
		t.Fatalf("expected network error")
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
		if addr != ":8000" {
			t.Fatalf("unexpected addr %s", addr)
		}
		return errors.New("stop")
	}
	fatalf = func(v ...interface{}) {}
	main()
	if !called {
		t.Fatalf("expected startServer call")
	}
}

func TestGetDogBreedInfoInvalidRecogJSONAndDBUnavailable(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()

	badJSONRecog := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("not-json"))
	}))
	defer badJSONRecog.Close()

	restore := withIPs(addrFromURL(authSrv.URL), "127.0.0.1:1", addrFromURL(badJSONRecog.URL))
	defer restore()

	ct, body := makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req := httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", ct)
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 for invalid recog json got %d", w.Code)
	}

	goodRecog := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{"breedName": "beagle", "confidence": "0.7"})
	}))
	defer goodRecog.Close()

	restore2 := withIPs(addrFromURL(authSrv.URL), "127.0.0.1:1", addrFromURL(goodRecog.URL))
	defer restore2()

	ct, body = makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", ct)
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 for db unavailable got %d", w.Code)
	}
}

func TestGetDogBreedInfoDBFallbackAndPropagation(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()

	recogSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"breedName":"","confidence":null}`))
	}))
	defer recogSrv.Close()

	// No body from DB should keep Content-Type empty and trigger fallback in handler.
	dbSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer dbSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), addrFromURL(dbSrv.URL), addrFromURL(recogSrv.URL))
	defer restore()

	ct, body := makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req := httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "token-only")
	req.Header.Set("Content-Type", ct)
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 got %d", w.Code)
	}
	if !strings.Contains(w.Header().Get("Content-Type"), "application/json") {
		t.Fatalf("expected fallback application/json got %s", w.Header().Get("Content-Type"))
	}

	// DB non-200 should be propagated.
	dbSrv2 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte("race not found"))
	}))
	defer dbSrv2.Close()

	restore2 := withIPs(addrFromURL(authSrv.URL), addrFromURL(dbSrv2.URL), addrFromURL(recogSrv.URL))
	defer restore2()

	ct, body = makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", ct)
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusNotFound || !strings.Contains(w.Body.String(), "race not found") {
		t.Fatalf("expected propagated 404, got code=%d body=%s", w.Code, w.Body.String())
	}
}

func TestAuthorizationCheckNon200AndLoginBadJSON(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer authSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodPost, "/x", nil)
	req.Header.Set("Authorization", "Bearer token")
	if authorizationCheck(req) {
		t.Fatalf("expected false on non-200 auth verify")
	}

	req = httptest.NewRequest(http.MethodPost, "/loginUser", bytes.NewBufferString("bad"))
	w := httptest.NewRecorder()
	handleLoginUser(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 got %d", w.Code)
	}
}

func TestGetDogBreedInfoMultipartParseBranches(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, recognitionIP)
	defer restore()

	// Missing boundary branch.
	req := httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewBufferString("abc"))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", "multipart/form-data")
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for missing boundary got %d", w.Code)
	}

	// Parse error branch.
	req = httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewBufferString("abc"))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", "multipart/form-data; boundary=\"unterminated")
	w = httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for invalid media type got %d", w.Code)
	}
}

func TestRegisterUserMethodNotAllowedPath(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/registerUser", nil)
	w := httptest.NewRecorder()
	handleRegisterUser(w, req)
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404 got %d", w.Code)
	}
}

func TestGetDogBreedInfoRecognitionUnavailable(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), databaseIP, "127.0.0.1:1")
	defer restore()

	ct, body := makePhotoMultipart(t, "photo", []byte{1, 2, 3})
	req := httptest.NewRequest(http.MethodPost, "/getDogBreedInfo", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer token")
	req.Header.Set("Content-Type", ct)
	w := httptest.NewRecorder()
	handleGetDogBreedInfo(w, req)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 got %d", w.Code)
	}
}

func TestSubmitFeedbackDatabaseUnavailable(t *testing.T) {
	authSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer authSrv.Close()

	restore := withIPs(addrFromURL(authSrv.URL), "127.0.0.1:1", recognitionIP)
	defer restore()

	req := httptest.NewRequest(http.MethodPost, "/submitDogBreedFeedback", bytes.NewBufferString(`{"raceNameData":{"raceName":"beagle"}}`))
	req.Header.Set("Authorization", "Bearer token")
	w := httptest.NewRecorder()
	handleSubmitDogBreedFeedback(w, req)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 got %d", w.Code)
	}
}
