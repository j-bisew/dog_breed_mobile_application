import json
import bcrypt
import time
import base64
import hashlib
import http.client
import hmac

def loginPathHandler(self):

    # {loginData: {"username": "user", "password": "pass"}}

    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received login data: {post_data}")
    loginData = post_data.decode('utf-8')
    loginData = json.loads(loginData).get('loginData', {})
    username = loginData.get('username')
    password = loginData.get('password')
    print(f"Username: {username}, Password: {password}")

    # Send request to database server to get stored salt and hashed password
    IP = '127.0.0.1:8020'
    saltRequestData = {
        'usernameData': {
            'username': username
        }
    }
    saltRequestJson = json.dumps(saltRequestData).encode('utf-8')

    conn = http.client.HTTPConnection(IP)
    conn.request("POST", "/requestSalt", body=saltRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status != 200:
        self.send_response(401)
        self.end_headers()
        self.wfile.write(b'User not found')
        return
    
    responseData = response.read()
    responseJson = json.loads(responseData.decode('utf-8'))
    salt = responseJson.get('salt')
    print(f"Retrieved salt: {salt}")

    # Verify password
    hashedPassword = bcrypt.hashpw(password.encode('utf-8'), salt.encode('utf-8'))

    verifyRequestData = {
        'loginData': {
            'username': username,
            'password': hashedPassword.decode('utf-8')
        }
    }
    verifyRequestJson = json.dumps(verifyRequestData).encode('utf-8')
    conn.request("POST", "/verifyUsernamePassword", body=verifyRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status != 200:
        self.send_response(401)
        self.end_headers()
        self.wfile.write(b'Invalid username or password')
        return



    generatedToken = generateToken({
        'username': username
    })
    
    # Send response
    self.send_response(200)
    self.end_headers()
    response = {
        'message': 'Login successful',
        'token': generatedToken
    }
    self.wfile.write(json.dumps(response).encode('utf-8'))


def registerPathHandler(self):
    
    # {registrationData: {"name": "Adam", "username": "adam123", "email": "adam@example.com", "password": "pass"}}
    
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received registration data: {post_data}")
    registrationData = post_data.decode('utf-8')
    registrationData = json.loads(registrationData).get('registrationData', {})
    name = registrationData.get('name')
    username = registrationData.get('username')
    email = registrationData.get('email')
    password = registrationData.get('password')

    def verifyInputs(name, username, email, password):
        if not name or not email or not password:
            return False
        if len(password) < 8:
            return False
        if "@" not in email or "." not in email:
            return False
        #check for special characters in password
        special_characters = "!@#$%^&*()-+?_=,<>/"
        if not any(char in special_characters for char in password):
            return False
        if not name.isalnum():
            return False
        if not username.isalnum():
            return False
        
        conn = http.client.HTTPConnection('127.0.0.1:8020')
        usernameCheckData = {
            'usernameData': {
                'username': username
            }
        }
        usernameCheckJson = json.dumps(usernameCheckData).encode('utf-8')
        conn.request("POST", "/isUsernameOrEmailTaken", body=usernameCheckJson, headers={'Content-Type': 'application/json'})
        response = conn.getresponse()
        if response.status != 200:
            return False    

        return True
    
    if not verifyInputs(name, username, email, password):
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Invalid registration data')
        print("Invalid registration data")
        return
    
    salt = bcrypt.gensalt()
    hashedPassword = bcrypt.hashpw(password.encode('utf-8'), salt)
    print(f"Storing user: Name: {name}, Email: {email}, Hashed Password: {hashedPassword}")

    # Send request to database server to register user
    IP = '127.0.0.1:8020'
    registrationRequestData = {
        'registrationData': {
            'name': name,
            'username': username,
            'email': email,
            'password_hash': hashedPassword.decode('utf-8'),
            'salt': salt.decode('utf-8')
        }
    }
    registrationRequestJson = json.dumps(registrationRequestData).encode('utf-8')
    conn = http.client.HTTPConnection(IP)
    conn.request("POST", "/registerUser", body=registrationRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status != 201:
        self.send_response(500)
        self.end_headers()
        self.wfile.write(b'Failed to register user')
        return
    

    generatedToken = generateToken({
        'username': name,
    })

    # Send response
    self.send_response(201)
    self.end_headers()
    response = {
        'message': 'Registration successful',
        'token': generatedToken
    }
    self.wfile.write(json.dumps(response).encode('utf-8'))
    
    
TOKEN_TTL = 7200

def load_secret_key():
    try:
        with open('keys.txt', 'rb') as f:
            return f.read()
    except Exception as e:
        raise ValueError("Failed to load secret key: " + str(e))

def generateToken(user):
    try:
        secret_key = load_secret_key()

        timestamp = str(int(time.time()))
        token = f"{user['username']}|{timestamp}"

        signature = hmac.new(secret_key, token.encode('utf-8'), hashlib.sha256).hexdigest()

        token_with_signature = f"{token}|{signature}"

        encoded_token = base64.urlsafe_b64encode(token_with_signature.encode('utf-8')).decode('utf-8')

        return encoded_token
    except Exception as e:
        print(f"Error generating token: {e}")
        return None

def verifyToken(token):
    try:
        decoded = base64.urlsafe_b64decode(token).decode('utf-8')

        username, timestamp, signature = decoded.split('|')

        secret_key = load_secret_key()

        expected_signature = hmac.new(secret_key, f"{username}|{timestamp}".encode('utf-8'), hashlib.sha256).hexdigest()

        if not hmac.compare_digest(expected_signature, signature):
            print("Signature mismatch!")
            return False

        current_time = int(time.time())
        if current_time - int(timestamp) > TOKEN_TTL:
            print("Token expired!")
            return False

        return True
    except Exception as e:
        print(f"Token verification failed: {e}")
        return False


def verifyTokenPathHandler(self):
    # {tokenData: {"token": "<token_string>"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received token data: {post_data}")
    tokenData = post_data.decode('utf-8')
    tokenData = json.loads(tokenData).get('tokenData', {})
    token = tokenData.get('token')
    if verifyToken(token):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'Token is valid')
    else:
        self.send_response(401)
        self.end_headers()
        self.wfile.write(b'Invalid or expired token')