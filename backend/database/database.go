package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"
	_ "modernc.org/sqlite"
)

const (
	AuthDB = "authServer.db"
	RaceDB = "raceDB.db"
)

type RaceInfo struct {
	ID         int
	Name       string
	FolderName string
}

func openAuthDB() (*sql.DB, error) {
	db, err := sql.Open("sqlite", AuthDB)
	if err != nil {
		return nil, err
	}
	// Serialize writes and give SQLite time to resolve locks.
	db.SetMaxOpenConns(1)
	_, _ = db.Exec("PRAGMA busy_timeout = 5000")
	_, _ = db.Exec("PRAGMA journal_mode = WAL")
	_, _ = db.Exec("PRAGMA synchronous = NORMAL")
	return db, nil
}

func openRaceDB() (*sql.DB, error) {
	db, err := sql.Open("sqlite", RaceDB)
	if err != nil {
		return nil, err
	}
	// Serialize writes and give SQLite time to resolve locks.
	db.SetMaxOpenConns(1)
	_, _ = db.Exec("PRAGMA busy_timeout = 5000")
	_, _ = db.Exec("PRAGMA journal_mode = WAL")
	_, _ = db.Exec("PRAGMA synchronous = NORMAL")
	return db, nil
}

func requestSaltFromDB(username string) (string, error) {
	db, err := openAuthDB()
	if err != nil {
		return "", err
	}
	defer db.Close()

	var salt string
	err = db.QueryRow("SELECT salt FROM users WHERE username = ?", username).Scan(&salt)
	if err != nil {
		return "", err
	}

	return salt, nil
}

func verifyPasswordFromDB(username, passwordHash string) (bool, error) {
	db, err := openAuthDB()
	if err != nil {
		return false, err
	}
	defer db.Close()

	var storedPasswordHash string
	err = db.QueryRow("SELECT password_hash FROM users WHERE username = ?", username).Scan(&storedPasswordHash)
	if err != nil {
		return false, err
	}

	if storedPasswordHash == passwordHash {
		return true, nil
	}

	if bcrypt.CompareHashAndPassword([]byte(storedPasswordHash), []byte(passwordHash)) == nil {
		return true, nil
	}

	return false, nil
}

func isUsernameOrEmailTakenFromDB(username string) (bool, error) {
	db, err := openAuthDB()
	if err != nil {
		return false, err
	}
	defer db.Close()

	var count int
	err = db.QueryRow("SELECT COUNT(*) FROM users WHERE username = ?", username).Scan(&count)
	if err != nil {
		return false, err
	}

	return count > 0, nil
}

func registerUserInDB(name, username, email, passwordHash, salt string) error {
	db, err := openAuthDB()
	if err != nil {
		return err
	}
	defer db.Close()

	_, err = db.Exec(
		"INSERT INTO users (name, username, email, password_hash, salt) VALUES (?, ?, ?, ?, ?)",
		name, username, email, passwordHash, salt,
	)
	if err != nil {
		return fmt.Errorf("username or email already taken")
	}

	return nil
}

func getDogRaceInfoFromDB(raceID *int, raceName string) (*RaceInfo, error) {
	db, err := openRaceDB()
	if err != nil {
		return nil, err
	}
	defer db.Close()

	if raceID != nil {
		return queryRaceInfoByID(db, *raceID)
	} else if raceName != "" {
		if info, err := queryRaceInfoByNameOrFolder(db, raceName); err == nil {
			return info, nil
		}

		folderCandidate := normalizeRaceLookupKey(raceName)
		if folderCandidate != "" {
			folderPath := filepath.Join("racesFolder", folderCandidate)
			if stat, statErr := os.Stat(folderPath); statErr == nil && stat.IsDir() {
				return &RaceInfo{
					ID:         0,
					Name:       folderCandidate,
					FolderName: folderCandidate,
				}, nil
			}
		}

		return nil, sql.ErrNoRows
	} else {
		return nil, fmt.Errorf("invalid request data")
	}
}

func queryRaceInfoByID(db *sql.DB, raceID int) (*RaceInfo, error) {
	var id int
	var name, folderName string
	err := db.QueryRow("SELECT id, name, folderName FROM dog_races WHERE id = ?", raceID).Scan(&id, &name, &folderName)
	if err != nil {
		return nil, err
	}

	return &RaceInfo{ID: id, Name: name, FolderName: folderName}, nil
}

func queryRaceInfoByNameOrFolder(db *sql.DB, raceName string) (*RaceInfo, error) {
	normalized := normalizeRaceLookupKey(raceName)
	if normalized == "" {
		return nil, sql.ErrNoRows
	}

	var id int
	var name, folderName string

	err := db.QueryRow(
		"SELECT id, name, folderName FROM dog_races WHERE lower(name) = ? OR lower(folderName) = ? LIMIT 1",
		normalized,
		normalized,
	).Scan(&id, &name, &folderName)
	if err == nil {
		return &RaceInfo{ID: id, Name: name, FolderName: folderName}, nil
	}

	compact := strings.ReplaceAll(normalized, "_", "")
	if compact == "" {
		return nil, err
	}

	err = db.QueryRow(
		"SELECT id, name, folderName FROM dog_races WHERE REPLACE(lower(name), '_', '') = ? OR REPLACE(lower(folderName), '_', '') = ? LIMIT 1",
		compact,
		compact,
	).Scan(&id, &name, &folderName)
	if err != nil {
		return nil, err
	}

	return &RaceInfo{ID: id, Name: name, FolderName: folderName}, nil
}

func normalizeRaceLookupKey(value string) string {
	v := strings.TrimSpace(strings.ToLower(value))
	v = strings.ReplaceAll(v, "-", "_")
	v = strings.ReplaceAll(v, " ", "_")
	for strings.Contains(v, "__") {
		v = strings.ReplaceAll(v, "__", "_")
	}
	return strings.Trim(v, "_")
}

func incrementTimesSearchedInDB(raceID int) error {
	db, err := openRaceDB()
	if err != nil {
		return err
	}
	defer db.Close()

	for i := 0; i < 5; i++ {
		_, err = db.Exec(
			"UPDATE dog_races SET timesSearched = timesSearched + 1 WHERE id = ?",
			raceID,
		)
		if err == nil {
			return nil
		}
		if !strings.Contains(err.Error(), "database is locked") {
			return err
		}
		time.Sleep(50 * time.Millisecond)
	}
	return err
}

func addRaceToDB(name, folderName string) error {
	db, err := openRaceDB()
	if err != nil {
		return err
	}
	defer db.Close()

	_, err = db.Exec(
		"INSERT INTO dog_races (name, folderName) VALUES (?, ?)",
		name, folderName,
	)
	if err != nil {
		return fmt.Errorf("error adding race")
	}

	return nil
}

func incrementQuestionedCountInDB(raceName string) error {
	db, err := openRaceDB()
	if err != nil {
		return err
	}
	defer db.Close()

	for i := 0; i < 5; i++ {
		_, err = db.Exec(
			"UPDATE dog_races SET timesQuestioned = timesQuestioned + 1 WHERE name = ?",
			raceName,
		)
		if err == nil {
			log.Printf("Incremented timesQuestioned for race: %s\n", raceName)
			return nil
		}
		if !strings.Contains(err.Error(), "database is locked") {
			return err
		}
		time.Sleep(50 * time.Millisecond)
	}

	log.Printf("Incremented timesQuestioned for race: %s\n", raceName)
	return err
}
