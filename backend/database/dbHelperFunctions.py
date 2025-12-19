import sqlite3
import os
import json

AUTH_DB = 'authServer.db'
RACE_DB = 'raceDB.db'

authDBConnection = sqlite3.connect(AUTH_DB)
authDBCursor = authDBConnection.cursor()

raceDBConnection = sqlite3.connect(RACE_DB)
raceDBCursor = raceDBConnection.cursor()

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


def getDogRaceInfoPathHandler(self):
    # {raceRequestData: {"raceId": 1, "name": "Poodle"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received race request data: {post_data}")
    raceRequestData = post_data.decode('utf-8')
    raceRequestData = json.loads(raceRequestData).get('raceRequestData', {})
    raceId = raceRequestData.get('raceId')
    name = raceRequestData.get('name')
    confidence = raceRequestData.get('confidence')

    if raceId is not None:
        query = "SELECT id, name, folderName FROM dog_races WHERE id = ?"
        params = (raceId,)
    elif name is not None:
        query = "SELECT id, name, folderName FROM dog_races WHERE name = ?"
        params = (name,)
    else:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Invalid request data')
        return
    
    raceDBCursor.execute(query, params)
    result = raceDBCursor.fetchone()
    if result:
        raceInfo = {
            'id': result[0],
            'name': result[1],
            'folderName': result[2]
        }

        # Increment timesSearched
        raceDBCursor.execute('''
            UPDATE dog_races
            SET timesSearched = timesSearched + 1
            WHERE id = ?
        ''', (raceInfo['id'],))
        raceDBConnection.commit()

        # Build metadata (JSON) and attach main photo as a separate multipart part
        response_metadata = {}

        mainBreedPhotoPath = os.path.join('racesFolder', raceInfo['folderName'], 'mainPhoto.jpg')
        photo_bytes = None
        if os.path.exists(mainBreedPhotoPath):
            with open(mainBreedPhotoPath, 'rb') as f:
                photo_bytes = f.read()

        breedInfoPath = os.path.join('racesFolder', raceInfo['folderName'], 'summary')
        if os.path.exists(breedInfoPath):
            with open(breedInfoPath, 'r', encoding='utf-8') as f:
                breedInfo = f.read()
            breedFullName = breedInfo.split('\n')[0].split(':')[1].strip()
            breedDescription = breedInfo.split('\n')[1].split(':')[1].strip()
            response_metadata['breedName'] = raceInfo['name']
            response_metadata['breedFullName'] = breedFullName
            response_metadata['breedDescription'] = breedDescription
            if confidence is not None:
                response_metadata['confidence'] = confidence

        # If we have photo bytes, respond using multipart/mixed with two parts:
        # part 1 = application/json metadata, part 2 = binary image (jpeg)
        if photo_bytes is not None:
            boundary = '----Boundary' + str(int.from_bytes(os.urandom(4), 'big'))
            crlf = b"\r\n"
            body = b''

            # Part 1: JSON metadata
            body += b'--' + boundary.encode('utf-8') + crlf
            body += b'Content-Type: application/json; charset=utf-8' + crlf + crlf
            body += json.dumps(response_metadata).encode('utf-8') + crlf

            # Part 2: image attachment
            body += b'--' + boundary.encode('utf-8') + crlf
            body += b'Content-Type: image/jpeg' + crlf
            body += b'Content-Disposition: attachment; filename="mainPhoto.jpg"' + crlf
            body += b'Content-Transfer-Encoding: binary' + crlf + crlf
            body += photo_bytes + crlf

            # Closing boundary
            body += b'--' + boundary.encode('utf-8') + b'--' + crlf

            self.send_response(200)
            self.send_header('Content-Type', f'multipart/mixed; boundary={boundary}')
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(body)
        else:
            # No photo available — return JSON metadata only
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(response_body)





    else:
        self.send_response(404)
        self.end_headers()
        self.wfile.write(b'Race not found')


def addRacePathHandler(self):
    # {raceData: {"name": "Poodle", "folderName": "poodle_folder"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received race data: {post_data}")
    raceData = post_data.decode('utf-8')
    raceData = json.loads(raceData).get('raceData', {})
    name = raceData.get('name')
    folderName = raceData.get('folderName')
    try:
        raceDBCursor.execute('''
            INSERT INTO dog_races (name, folderName)
            VALUES (?, ?)
        ''', (name, folderName))
        raceDBConnection.commit()
        self.send_response(201)
        self.end_headers()
        self.wfile.write(b'Race added successfully')
    except sqlite3.IntegrityError as e:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Error adding race')

def incrementDogRaceQuestionedCountPathHandler(self):
    # {raceNameData: {"raceName": "Poodle"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received race ID data for search count increment: {post_data}")
    raceNameData = post_data.decode('utf-8')
    raceNameData = json.loads(raceNameData).get('raceNameData', {})
    raceName = raceNameData.get('raceName')
    raceDBCursor.execute('''
        UPDATE dog_races
        SET timesQuestioned = timesQuestioned + 1
        WHERE name = ?
    ''', (raceName,))
    raceDBConnection.commit()
    self.send_response(200)
    self.end_headers()
    self.wfile.write(b'Questioned count incremented successfully')