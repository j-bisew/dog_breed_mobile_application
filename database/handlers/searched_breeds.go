package handlers

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/dogbreed/database/db"
)

func incrementSearchedBreed(username, breedName string) error {
	if username == "" || breedName == "" {
		return nil
	}

	var userID int
	if err := db.DB.QueryRow("SELECT id FROM users WHERE username = $1", username).Scan(&userID); err != nil {
		return err
	}

	_, err := db.DB.Exec(
		`INSERT INTO searched_breeds (user_id, breed_name, times_searched)
		 VALUES ($1, $2, 1)
		 ON CONFLICT (user_id, breed_name)
		 DO UPDATE SET times_searched = searched_breeds.times_searched + 1`,
		userID, breedName,
	)
	return err
}

// POST /getUserSearchedBreedsSummary
// Body: {"usernameData": {"username": "user"}}
func GetUserSearchedBreedsSummary(w http.ResponseWriter, r *http.Request) {
	var body struct {
		UsernameData struct {
			Username string `json:"username"`
		} `json:"usernameData"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, "Bad request", http.StatusBadRequest)
		return
	}

	username := body.UsernameData.Username
	if username == "" {
		http.Error(w, "Username required", http.StatusBadRequest)
		return
	}

	var userID int
	if err := db.DB.QueryRow("SELECT id FROM users WHERE username = $1", username).Scan(&userID); err != nil {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		json.NewEncoder(w).Encode(map[string]interface{}{"breeds": []interface{}{}})
		return
	}

	rows, err := db.DB.Query(
		`SELECT breed_name, times_searched
		 FROM searched_breeds
		 WHERE user_id = $1
		 ORDER BY times_searched DESC, breed_name ASC`,
		userID,
	)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	type breedSummary struct {
		BreedName     string `json:"breedName"`
		TimesSearched int    `json:"timesSearched"`
	}
	summaries := make([]breedSummary, 0)
	for rows.Next() {
		var s breedSummary
		if err := rows.Scan(&s.BreedName, &s.TimesSearched); err != nil {
			http.Error(w, "Database error", http.StatusInternalServerError)
			return
		}
		summaries = append(summaries, s)
	}

	if err := rows.Err(); err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	if err := json.NewEncoder(w).Encode(map[string]interface{}{"breeds": summaries}); err != nil {
		log.Printf("Failed to write response: %v", err)
	}
}
