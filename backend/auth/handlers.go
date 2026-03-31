package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"

	"golang.org/x/crypto/bcrypt"
)

var DATABASE_IP = "host.docker.internal:8020"

type LoginRequest struct {
	LoginData struct {
		Username string `json:"username"`
		Password string `json:"password"`
	} `json:"loginData"`
}

type RegistrationRequest struct {
	RegistrationData struct {
		Name     string `json:"name"`
		Username string `json:"username"`
		Email    string `json:"email"`
		Password string `json:"password"`
	} `json:"registrationData"`
}

type VerifyTokenRequest struct {
	Token string `json:"token"`
	TokenData struct {
		Token string `json:"token"`
	} `json:"tokenData"`
}

type SaltResponse struct {
	Salt string `json:"salt"`
}

type VerifyResponse struct {
	Success bool `json:"success"`
}

type AuthResponse struct {
	Message string `json:"message"`
	Token   string `json:"token"`
}

func handleLogin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	var loginReq LoginRequest
	if err := json.Unmarshal(body, &loginReq); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	username := loginReq.LoginData.Username
	password := loginReq.LoginData.Password

	log.Printf("Username: %s, Password: %s\n", username, password)

	// Keep the user existence check behavior via requestSalt.
	saltRequestData := map[string]interface{}{
		"usernameData": map[string]string{
			"username": username,
		},
	}
	saltRequestBody, _ := json.Marshal(saltRequestData)

	resp, err := http.Post(
		fmt.Sprintf("http://%s/requestSalt", DATABASE_IP),
		"application/json",
		bytes.NewBuffer(saltRequestBody),
	)
	if err != nil || resp.StatusCode != http.StatusOK {
		http.Error(w, "User not found", http.StatusUnauthorized)
		return
	}

	var saltResp SaltResponse
	json.NewDecoder(resp.Body).Decode(&saltResp)
	resp.Body.Close()

	log.Printf("Retrieved salt: %s\n", saltResp.Salt)

	verifyRequestData := map[string]interface{}{
		"loginData": map[string]string{
			"username": username,
			// Send plaintext password; database service verifies against stored bcrypt hash.
			"password": password,
		},
	}
	verifyRequestBody, _ := json.Marshal(verifyRequestData)

	resp, err = http.Post(
		fmt.Sprintf("http://%s/verifyUsernamePassword", DATABASE_IP),
		"application/json",
		bytes.NewBuffer(verifyRequestBody),
	)
	if err != nil || resp.StatusCode != http.StatusOK {
		http.Error(w, "Invalid username or password", http.StatusUnauthorized)
		return
	}
	resp.Body.Close()

	// Generate token
	generatedToken, err := generateToken(username)
	if err != nil {
		http.Error(w, "Error generating token", http.StatusInternalServerError)
		return
	}

	// Send response
	authResp := AuthResponse{
		Message: "Login successful",
		Token:   generatedToken,
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusAccepted)
	json.NewEncoder(w).Encode(authResp)
}

func handleRegister(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	var regReq RegistrationRequest
	if err := json.Unmarshal(body, &regReq); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	name := regReq.RegistrationData.Name
	username := regReq.RegistrationData.Username
	email := regReq.RegistrationData.Email
	password := regReq.RegistrationData.Password

	// Validate inputs
	if !verifyInputs(name, username, email, password) {
		http.Error(w, "Invalid registration data", http.StatusBadRequest)
		log.Println("Invalid registration data")
		return
	}

	// Generate salt and hash password
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		http.Error(w, "Error hashing password", http.StatusInternalServerError)
		return
	}

	salt, err := extractSaltFromBcryptHash(string(hashedPassword))
	if err != nil {
		http.Error(w, "Error extracting salt", http.StatusInternalServerError)
		return
	}

	log.Printf("Storing user: Name: %s, Email: %s, Hashed Password: %s\n", name, email, string(hashedPassword))

	// Send registration request to database server
	registrationRequestData := map[string]interface{}{
		"registrationData": map[string]string{
			"name":          name,
			"username":      username,
			"email":         email,
			"password_hash": string(hashedPassword),
			"salt":          salt,
		},
	}
	registrationRequestBody, _ := json.Marshal(registrationRequestData)

	resp, err := http.Post(
		fmt.Sprintf("http://%s/registerUser", DATABASE_IP),
		"application/json",
		bytes.NewBuffer(registrationRequestBody),
	)
	if err != nil || resp.StatusCode != http.StatusCreated {
		http.Error(w, "Failed to register user", http.StatusInternalServerError)
		return
	}
	resp.Body.Close()

	// Generate token
	generatedToken, err := generateToken(name)
	if err != nil {
		http.Error(w, "Error generating token", http.StatusInternalServerError)
		return
	}

	// Send response
	authResp := AuthResponse{
		Message: "Registration successful",
		Token:   generatedToken,
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(authResp)
}

func handleVerifyToken(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	var verifyReq VerifyTokenRequest
	if err := json.Unmarshal(body, &verifyReq); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	token := verifyReq.Token
	if token == "" {
		token = verifyReq.TokenData.Token
	}

	// Verify token
	valid, err := verifyToken(token)
	if err != nil || !valid {
		http.Error(w, "Invalid token", http.StatusUnauthorized)
		return
	}

	verifyResp := VerifyResponse{Success: true}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(verifyResp)
}

func verifyInputs(name, username, email, password string) bool {
	// Check if fields are empty
	if name == "" || email == "" || password == "" || username == "" {
		return false
	}

	// Check password length
	if len(password) < 8 {
		return false
	}

	// Check email format
	if !contains(email, "@") || !contains(email, ".") {
		return false
	}

	// Check for special characters in password
	specialChars := "!@#$%^&*()-+?_=,<>/"
	hasSpecial := false
	for _, char := range password {
		if contains(specialChars, string(char)) {
			hasSpecial = true
			break
		}
	}
	if !hasSpecial {
		return false
	}

	// Check if name is alphanumeric
	if !isAlphanumeric(name) {
		return false
	}

	// Check if username is alphanumeric
	if !isAlphanumeric(username) {
		return false
	}

	// Check if username is taken
	usernameCheckData := map[string]interface{}{
		"usernameData": map[string]string{
			"username": username,
		},
	}
	usernameCheckBody, _ := json.Marshal(usernameCheckData)

	resp, err := http.Post(
		fmt.Sprintf("http://%s/isUsernameOrEmailTaken", DATABASE_IP),
		"application/json",
		bytes.NewBuffer(usernameCheckBody),
	)
	if err != nil || resp.StatusCode != http.StatusOK {
		return false
	}
	resp.Body.Close()

	return true
}

func contains(str, substr string) bool {
	if substr == "" {
		return true // Empty substring is always contained
	}
	for i := 0; i < len(str); i++ {
		if i+len(substr) <= len(str) && str[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

func isAlphanumeric(s string) bool {
	for _, char := range s {
		if !((char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') || (char >= '0' && char <= '9')) {
			return false
		}
	}
	return len(s) > 0
}

func extractSaltFromBcryptHash(hash string) (string, error) {
	// Bcrypt hashes encode algorithm, cost and salt in the first 29 chars.
	if len(hash) < 29 {
		return "", fmt.Errorf("invalid bcrypt hash length")
	}
	return hash[:29], nil
}
