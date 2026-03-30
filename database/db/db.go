package db

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"time"

	_ "github.com/lib/pq"
)

var DB *sql.DB

func Connect() {
	dsn := fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		getEnv("POSTGRES_HOST", "postgres"),
		getEnv("POSTGRES_PORT", "5432"),
		getEnv("POSTGRES_USER", "dogbreed"),
		getEnv("POSTGRES_PASSWORD", "dogbreed"),
		getEnv("POSTGRES_DB", "dogbreed"),
	)

	var err error
	for i := 1; i <= 10; i++ {
		DB, err = sql.Open("postgres", dsn)
		if err == nil {
			if err = DB.Ping(); err == nil {
				log.Println("Connected to PostgreSQL")
				return
			}
		}
		log.Printf("Waiting for PostgreSQL... attempt %d/10: %v", i, err)
		time.Sleep(2 * time.Second)
	}
	log.Fatalf("Failed to connect to PostgreSQL after 10 attempts: %v", err)
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok {
		return v
	}
	return fallback
}
