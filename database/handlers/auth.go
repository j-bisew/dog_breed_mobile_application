package handlers

import (
	"encoding/json"
	"net/http"

	"github.com/dogbreed/database/db"
)

// POST /requestSalt
// Body: {"usernameData": {"username": "user"}}
func RequestSalt(w http.ResponseWriter, r *http.Request) {
	var body struct {
		UsernameData struct {
			Username string `json:"username"`
		} `json:"usernameData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	var salt string
	err := db.DB.QueryRow(
		"SELECT salt FROM users WHERE username = $1",
		body.UsernameData.Username,
	).Scan(&salt)
	if err != nil {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"salt": salt})
}

// POST /verifyUsernamePassword
// Body: {"loginData": {"username": "user", "password": "passwordHash"}}
func VerifyUsernamePassword(w http.ResponseWriter, r *http.Request) {
	var body struct {
		LoginData struct {
			Username string `json:"username"`
			Password string `json:"password"`
		} `json:"loginData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	var storedHash string
	err := db.DB.QueryRow(
		"SELECT password_hash FROM users WHERE username = $1",
		body.LoginData.Username,
	).Scan(&storedHash)
	if err != nil {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	if storedHash != body.LoginData.Password {
		http.Error(w, "Invalid password", http.StatusUnauthorized)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Verification successful"))
}

// POST /isUsernameOrEmailTaken
// Body: {"usernameData": {"username": "user"}}
func IsUsernameOrEmailTaken(w http.ResponseWriter, r *http.Request) {
	var body struct {
		UsernameData struct {
			Username string `json:"username"`
		} `json:"usernameData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	var count int
	db.DB.QueryRow(
		"SELECT COUNT(*) FROM users WHERE username = $1",
		body.UsernameData.Username,
	).Scan(&count)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]bool{"isTaken": count > 0})
}

// POST /registerUser
// Body: {"registrationData": {"name": "Name", "username": "user", "email": "email", "password_hash": "hash", "salt": "salt"}}
func RegisterUser(w http.ResponseWriter, r *http.Request) {
	var body struct {
		RegistrationData struct {
			Name         string `json:"name"`
			Username     string `json:"username"`
			Email        string `json:"email"`
			PasswordHash string `json:"password_hash"`
			Salt         string `json:"salt"`
		} `json:"registrationData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	d := body.RegistrationData
	_, err := db.DB.Exec(
		`INSERT INTO users (name, username, email, password_hash, salt) VALUES ($1, $2, $3, $4, $5)`,
		d.Name, d.Username, d.Email, d.PasswordHash, d.Salt,
	)
	if err != nil {
		http.Error(w, "Username or email already taken", http.StatusBadRequest)
		return
	}

	w.WriteHeader(http.StatusCreated)
	w.Write([]byte("User registered successfully"))
}
