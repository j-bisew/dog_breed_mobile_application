package main

import (
	"log"
	"net/http"

	"github.com/dogbreed/database/db"
	"github.com/dogbreed/database/handlers"
)

func main() {
	db.Connect()

	mux := http.NewServeMux()

	mux.HandleFunc("GET /", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("Welcome to the Database Handler Server!"))
	})

	// Auth
	mux.HandleFunc("POST /requestSalt", handlers.RequestSalt)
	mux.HandleFunc("POST /verifyUsernamePassword", handlers.VerifyUsernamePassword)
	mux.HandleFunc("POST /isUsernameOrEmailTaken", handlers.IsUsernameOrEmailTaken)
	mux.HandleFunc("POST /registerUser", handlers.RegisterUser)

	// Races
	mux.HandleFunc("POST /getDogRaceInfo", handlers.GetDogRaceInfo)
	mux.HandleFunc("POST /addDogRace", handlers.AddDogRace)
	mux.HandleFunc("POST /incrementDogRaceQuestionedCount", handlers.IncrementDogRaceQuestionedCount)
	mux.HandleFunc("POST /getUserSearchedBreedsSummary", handlers.GetUserSearchedBreedsSummary)

	log.Println("Starting database handler server on port 8020...")
	if err := http.ListenAndServe(":8020", mux); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
