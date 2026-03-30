package handlers

import (
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"os"
	"path/filepath"
	"strings"

	"github.com/dogbreed/database/db"
)

func racesFolder() string {
	if v := os.Getenv("RACES_FOLDER"); v != "" {
		return v
	}
	return "racesFolder"
}

// POST /getDogRaceInfo
// Body: {"raceRequestData": {"raceId": 1}} or {"raceRequestData": {"name": "Poodle", "confidence": 0.9}}
func GetDogRaceInfo(w http.ResponseWriter, r *http.Request) {
	var body struct {
		RaceRequestData struct {
			RaceID     *int     `json:"raceId"`
			Name       *string  `json:"name"`
			Confidence *float64 `json:"confidence"`
			Username   *string  `json:"username"`
		} `json:"raceRequestData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	d := body.RaceRequestData
	var id int
	var name, folderName string
	var err error

	if d.RaceID != nil {
		err = db.DB.QueryRow(
			"SELECT id, name, folder_name FROM dog_races WHERE id = $1", *d.RaceID,
		).Scan(&id, &name, &folderName)
	} else if d.Name != nil {
		err = db.DB.QueryRow(
			"SELECT id, name, folder_name FROM dog_races WHERE name = $1", *d.Name,
		).Scan(&id, &name, &folderName)
	} else {
		http.Error(w, "Invalid request data", http.StatusBadRequest)
		return
	}

	if err != nil {
		http.Error(w, "Race not found", http.StatusNotFound)
		return
	}

	// Increment times_searched
	db.DB.Exec("UPDATE dog_races SET times_searched = times_searched + 1 WHERE id = $1", id)
	if d.Username != nil {
		if err := incrementSearchedBreed(*d.Username, name); err != nil {
			// Do not fail the main response if stats update fails
			fmt.Printf("incrementSearchedBreed error: %v\n", err)
		}
	}

	// Build metadata
	metadata := map[string]interface{}{}
	summaryPath := filepath.Join(racesFolder(), folderName, "summary")
	if data, err := os.ReadFile(summaryPath); err == nil {
		lines := strings.Split(string(data), "\n")
		if len(lines) >= 2 {
			if parts := strings.SplitN(lines[0], ":", 2); len(parts) == 2 {
				metadata["breedFullName"] = strings.TrimSpace(parts[1])
			}
			if parts := strings.SplitN(lines[1], ":", 2); len(parts) == 2 {
				metadata["breedDescription"] = strings.TrimSpace(parts[1])
			}
		}
		metadata["breedName"] = name
		if d.Confidence != nil {
			metadata["confidence"] = *d.Confidence
		}
	}

	// Try to attach main photo as multipart
	photoPath := filepath.Join(racesFolder(), folderName, "mainPhoto.jpg")
	photoData, photoErr := os.ReadFile(photoPath)
	if photoErr == nil {
		boundary := fmt.Sprintf("----Boundary%08x", uint32(os.Getpid()))
		mw := multipart.NewWriter(w)
		mw.SetBoundary(boundary)

		w.Header().Set("Content-Type", "multipart/mixed; boundary="+boundary)
		w.Header().Set("Connection", "close")
		w.WriteHeader(http.StatusOK)

		// Part 1: JSON metadata
		jsonHdr := textproto.MIMEHeader{}
		jsonHdr.Set("Content-Type", "application/json; charset=utf-8")
		pw, _ := mw.CreatePart(jsonHdr)
		json.NewEncoder(pw).Encode(metadata)

		// Part 2: image
		imgHdr := textproto.MIMEHeader{}
		imgHdr.Set("Content-Type", "image/jpeg")
		imgHdr.Set("Content-Disposition", `attachment; filename="mainPhoto.jpg"`)
		imgHdr.Set("Content-Transfer-Encoding", "binary")
		pw, _ = mw.CreatePart(imgHdr)
		pw.Write(photoData)

		mw.Close()
	} else {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Connection", "close")
		json.NewEncoder(w).Encode(metadata)
	}
}

// POST /addDogRace
// Body: {"raceData": {"name": "Poodle", "folderName": "poodle_folder"}}
func AddDogRace(w http.ResponseWriter, r *http.Request) {
	var body struct {
		RaceData struct {
			Name       string `json:"name"`
			FolderName string `json:"folderName"`
		} `json:"raceData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	_, err := db.DB.Exec(
		"INSERT INTO dog_races (name, folder_name) VALUES ($1, $2)",
		body.RaceData.Name, body.RaceData.FolderName,
	)
	if err != nil {
		http.Error(w, "Error adding race", http.StatusBadRequest)
		return
	}

	w.WriteHeader(http.StatusCreated)
	w.Write([]byte("Race added successfully"))
}

// POST /incrementDogRaceQuestionedCount
// Body: {"raceNameData": {"raceName": "Poodle"}}
func IncrementDogRaceQuestionedCount(w http.ResponseWriter, r *http.Request) {
	var body struct {
		RaceNameData struct {
			RaceName string `json:"raceName"`
		} `json:"raceNameData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	db.DB.Exec(
		"UPDATE dog_races SET times_questioned = times_questioned + 1 WHERE name = $1",
		body.RaceNameData.RaceName,
	)

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Questioned count incremented successfully"))
}
