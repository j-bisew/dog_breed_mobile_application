package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestHandleLogin_Success(t *testing.T) {
	// Mock database server
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/requestSalt" {
			w.WriteHeader(http.StatusOK)
			json.NewEncoder(w).Encode(map[string]string{"salt": "$2y$10$mockSalt"})
		} else if r.URL.Path == "/verifyUsernamePassword" {
			w.WriteHeader(http.StatusOK)
			json.NewEncoder(w).Encode(map[string]bool{"success": true})
		}
	}))
	defer mockDB.Close()

	// Override DATABASE_IP to mock
	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	loginReq := LoginRequest{
		LoginData: struct {
			Username string `json:"username"`
			Password string `json:"password"`
		}{
			Username: "testuser",
			Password: "Test@1234",
		},
	}
	body, _ := json.Marshal(loginReq)

	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleLogin(w, req)

	if w.Code != http.StatusAccepted {
		t.Errorf("Expected status 202, got %d", w.Code)
	}

	var resp AuthResponse
	json.NewDecoder(w.Body).Decode(&resp)
	if resp.Message != "Login successful" || resp.Token == "" {
		t.Errorf("Unexpected response: %+v", resp)
	}
}

func TestHandleLogin_InvalidJSON(t *testing.T) {
	req := httptest.NewRequest("POST", "/login", bytes.NewReader([]byte("invalid json")))
	w := httptest.NewRecorder()

	handleLogin(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleLogin_UserNotFound(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	loginReq := LoginRequest{
		LoginData: struct {
			Username string `json:"username"`
			Password string `json:"password"`
		}{
			Username: "nonexistent",
			Password: "Test@1234",
		},
	}
	body, _ := json.Marshal(loginReq)

	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleLogin(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("Expected status 401, got %d", w.Code)
	}
}

func TestHandleLogin_InvalidMethod(t *testing.T) {
	req := httptest.NewRequest("GET", "/login", nil)
	w := httptest.NewRecorder()

	handleLogin(w, req)

	if w.Code != http.StatusMethodNotAllowed {
		t.Errorf("Expected status 405, got %d", w.Code)
	}
}

func TestHandleRegister_Success(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/isUsernameOrEmailTaken" {
			w.WriteHeader(http.StatusOK)
		} else if r.URL.Path == "/registerUser" {
			w.WriteHeader(http.StatusCreated)
		}
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John",
			Username: "john123",
			Email:    "john@example.com",
			Password: "SecurePass@123",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusCreated {
		t.Errorf("Expected status 201, got %d", w.Code)
	}

	var resp AuthResponse
	json.NewDecoder(w.Body).Decode(&resp)
	if resp.Message != "Registration successful" || resp.Token == "" {
		t.Errorf("Unexpected response: %+v", resp)
	}
}

func TestHandleRegister_InvalidJSON(t *testing.T) {
	req := httptest.NewRequest("POST", "/register", bytes.NewReader([]byte("invalid")))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleRegister_PasswordTooShort(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John",
			Username: "john123",
			Email:    "john@example.com",
			Password: "Pass",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleRegister_NoSpecialChar(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John",
			Username: "john123",
			Email:    "john@example.com",
			Password: "Password1234",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d, should fail - no special char", w.Code)
	}
}

func TestHandleRegister_InvalidEmail(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John",
			Username: "john123",
			Email:    "invalidemail",
			Password: "SecurePass@123",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleRegister_NonAlphanumericName(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John@",
			Username: "john123",
			Email:    "john@example.com",
			Password: "SecurePass@123",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleRegister_UsernameTaken(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/isUsernameOrEmailTaken" {
			w.WriteHeader(http.StatusConflict)
		}
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John",
			Username: "taken",
			Email:    "john@example.com",
			Password: "SecurePass@123",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleRegister_DBError(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/isUsernameOrEmailTaken" {
			w.WriteHeader(http.StatusOK)
		} else if r.URL.Path == "/registerUser" {
			w.WriteHeader(http.StatusInternalServerError)
		}
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	regReq := RegistrationRequest{
		RegistrationData: struct {
			Name     string `json:"name"`
			Username string `json:"username"`
			Email    string `json:"email"`
			Password string `json:"password"`
		}{
			Name:     "John",
			Username: "john123",
			Email:    "john@example.com",
			Password: "SecurePass@123",
		},
	}
	body, _ := json.Marshal(regReq)

	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleRegister(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("Expected status 500, got %d", w.Code)
	}
}

func TestHandleVerifyToken_Valid(t *testing.T) {
	token, err := generateToken("testuser")
	if err != nil {
		t.Fatalf("Failed to generate token: %v", err)
	}

	verifyReq := VerifyTokenRequest{Token: token}
	body, _ := json.Marshal(verifyReq)

	req := httptest.NewRequest("POST", "/verifyToken", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleVerifyToken(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("Expected status 200, got %d", w.Code)
	}

	var resp VerifyResponse
	json.NewDecoder(w.Body).Decode(&resp)
	if !resp.Success {
		t.Errorf("Token verification should succeed")
	}
}

func TestHandleVerifyToken_Invalid(t *testing.T) {
	verifyReq := VerifyTokenRequest{Token: "invalid_token"}
	body, _ := json.Marshal(verifyReq)

	req := httptest.NewRequest("POST", "/verifyToken", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handleVerifyToken(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("Expected status 401, got %d", w.Code)
	}
}

func TestHandleVerifyToken_InvalidJSON(t *testing.T) {
	req := httptest.NewRequest("POST", "/verifyToken", bytes.NewReader([]byte("invalid")))
	w := httptest.NewRecorder()

	handleVerifyToken(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status 400, got %d", w.Code)
	}
}

func TestHandleVerifyToken_InvalidMethod(t *testing.T) {
	req := httptest.NewRequest("GET", "/verifyToken", nil)
	w := httptest.NewRecorder()

	handleVerifyToken(w, req)

	if w.Code != http.StatusMethodNotAllowed {
		t.Errorf("Expected status 405, got %d", w.Code)
	}
}

func TestVerifyInputs_EmptyFields(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	if verifyInputs("", "username", "email@test.com", "SecurePass@123") {
		t.Errorf("Should reject empty name")
	}
	if verifyInputs("name", "", "email@test.com", "SecurePass@123") {
		t.Errorf("Should reject empty username")
	}
	if verifyInputs("name", "username", "", "SecurePass@123") {
		t.Errorf("Should reject empty email")
	}
	if verifyInputs("name", "username", "email@test.com", "") {
		t.Errorf("Should reject empty password")
	}
}

func TestContains(t *testing.T) {
	tests := []struct {
		str      string
		substr   string
		expected bool
	}{
		{"hello world", "world", true},
		{"hello world", "xyz", false},
		{"test@example.com", "@", true},
		{"", "", true},
		{"test", "", true},
	}

	for _, tt := range tests {
		if result := contains(tt.str, tt.substr); result != tt.expected {
			t.Errorf("contains(%q, %q) = %v, want %v", tt.str, tt.substr, result, tt.expected)
		}
	}
}

func TestIsAlphanumeric(t *testing.T) {
	tests := []struct {
		input    string
		expected bool
	}{
		{"abc123", true},
		{"ABC", true},
		{"abc@def", false},
		{"abc 123", false},
		{"", false},
		{"123", true},
		{"_abc", false},
	}

	for _, tt := range tests {
		if result := isAlphanumeric(tt.input); result != tt.expected {
			t.Errorf("isAlphanumeric(%q) = %v, want %v", tt.input, result, tt.expected)
		}
	}
}

func TestHandleLogin_DBUnavailable(t *testing.T) {
	originalIP := DATABASE_IP
	DATABASE_IP = "127.0.0.1:1"
	defer func() { DATABASE_IP = originalIP }()

	body := []byte(`{"loginData":{"username":"u","password":"P@ssword1"}}`)
	req := httptest.NewRequest("POST", "/login", bytes.NewReader(body))
	w := httptest.NewRecorder()
	handleLogin(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 got %d", w.Code)
	}
}

func TestHandleRegister_TokenErrorWhenKeyMissing(t *testing.T) {
	mockDB := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/isUsernameOrEmailTaken" {
			w.WriteHeader(http.StatusOK)
			return
		}
		if r.URL.Path == "/registerUser" {
			w.WriteHeader(http.StatusCreated)
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer mockDB.Close()

	originalIP := DATABASE_IP
	DATABASE_IP = mockDB.Listener.Addr().String()
	defer func() { DATABASE_IP = originalIP }()

	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd failed: %v", err)
	}
	tmp := t.TempDir()
	if err := os.Chdir(tmp); err != nil {
		t.Fatalf("chdir failed: %v", err)
	}
	defer func() { _ = os.Chdir(cwd) }()

	body := []byte(`{"registrationData":{"name":"John","username":"john123","email":"john@example.com","password":"SecurePass@123"}}`)
	req := httptest.NewRequest("POST", "/register", bytes.NewReader(body))
	w := httptest.NewRecorder()
	handleRegister(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 when key missing, got %d", w.Code)
	}
}

func TestHandleRegister_InvalidMethod(t *testing.T) {
	req := httptest.NewRequest("GET", "/register", nil)
	w := httptest.NewRecorder()
	handleRegister(w, req)
	if w.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected 405 got %d", w.Code)
	}
}

func TestVerifyInputs_DBUnavailable(t *testing.T) {
	originalIP := DATABASE_IP
	DATABASE_IP = "127.0.0.1:1"
	defer func() { DATABASE_IP = originalIP }()

	if verifyInputs("John", "john123", "john@example.com", "SecurePass@123") {
		t.Fatalf("expected verifyInputs false when DB is unavailable")
	}
}
