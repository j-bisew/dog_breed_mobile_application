package main

import (
	"fmt"
	"log"
	"net/http"
)

const PORT = 8010

var startServer = http.ListenAndServe
var fatalf = log.Fatal

func main() {
	// Setup HTTP routes
	http.HandleFunc("/", handleRoot)
	http.HandleFunc("/login", handleLogin)
	http.HandleFunc("/register", handleRegister)
	http.HandleFunc("/verifyToken", handleVerifyToken)

	// Start server
	address := fmt.Sprintf(":%d", PORT)
	log.Printf("Starting auth server on port %d...\n", PORT)
	fatalf(startServer(address, nil))
}

func handleRoot(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		w.WriteHeader(http.StatusOK)
		w.Header().Set("Content-Type", "text/html")
		fmt.Fprint(w, "Welcome to the Auth Server!")
	} else {
		http.NotFound(w, r)
	}
}
