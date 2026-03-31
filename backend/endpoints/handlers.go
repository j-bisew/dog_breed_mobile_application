package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"mime"
	"mime/multipart"
	"net/http"
	"os"
	"strings"
)

var authIP = "host.docker.internal:8010"
var databaseIP = "host.docker.internal:8020"
var recognitionIP = "host.docker.internal:8030"

var authorizationRequiredPaths = map[string]bool{
	"/submitDogBreedFeedback": true,
	"/getDogBreedInfo":        true,
}

func handleRegisterUser(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}
	log.Printf("Received registration data: %s\n", string(body))

	var incoming map[string]map[string]interface{}
	if err := json.Unmarshal(body, &incoming); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	userData := incoming["registrationData"]
	registrationRequestData := map[string]interface{}{
		"registrationData": map[string]interface{}{
			"name":     userData["name"],
			"username": userData["username"],
			"email":    userData["email"],
			"password": userData["password"],
		},
	}

	payload, _ := json.Marshal(registrationRequestData)
	resp, respBody, err := postJSON(fmt.Sprintf("http://%s/register", authIP), payload)
	if err != nil {
		http.Error(w, "Auth unavailable", http.StatusBadGateway)
		return
	}

	if resp.StatusCode == http.StatusOK {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("Registration successful"))
		return
	}

	w.WriteHeader(resp.StatusCode)
	_, _ = w.Write(respBody)
}

func handleLoginUser(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}
	log.Printf("Received login data: %s\n", string(body))

	var incoming map[string]map[string]interface{}
	if err := json.Unmarshal(body, &incoming); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	loginData := incoming["loginData"]
	loginRequestData := map[string]interface{}{
		"loginData": map[string]interface{}{
			"username": loginData["username"],
			"password": loginData["password"],
		},
	}

	payload, _ := json.Marshal(loginRequestData)
	resp, respBody, err := postJSON(fmt.Sprintf("http://%s/login", authIP), payload)
	if err != nil {
		http.Error(w, "Auth unavailable", http.StatusBadGateway)
		return
	}

	if resp.StatusCode == http.StatusOK {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("Login successful"))
		return
	}

	w.WriteHeader(resp.StatusCode)
	_, _ = w.Write(respBody)
}

func handleGetDogBreedInfo(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}

	if authorizationRequiredPaths[r.URL.Path] {
		if !authorizationCheck(r) {
			w.WriteHeader(http.StatusUnauthorized)
			_, _ = w.Write([]byte("Unauthorized"))
			return
		}
	}

	log.Printf("Handling getDogBreedInfoPathHandler request")

	contentType := r.Header.Get("Content-Type")
	if contentType == "" {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Content-Type header missing"))
		return
	}

	if !strings.HasPrefix(contentType, "multipart/form-data") {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Unsupported Content-Type"))
		return
	}

	if r.ContentLength > 20*1024*1024 {
		w.WriteHeader(http.StatusRequestEntityTooLarge)
		_, _ = w.Write([]byte("Request entity too large"))
		return
	}

	_, params, err := mime.ParseMediaType(contentType)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Unsupported Content-Type"))
		return
	}

	boundary := params["boundary"]
	if boundary == "" {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Unsupported Content-Type"))
		return
	}

	reader := multipart.NewReader(r.Body, boundary)
	var photoData []byte
	for {
		part, err := reader.NextPart()
		if err == io.EOF {
			break
		}
		if err != nil {
			w.WriteHeader(http.StatusBadRequest)
			_, _ = w.Write([]byte("Photo data not found in the request"))
			return
		}

		if part.FormName() == "photo" {
			photoData, err = io.ReadAll(part)
			if err != nil {
				w.WriteHeader(http.StatusBadRequest)
				_, _ = w.Write([]byte("Photo data not found in the request"))
				return
			}
			break
		}
	}

	if len(photoData) == 0 {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Photo data not found in the request"))
		return
	}

	log.Printf("Received photo data of length: %d\n", len(photoData))
	_ = os.WriteFile("aaaa.jpg", photoData, 0o644)

	var reqBuffer bytes.Buffer
	mw := multipart.NewWriter(&reqBuffer)
	fw, err := mw.CreateFormFile("photo", "photo.jpg")
	if err != nil {
		http.Error(w, "Recognition request build failed", http.StatusInternalServerError)
		return
	}
	_, _ = fw.Write(photoData)
	_ = mw.Close()

	recReq, _ := http.NewRequest(http.MethodPost, fmt.Sprintf("http://%s/submitDogPhoto", recognitionIP), &reqBuffer)
	recReq.Header.Set("Content-Type", mw.FormDataContentType())
	recResp, err := http.DefaultClient.Do(recReq)
	if err != nil {
		http.Error(w, "Recognition unavailable", http.StatusBadGateway)
		return
	}
	defer recResp.Body.Close()
	recBody, _ := io.ReadAll(recResp.Body)

	if recResp.StatusCode != http.StatusOK {
		w.WriteHeader(recResp.StatusCode)
		_, _ = w.Write(recBody)
		return
	}

	var recJSON map[string]interface{}
	if err := json.Unmarshal(recBody, &recJSON); err != nil {
		http.Error(w, "Invalid recognition response", http.StatusBadGateway)
		return
	}

	breedName, _ := recJSON["breedName"].(string)
	if breedName == "" {
		breedName = "UnknownBreed"
	}
	confidence := recJSON["confidence"]
	if confidence == nil {
		confidence = 0.0
	}

	breedInfoReq := map[string]interface{}{
		"raceRequestData": map[string]interface{}{
			"name":       breedName,
			"confidence": confidence,
		},
	}
	breedPayload, _ := json.Marshal(breedInfoReq)

	dbReq, _ := http.NewRequest(http.MethodPost, fmt.Sprintf("http://%s/getDogRaceInfo", databaseIP), bytes.NewReader(breedPayload))
	dbReq.Header.Set("Content-Type", "application/json")
	dbResp, err := http.DefaultClient.Do(dbReq)
	if err != nil {
		http.Error(w, "Database unavailable", http.StatusBadGateway)
		return
	}
	defer dbResp.Body.Close()
	dbBody, _ := io.ReadAll(dbResp.Body)

	log.Printf("Breed info response status: %d\n", dbResp.StatusCode)

	if dbResp.StatusCode == http.StatusOK {
		if ct := dbResp.Header.Get("Content-Type"); ct != "" {
			w.Header().Set("Content-Type", ct)
		} else {
			w.Header().Set("Content-Type", "application/json")
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(dbBody)
		return
	}

	w.WriteHeader(dbResp.StatusCode)
	_, _ = w.Write(dbBody)
}

