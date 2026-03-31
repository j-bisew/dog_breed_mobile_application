package main

import (
	"fmt"
	"log"
	"net/http"
)

const port = 8030

func main() {
	if err := initializeRecognition(); err != nil {
		log.Fatalf("failed to initialize recognition models: %v", err)
	}
	defer shutdownRecognition()

	http.HandleFunc("/", handleRoot)
	http.HandleFunc("/submitDogPhoto", handleSubmitDogPhoto)

	addr := fmt.Sprintf(":%d", port)
	log.Printf("Starting recognition server on port %d...\n", port)
	log.Fatal(http.ListenAndServe(addr, nil))
}

func handleRoot(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		w.Header().Set("Content-Type", "text/html")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("Welcome to the Recognition Server!"))
		return
	}

	http.NotFound(w, r)
}
