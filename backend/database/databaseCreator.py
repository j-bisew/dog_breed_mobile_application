import sqlite3

def deleteDBIfExists(dbName):
    import os
    if os.path.exists(dbName):
        os.remove(dbName)
        print(f"Deleted existing database: {dbName}")

def createDatabase(dbName):
    conn = sqlite3.connect(dbName)
    cursor = conn.cursor()
    
    # Create users table
    # Remove any existing users table (to ensure the correct schema) and recreate it
    cursor.execute('DROP TABLE IF EXISTS users')
    cursor.execute('''
        CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            username TEXT UNIQUE NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            salt TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')

    # Create nonces table
    cursor.execute('''
        CREATE TABLE nonces (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nonce TEXT UNIQUE NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    
    conn.commit()
    conn.close()
    print(f"Created new database: {dbName}")

if __name__ == "__main__":
    dbName = 'authServer.db'
    deleteDBIfExists(dbName)
    createDatabase(dbName)
