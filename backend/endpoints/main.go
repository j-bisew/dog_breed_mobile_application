package main

import (
	"fmt"
	"log"
	"net/http"
)

const port = 8000

var startServer = http.ListenAndServe
var fatalf = log.Fatal

func main() {
	http.HandleFunc("/", handleRoot)
	http.HandleFunc("/registerUser", handleRegisterUser)
	http.HandleFunc("/loginUser", handleLoginUser)
	http.HandleFunc("/getDogBreedInfo", handleGetDogBreedInfo)
	http.HandleFunc("/submitDogBreedFeedback", handleSubmitDogBreedFeedback)

	address := fmt.Sprintf(":%d", port)
	log.Printf("Starting endpoints server on port %d...\n", port)
	fatalf(startServer(address, nil))
}

func handleRoot(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		w.Header().Set("Content-Type", "text/html")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("Welcome to the Endpoints Server!"))
		return
	}

	http.NotFound(w, r)
}
