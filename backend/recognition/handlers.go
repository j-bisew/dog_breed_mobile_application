package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	//"fmt"
	"io"
	"log"
	"mime/multipart"
	"net/http"
	"os"
	//"path/filepath"
	"strings"
)

const folderPath = "dog_photos"

var randRead = rand.Read

type recognitionResponse struct {
	BreedName  string  `json:"breedName"`
	Confidence float32 `json:"confidence"`
}

func handleSubmitDogPhoto(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}

	contentType := r.Header.Get("Content-Type")
	if contentType == "" || !strings.HasPrefix(contentType, "multipart/form-data") {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Unsupported Content-Type"))
		return
	}

	if err := r.ParseMultipartForm(20 * 1024 * 1024); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Boundary not found"))
		return
	}

	file, _, err := r.FormFile("photo")
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Photo data not found"))
		return
	}
	defer file.Close()

	photoData, err := io.ReadAll(file)
	if err != nil || len(photoData) == 0 {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte("Photo data not found"))
		return
	}

	if err := os.MkdirAll(folderPath, 0o755); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte("Failed to prepare photo storage"))
		return
	}

	// randomName := randomString(16)
	// photoPath := filepath.Join(folderPath, fmt.Sprintf("%s.jpg", randomName))
	// if err := os.WriteFile(photoPath, photoData, 0o644); err != nil {
	// 	w.WriteHeader(http.StatusInternalServerError)
	// 	_, _ = w.Write([]byte("Failed to save photo"))
	// 	return
	// }

	box, conf, cropBytes, err := detectAndCropDog(photoData)
	if err != nil {
		log.Printf("detection error: %v", err)
		w.WriteHeader(515)
		_, _ = w.Write([]byte("No dog detected in the photo"))
		return
	}
	if box == nil || conf < 0.5 {
		w.WriteHeader(515)
		_, _ = w.Write([]byte("No dog detected in the photo"))
		return
	}

	// croppedPath := filepath.Join(folderPath, fmt.Sprintf("%s_cropped.jpg", randomName))
	// _ = os.WriteFile(croppedPath, cropBytes, 0o644)

	breed, confidence, err := predictBreedFromBytes(cropBytes)
	if err != nil {
		log.Printf("breed prediction error: %v", err)
		w.WriteHeader(515)
		_, _ = w.Write([]byte("Error predicting breed"))
		return
	}

	if confidence < 50.0 {
		w.WriteHeader(515)
		_, _ = w.Write([]byte("Breed confidence too low"))
		return
	}

	breed = strings.ToLower(strings.ReplaceAll(breed, " ", "_"))

	resp := recognitionResponse{
		BreedName:  breed,
		Confidence: confidence,
	}
	respJSON, _ := json.Marshal(resp)
	log.Printf("Predicted breed: %s with confidence %f%%", breed, confidence)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(respJSON)
}

func randomString(n int) string {
	b := make([]byte, n)
	_, err := randRead(b)
	if err != nil {
		return "randomfallback"
	}
	return hex.EncodeToString(b)[:n]
}

func readMultipartPhoto(file multipart.File) ([]byte, error) {
	return io.ReadAll(file)
}
