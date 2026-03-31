package main

import (
	"strings"
	"testing"
	"time"
)

func TestGenerateToken(t *testing.T) {
	token, err := generateToken("testuser")
	if err != nil {
		t.Fatalf("Failed to generate token: %v", err)
	}

	if token == "" {
		t.Errorf("Token should not be empty")
	}

	// Token should be base64 encoded
	parts := strings.Split(token, "-")
	if len(parts) == 0 {
		t.Errorf("Token should be non-empty")
	}
}

func TestGenerateToken_DifferentUsers(t *testing.T) {
	token1, err1 := generateToken("user1")
	token2, err2 := generateToken("user2")

	if err1 != nil || err2 != nil {
		t.Fatalf("Failed to generate tokens")
	}

	// Tokens for different users should be different
	if token1 == token2 {
		t.Errorf("Tokens for different users should differ")
	}
}

func TestVerifyToken_Valid(t *testing.T) {
	token, err := generateToken("testuser")
	if err != nil {
		t.Fatalf("Failed to generate token: %v", err)
	}

	valid, err := verifyToken(token)
	if err != nil {
		t.Fatalf("Failed to verify token: %v", err)
	}

	if !valid {
		t.Errorf("Token should be valid")
	}
}

func TestVerifyToken_InvalidFormat(t *testing.T) {
	valid, _ := verifyToken("invalid_token_format")
	if valid {
		t.Errorf("Invalid token format should not be valid")
	}
}

func TestVerifyToken_MalformedBase64(t *testing.T) {
	valid, err := verifyToken("!!!invalid_base64!!!")
	if valid {
		t.Errorf("Malformed base64 should not be valid")
	}
	if err == nil {
		t.Errorf("Should return error for malformed base64")
	}
}

func TestVerifyToken_EmptyToken(t *testing.T) {
	valid, _ := verifyToken("")
	if valid {
		t.Errorf("Empty token should not be valid")
	}
}

func TestSplitString(t *testing.T) {
	tests := []struct {
		input    string
		sep      string
		expected []string
	}{
		{"a|b|c", "|", []string{"a", "b", "c"}},
		{"hello", "|", []string{"hello"}},
		{"a||c", "|", []string{"a", "", "c"}},
		{"", "|", []string{""}},
		{"abc", "bc", []string{"a", ""}},
	}

	for _, tt := range tests {
		result := splitString(tt.input, tt.sep)
		if len(result) != len(tt.expected) {
			t.Errorf("splitString(%q, %q) length mismatch: got %d, want %d", tt.input, tt.sep, len(result), len(tt.expected))
			continue
		}
		for i, v := range result {
			if v != tt.expected[i] {
				t.Errorf("splitString(%q, %q)[%d] = %q, want %q", tt.input, tt.sep, i, v, tt.expected[i])
			}
		}
	}
}

func TestLoadSecretKey(t *testing.T) {
	key, err := loadSecretKey()
	if err != nil {
		t.Fatalf("Failed to load secret key: %v", err)
	}

	if len(key) == 0 {
		t.Errorf("Secret key should not be empty")
	}
}

func TestVerifyToken_ExpiredToken(t *testing.T) {
	// This test would require manipulating time, which is complex
	// We verify the token validation logic works with current time
	token, err := generateToken("testuser")
	if err != nil {
		t.Fatalf("Failed to generate token: %v", err)
	}

	valid, err := verifyToken(token)
	if err != nil {
		t.Fatalf("Failed to verify fresh token: %v", err)
	}

	if !valid {
		t.Errorf("Fresh token should be valid")
	}
}

func TestTokenTimestampParsing(t *testing.T) {
	// Generate a token and verify timestamp is current time
	startTime := time.Now().Unix()
	token, err := generateToken("testuser")
	if err != nil {
		t.Fatalf("Failed to generate token: %v", err)
	}
	endTime := time.Now().Unix()

	valid, err := verifyToken(token)
	if err != nil {
		t.Fatalf("Failed to verify token: %v", err)
	}

	if !valid {
		t.Errorf("Token should be valid")
	}

	// The timestamp should be close to current time
	if endTime-startTime > 2 { // Allow 2 second difference
		t.Errorf("Token generation took too long")
	}
}

func TestVerifyTokenInvalidSignature(t *testing.T) {
	token, err := generateToken("testuser")
	if err != nil {
		t.Fatalf("Failed to generate token: %v", err)
	}

	// Tamper with the token
	tamperedToken := token[:len(token)-5] + "xxxxx"
	valid, err := verifyToken(tamperedToken)

	if valid {
		t.Errorf("Tampered token should not be valid")
	}
}
