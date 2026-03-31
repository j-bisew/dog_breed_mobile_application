package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"log"
	"os"
	"strconv"
	"time"
)

const TOKEN_TTL = 7200 // 2 hours in seconds

func loadSecretKey() ([]byte, error) {
	key, err := os.ReadFile("keys.txt")
	if err != nil {
		return nil, fmt.Errorf("failed to load secret key: %v", err)
	}
	// Remove any trailing newline/whitespace
	if len(key) > 0 && key[len(key)-1] == '\n' {
		key = key[:len(key)-1]
	}
	return key, nil
}

func generateToken(username string) (string, error) {
	secretKey, err := loadSecretKey()
	if err != nil {
		log.Printf("Error loading secret key: %v\n", err)
		return "", err
	}

	// Create timestamp
	timestamp := strconv.FormatInt(time.Now().Unix(), 10)

	// Create token
	token := fmt.Sprintf("%s|%s", username, timestamp)

	// Generate HMAC signature
	h := hmac.New(sha256.New, secretKey)
	h.Write([]byte(token))
	signature := hex.EncodeToString(h.Sum(nil))

	// Combine token and signature
	tokenWithSignature := fmt.Sprintf("%s|%s", token, signature)

	// Base64 encode
	encodedToken := base64.URLEncoding.EncodeToString([]byte(tokenWithSignature))

	return encodedToken, nil
}

func verifyToken(encodedToken string) (bool, error) {
	secretKey, err := loadSecretKey()
	if err != nil {
		log.Printf("Error loading secret key: %v\n", err)
		return false, err
	}

	// Decode token
	tokenWithSignature, err := base64.URLEncoding.DecodeString(encodedToken)
	if err != nil {
		log.Printf("Error decoding token: %v\n", err)
		return false, err
	}

	// Parse token parts
	parts := splitString(string(tokenWithSignature), "|")
	if len(parts) != 3 {
		return false, fmt.Errorf("invalid token format")
	}

	username := parts[0]
	timestamp := parts[1]
	signature := parts[2]

	// Verify timestamp
	tokenTime, err := strconv.ParseInt(timestamp, 10, 64)
	if err != nil {
		return false, fmt.Errorf("invalid timestamp")
	}

	currentTime := time.Now().Unix()
	if currentTime-tokenTime > TOKEN_TTL {
		return false, fmt.Errorf("token expired")
	}

	// Verify signature
	token := fmt.Sprintf("%s|%s", username, timestamp)
	h := hmac.New(sha256.New, secretKey)
	h.Write([]byte(token))
	expectedSignature := hex.EncodeToString(h.Sum(nil))

	if signature != expectedSignature {
		return false, fmt.Errorf("invalid token signature")
	}

	return true, nil
}

func splitString(s, sep string) []string {
	var parts []string
	start := 0
	for i := 0; i <= len(s)-len(sep); i++ {
		if s[i:i+len(sep)] == sep {
			parts = append(parts, s[start:i])
			start = i + len(sep)
			i += len(sep) - 1
		}
	}
	parts = append(parts, s[start:])
	return parts
}
