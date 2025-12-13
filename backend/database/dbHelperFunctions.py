import sqlite3
import json

AUTH_DB = 'authServer.db'

authDBConnection = sqlite3.connect(AUTH_DB)
authDBCursor = authDBConnection.cursor()

def requestSaltPathHandler(self):
    # {usernameData: {"username": "user"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received username data: {post_data}")
    usernameData = post_data.decode('utf-8')
    usernameData = json.loads(usernameData).get('usernameData', {})
    username = usernameData.get('username')

    authDBCursor.execute("SELECT salt FROM users WHERE username = ?", (username,))
    result = authDBCursor.fetchone()
    if result:
        salt = result[0]
        self.send_response(200)
        self.end_headers()
        response = {
            'salt': salt
        }
        self.wfile.write(json.dumps(response).encode('utf-8'))
    else:
        self.send_response(404)
        self.end_headers()
        self.wfile.write(b'User not found')

def verifyUsernamePasswordPathHandler(self):
    # {loginData: {"username": "user", "password": "passwordHash"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received login data: {post_data}")
    loginData = post_data.decode('utf-8')
    loginData = json.loads(loginData).get('loginData', {})
    username = loginData.get('username')
    passwordHash = loginData.get('password')
    authDBCursor.execute("SELECT password_hash FROM users WHERE username = ?", (username,))
    result = authDBCursor.fetchone()
    if result:
        storedPasswordHash = result[0]
        if storedPasswordHash == passwordHash:
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'Verification successful')
        else:
            self.send_response(401)
            self.end_headers()
            self.wfile.write(b'Invalid password')
    else:
        self.send_response(404)
        self.end_headers()
        self.wfile.write(b'User not found')

def isUsernameOrEmailTakenPathHandler(self):
    # {usernameData: {"username": "user"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received username data: {post_data}")
    usernameData = post_data.decode('utf-8')
    usernameData = json.loads(usernameData).get('usernameData', {})
    username = usernameData.get('username')

    authDBCursor.execute("SELECT COUNT(*) FROM users WHERE username = ?", (username,))
    result = authDBCursor.fetchone()
    isTaken = result[0] > 0

    self.send_response(200)
    self.end_headers()
    response = {
        'isTaken': isTaken
    }
    self.wfile.write(json.dumps(response).encode('utf-8'))

def registerUserPathHandler(self):
    # {registrationData: {"name": "Name", "username": "user", "email": "email", "password_hash": "hash", "salt": "salt"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received registration data: {post_data}")
    registrationData = post_data.decode('utf-8')
    registrationData = json.loads(registrationData).get('registrationData', {})
    name = registrationData.get('name')
    username = registrationData.get('username')
    email = registrationData.get('email')
    password_hash = registrationData.get('password_hash')
    salt = registrationData.get('salt')

    try:
        authDBCursor.execute('''
            INSERT INTO users (name, username, email, password_hash, salt)
            VALUES (?, ?, ?, ?, ?)
        ''', (name, username, email, password_hash, salt))
        authDBConnection.commit()
        self.send_response(201)
        self.end_headers()
        self.wfile.write(b'User registered successfully')
    except sqlite3.IntegrityError as e:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Username or email already taken')