func handleSubmitDogBreedFeedback(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}

	if authorizationRequiredPaths[r.URL.Path] {
		if !authorizationCheck(r) {
			w.WriteHeader(http.StatusUnauthorized)
			_, _ = w.Write([]byte("Unauthorized"))
			return
		}
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}
	log.Printf("Received breed feedback data: %s\n", string(body))

	var incoming map[string]map[string]interface{}
	if err := json.Unmarshal(body, &incoming); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	raceNameData := incoming["raceNameData"]
	feedbackRequestData := map[string]interface{}{
		"raceNameData": map[string]interface{}{
			"raceName": raceNameData["raceName"],
		},
	}

	payload, _ := json.Marshal(feedbackRequestData)
	resp, respBody, err := postJSON(fmt.Sprintf("http://%s/incrementDogRaceQuestionedCount", databaseIP), payload)
	if err != nil {
		http.Error(w, "Database unavailable", http.StatusBadGateway)
		return
	}

	if resp.StatusCode == http.StatusOK {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("Feedback submitted successfully"))
		return
	}

	w.WriteHeader(resp.StatusCode)
	_, _ = w.Write(respBody)
}

func authorizationCheck(r *http.Request) bool {
	authHeader := r.Header.Get("Authorization")
	if authHeader == "" {
		log.Println("Authorization header missing")
		return false
	}

	token := authHeader
	if strings.Contains(authHeader, " ") {
		parts := strings.SplitN(authHeader, " ", 2)
		token = parts[1]
	}

	tokenData := map[string]interface{}{
		"token": token,
	}
	payload, _ := json.Marshal(tokenData)

	resp, _, err := postJSON(fmt.Sprintf("http://%s/verifyToken", authIP), payload)
	if err != nil {
		log.Println("Authorization failed")
		return false
	}

	if resp.StatusCode == http.StatusOK {
		log.Println("Authorization successful")
		return true
	}

	log.Println("Authorization failed")
	return false
}

func postJSON(url string, payload []byte) (*http.Response, []byte, error) {
	req, _ := http.NewRequest(http.MethodPost, url, bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, nil, err
	}
	body, err := io.ReadAll(resp.Body)
	_ = resp.Body.Close()
	if err != nil {
		return nil, nil, err
	}
	resp.Body = io.NopCloser(bytes.NewReader(body))
	return resp, body, nil
}
