import json
import http.client

AUTH_IP = '127.0.0.1:8010'
DATABASE_IP = '127.0.0.1:8020'
RECOGNITION_IP = '127.0.0.1:8030'

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
    conn = http.client.HTTPConnection(DATABASE_IP)
    conn.request("POST", "/registerUser", body=registrationRequestJson, headers={'Content-Type': 'application/json'})
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
    conn = http.client.HTTPConnection(DATABASE_IP)
    conn.request("POST", "/verifyUsernamePassword", body=loginRequestJson, headers={'Content-Type': 'application/json'})
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
    
    contentType = self.headers.get('Content-Type')

    if not contentType:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Content-Type header missing')
        return
    
    if contentType.startswith('multipart/form-data'):
        
        boundary = contentType.split("boundary=")[1]
        content_length = int(self.headers['Content-Length'])
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
            

        recognitionRequestData = {
            'photoData': photoData.decode('latin1')
        }
        # encode JSON using latin1 so the recognition server can decode with latin1
        recognitionRequestJson = json.dumps(recognitionRequestData).encode('latin1')

        conn = http.client.HTTPConnection(RECOGNITION_IP)
        conn.request("POST", "/submitDogPhoto", body=recognitionRequestJson, headers={'Content-Type': 'application/json'})
        response = conn.getresponse()

        if response.status == 200:
            response_data = response.read()
            
            response_json = json.loads(response_data.decode('utf-8'))
            breedName = response_json.get('breedName', 'UnknownBreed')
            assurance = response_json.get('assurance', 0.0)
            breedInfoRequestData = {
                'raceRequestData': {
                    'name': breedName
                }
            }
            breedInfoRequestJson = json.dumps(breedInfoRequestData).encode('utf-8')
            conn = http.client.HTTPConnection(DATABASE_IP)
            conn.request("POST", "/getDogRaceInfo", body=breedInfoRequestJson, headers={'Content-Type': 'application/json'})
            breedInfoResponse = conn.getresponse()
            print(f"Breed info response status: {breedInfoResponse.status}")
            response_data = breedInfoResponse.read()
            print(f"Breed info response data: {response_data}")
            if breedInfoResponse.status == 200:
                
                self.send_response(200)
                self.end_headers()
                self.wfile.write(response_data)




            else:
                self.send_response(breedInfoResponse.status)
                self.end_headers()
                breedInfoData = breedInfoResponse.read()
                self.wfile.write(breedInfoData)
        
        else:
            self.send_response(response.status)
            self.end_headers()
            response_data = response.read()
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
    