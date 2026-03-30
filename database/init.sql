-- PostgreSQL initialization script for Dog Breed application

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    total_searches INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS nonces (
    id SERIAL PRIMARY KEY,
    nonce TEXT UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dog_races (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    folder_name TEXT NOT NULL,
    times_searched INTEGER DEFAULT 0,
    times_questioned INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS searched_breeds (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    breed_name TEXT NOT NULL,
    times_searched INTEGER DEFAULT 1,
    UNIQUE (user_id, breed_name)
);
