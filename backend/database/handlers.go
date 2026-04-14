package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

type UsernameData struct {
	UsernameData struct {
		Username string `json:"username"`
	} `json:"usernameData"`
}

type LoginData struct {
	LoginData struct {
		Username string `json:"username"`
		Password string `json:"password"`
	} `json:"loginData"`
}

type RegistrationData struct {
	RegistrationData struct {
		Name         string `json:"name"`
		Username     string `json:"username"`
		Email        string `json:"email"`
		PasswordHash string `json:"password_hash"`
		Salt         string `json:"salt"`
	} `json:"registrationData"`
}

type RaceRequestData struct {
	RaceRequestData struct {
		RaceID     *int     `json:"raceId"`
		Name       string   `json:"name"`
		Confidence *float64 `json:"confidence"`
	} `json:"raceRequestData"`
}

type RaceData struct {
	RaceData struct {
		Name       string `json:"name"`
		FolderName string `json:"folderName"`
	} `json:"raceData"`
}

type RaceNameData struct {
	RaceNameData struct {
		RaceName string `json:"raceName"`
	} `json:"raceNameData"`
}

type SaltResponse struct {
	Salt string `json:"salt"`
}

type VerificationResponse struct {
	Success bool `json:"success"`
}

type IsTakenResponse struct {
	IsTaken bool `json:"isTaken"`
}

type RaceInfoResponse struct {
	BreedName        string   `json:"breedName"`
	BreedFullName    string   `json:"breedFullName"`
	BreedDescription string   `json:"breedDescription"`
	Confidence       float64  `json:"confidence,omitempty"`
}

func handleRequestSalt(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received username data: %s\n", string(body))

	var req UsernameData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	username := req.UsernameData.Username

	salt, err := requestSaltFromDB(username)
	if err != nil {
		http.Error(w, "User not found", http.StatusNotFound)
		return
	}

	response := SaltResponse{Salt: salt}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(response)
}

func handleVerifyUsernamePassword(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received login data: %s\n", string(body))

	var req LoginData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	username := req.LoginData.Username
	password := req.LoginData.Password

	valid, err := verifyPasswordFromDB(username, password)
	if err != nil || !valid {
		http.Error(w, "Invalid password", http.StatusUnauthorized)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Verification successful"))
}

func handleIsUsernameOrEmailTaken(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received username data: %s\n", string(body))

	var req UsernameData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	username := req.UsernameData.Username

	isTaken, err := isUsernameOrEmailTakenFromDB(username)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	response := IsTakenResponse{IsTaken: isTaken}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(response)
}

func handleRegisterUser(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received registration data: %s\n", string(body))

	var req RegistrationData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	name := req.RegistrationData.Name
	username := req.RegistrationData.Username
	email := req.RegistrationData.Email
	passwordHash := req.RegistrationData.PasswordHash
	salt := req.RegistrationData.Salt

	err = registerUserInDB(name, username, email, passwordHash, salt)
	if err != nil {
		http.Error(w, "Username or email already taken", http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	w.Write([]byte("User registered successfully"))
}

func handleGetDogRaceInfo(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received race request data: %s\n", string(body))

	var req RaceRequestData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	raceID := req.RaceRequestData.RaceID
	raceName := req.RaceRequestData.Name
	confidence := req.RaceRequestData.Confidence

	raceInfo, err := getDogRaceInfoFromDB(raceID, raceName)
	if err != nil {
		http.Error(w, "Race not found", http.StatusNotFound)
		return
	}

	// Increment timesSearched
	if raceInfo.ID > 0 {
		err = incrementTimesSearchedInDB(raceInfo.ID)
		if err != nil {
			log.Printf("Error incrementing timesSearched: %v\n", err)
		}
	}

	// Read photo and breed info
	mainPhotoPath := filepath.Join("racesFolder", raceInfo.FolderName, "mainPhoto.jpg")
	summaryPath := filepath.Join("racesFolder", raceInfo.FolderName, "summary")

	photoBytes := []byte{}
	if data, err := os.ReadFile(mainPhotoPath); err == nil {
		photoBytes = data
	}

	breedFullName := ""
	breedDescription := ""
	if data, err := os.ReadFile(summaryPath); err == nil {
		lines := strings.Split(string(data), "\n")
		if len(lines) > 0 {
			parts := strings.Split(lines[0], ":")
			if len(parts) > 1 {
				breedFullName = strings.TrimSpace(parts[1])
			}
		}
		if len(lines) > 1 {
			parts := strings.Split(lines[1], ":")
			if len(parts) > 1 {
				breedDescription = strings.TrimSpace(parts[1])
			}
		}
	}

	responseMetadata := RaceInfoResponse{
		BreedName:        raceInfo.Name,
		BreedFullName:    breedFullName,
		BreedDescription: breedDescription,
	}

	if confidence != nil {
		responseMetadata.Confidence = *confidence
	}

	// If photo exists, send as multipart
	if len(photoBytes) > 0 {
		boundary := fmt.Sprintf("----Boundary%d", os.Getpid())
		crlf := "\r\n"

		w.Header().Set("Content-Type", fmt.Sprintf("multipart/mixed; boundary=%s", boundary))
		w.Header().Set("Connection", "close")
		w.WriteHeader(http.StatusOK)

		// Part 1: JSON metadata
		w.Write([]byte("--" + boundary + crlf))
		w.Write([]byte("Content-Type: application/json; charset=utf-8" + crlf + crlf))
		json.NewEncoder(w).Encode(responseMetadata)
		w.Write([]byte(crlf))

		// Part 2: Image
		w.Write([]byte("--" + boundary + crlf))
		w.Write([]byte("Content-Type: image/jpeg" + crlf))
		w.Write([]byte("Content-Disposition: attachment; filename=\"mainPhoto.jpg\"" + crlf))
		w.Write([]byte("Content-Transfer-Encoding: binary" + crlf + crlf))
		w.Write(photoBytes)
		w.Write([]byte(crlf))

		// Closing boundary
		w.Write([]byte("--" + boundary + "--" + crlf))
	} else {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Connection", "close")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(responseMetadata)
	}
}

func handleAddDogRace(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received race data: %s\n", string(body))

	var req RaceData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	name := req.RaceData.Name
	folderName := req.RaceData.FolderName

	err = addRaceToDB(name, folderName)
	if err != nil {
		http.Error(w, "Error adding race", http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	w.Write([]byte("Race added successfully"))
}

func handleIncrementDogRaceQuestionedCount(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}

	log.Printf("Received race ID data for search count increment: %s\n", string(body))

	var req RaceNameData
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	raceName := req.RaceNameData.RaceName

	err = incrementQuestionedCountInDB(raceName)
	if err != nil {
		http.Error(w, "Error updating count", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Questioned count incremented successfully"))
}
