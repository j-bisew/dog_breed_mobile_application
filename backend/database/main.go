package main

import (
	"fmt"
	"log"
	"net/http"
)

const PORT = 8020

var startServer = http.ListenAndServe
var fatalf = log.Fatal

func main() {
	// Setup HTTP routes
	http.HandleFunc("/", handleRoot)
	http.HandleFunc("/requestSalt", handleRequestSalt)
	http.HandleFunc("/verifyUsernamePassword", handleVerifyUsernamePassword)
	http.HandleFunc("/isUsernameOrEmailTaken", handleIsUsernameOrEmailTaken)
	http.HandleFunc("/registerUser", handleRegisterUser)
	http.HandleFunc("/getDogRaceInfo", handleGetDogRaceInfo)
	http.HandleFunc("/addDogRace", handleAddDogRace)
	http.HandleFunc("/incrementDogRaceQuestionedCount", handleIncrementDogRaceQuestionedCount)

	// Start server
	address := fmt.Sprintf(":%d", PORT)
	log.Printf("Starting database handler server on port %d...\n", PORT)
	fatalf(startServer(address, nil))
}

func handleRoot(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		w.WriteHeader(http.StatusOK)
		w.Header().Set("Content-Type", "text/html")
		fmt.Fprint(w, "Welcome to the Database Handler Server!")
	} else {
		http.NotFound(w, r)
	}
}
