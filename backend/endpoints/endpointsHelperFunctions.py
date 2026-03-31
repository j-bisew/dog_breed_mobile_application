import json
import os
import http.client

AUTH_IP = 'host.docker.internal:8010'
DATABASE_IP = 'host.docker.internal:8020'
RECOGNITION_IP = 'host.docker.internal:8030'

def registerUserPathHandler(self):
    # {registrationData: {"name": "Name", "username": "user", "email": "email", "password": "password"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received registration data: {post_data}")
    userData = post_data.decode('utf-8')
    userData = json.loads(userData).get('registrationData', {})
    name = userData.get('name')
    username = userData.get('username')
    email = userData.get('email')
    password = userData.get('password')
    registrationRequestData = {
        'registrationData': {
            'name': name,
            'username': username,
            'email': email,
            'password': password
        }
    }
    registrationRequestJson = json.dumps(registrationRequestData).encode('utf-8')
    conn = http.client.HTTPConnection(AUTH_IP)
    conn.request("POST", "/register", body=registrationRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status == 200:
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'Registration successful')
    else:
        self.send_response(response.status)
        self.end_headers()
        response_data = response.read()
        self.wfile.write(response_data)

def loginUserPathHandler(self):
    # {loginData: {"username": "user", "password": "password"}}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received login data: {post_data}")
    loginData = post_data.decode('utf-8')
    loginData = json.loads(loginData).get('loginData', {})
    username = loginData.get('username')
    password = loginData.get('password')
    loginRequestData = {
        'loginData': {
            'username': username,
            'password': password
        }
    }
    loginRequestJson = json.dumps(loginRequestData).encode('utf-8')
    conn = http.client.HTTPConnection(AUTH_IP)
    conn.request("POST", "/login", body=loginRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status == 200:
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'Login successful')
    else:
        self.send_response(response.status)
        self.end_headers()
        response_data = response.read()
        self.wfile.write(response_data)

def getDogBreedInfoPathHandler(self):
    # accept user made photo and return breed info

    print("Handling getDogBreedInfoPathHandler request")
    
    contentType = self.headers.get('Content-Type')

    if not contentType:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Content-Type header missing')
        return
    
    if contentType.startswith('multipart/form-data'):
        
        boundary = contentType.split("boundary=")[1]
        content_length = int(self.headers['Content-Length'])

        # Limit content length to 10MB
        if content_length > 20 * 1024 * 1024:
            self.send_response(413)
            self.end_headers()
            self.wfile.write(b'Request entity too large')
            return

        post_data = self.rfile.read(content_length)
        print(f"Received multipart/form-data of length: {len(post_data)}")
        parts = post_data.split(b'--' + boundary.encode())
        photoData = None
        for part in parts:
            if b'Content-Disposition' in part and b'name="photo"' in part:
                photoDataStart = part.find(b'\r\n\r\n') + 4
                photoDataEnd = part.rfind(b'\r\n')
                photoData = part[photoDataStart:photoDataEnd]
                break
        if photoData is None:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'Photo data not found in the request')
            return
        
              
        
        print(f"Received photo data of length: {len(photoData)}")


        # Save the received photo as mainPhoto.jpg for testing purposes
        with open('aaaa.jpg', 'wb') as f:
            f.write(photoData)

        # Build multipart/form-data body to send the raw binary photo to the Recognition service
        boundary = '----Boundary' + str(int.from_bytes(os.urandom(4), 'big'))
        crlf = b"\r\n"
        body = b''
        body += b'--' + boundary.encode('utf-8') + crlf
        body += b'Content-Disposition: form-data; name="photo"; filename="photo.jpg"' + crlf
        body += b'Content-Type: image/jpeg' + crlf + crlf
        body += photoData + crlf
        body += b'--' + boundary.encode('utf-8') + b'--' + crlf

        conn = http.client.HTTPConnection(RECOGNITION_IP)
        conn.request("POST", "/submitDogPhoto", body=body, headers={'Content-Type': f'multipart/form-data; boundary={boundary}'})
        response = conn.getresponse()

        if response.status == 200:
            response_data = response.read()
            
            response_json = json.loads(response_data.decode('utf-8'))
            breedName = response_json.get('breedName', 'UnknownBreed')
            confidence = response_json.get('confidence', 0.0)
            breedInfoRequestData = {
                'raceRequestData': {
                    'name': breedName,
                    'confidence': confidence
                }
            }
            breedInfoRequestJson = json.dumps(breedInfoRequestData).encode('utf-8')
            conn = http.client.HTTPConnection(DATABASE_IP)
            conn.request("POST", "/getDogRaceInfo", body=breedInfoRequestJson, headers={'Content-Type': 'application/json'})
            breedInfoResponse = conn.getresponse()
            print(f"Breed info response status: {breedInfoResponse.status}")
            response_data = breedInfoResponse.read()
            conn.close()
            if breedInfoResponse.status == 200:
                
                self.send_response(200)
                # Forward the Content-Type header (important for multipart responses)
                contentType = breedInfoResponse.getheader('Content-Type')
                if contentType:
                    self.send_header('Content-Type', contentType)
                else:
                    # Fallback if header is missing for some reason
                    self.send_header('Content-Type', 'application/json')
                
                self.send_header('Content-Length', str(len(response_data)))
                self.send_header('Connection', 'close')
                
                self.end_headers()
                self.wfile.write(response_data)
            else:
                self.send_response(breedInfoResponse.status)
                self.send_header('Content-Length', str(len(response_data)))
                self.send_header('Connection', 'close')
                self.end_headers()
                self.wfile.write(response_data)
        
        else:
            self.send_response(response.status)
            response_data = response.read()
            self.send_header('Content-Length', str(len(response_data)))
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(response_data)
        
        
    else:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Unsupported Content-Type')

def submitDogBreedFeedbackPathHandler(self):
    # {raceNameData: {"raceName": "Poodle"}}

    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received breed feedback data: {post_data}")
    raceNameData = post_data.decode('utf-8')
    raceNameData = json.loads(raceNameData).get('raceNameData', {})
    raceName = raceNameData.get('raceName')
    feedbackRequestData = {
        'raceNameData': {
            'raceName': raceName
        }
    }
    feedbackRequestJson = json.dumps(feedbackRequestData).encode('utf-8')
    conn = http.client.HTTPConnection(DATABASE_IP)
    conn.request("POST", "/incrementDogRaceQuestionedCount", body=feedbackRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status == 200:
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'Feedback submitted successfully')
    else:
        self.send_response(response.status)
        self.end_headers()
        response_data = response.read()
        self.wfile.write(response_data)
    
def authorizationCheck(self):
    # Check for Authorization header
    authHeader = self.headers.get('Authorization')
    if not authHeader:
        print("Authorization header missing")
        return False

    token = authHeader.split(" ")[1] if " " in authHeader else authHeader

    # Verify token with auth server
    conn = http.client.HTTPConnection(AUTH_IP)
    tokenData = {
        'tokenData': {
            'token': token
        }
    }
    tokenRequestJson = json.dumps(tokenData).encode('utf-8')
    conn.request("POST", "/verifyToken", body=tokenRequestJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    if response.status == 200:
        print("Authorization successful")
        return True
    else:
        print("Authorization failed")
        return False