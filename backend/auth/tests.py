import helperFunctions
import http.client
import json

def testTokenGeneration():
    user = {
        'id': 'user123',
        'username': 'testuser',
        'email': 'testuser@example.com'
    }
    token = helperFunctions.generateToken(user)
    assert token is not None
    assert isinstance(token, str)
    
    response = helperFunctions.verifyToken(token)
    assert response == True




def testUserRegistrationAndLogin():
    # Test data
    registrationData = {
        'registrationData': {
            'name': 'Test',
            'username': 'testuser',
            'email': 'testuser@example.com',
            'password': 'Test@1234'
        }
    }
    loginData = {
        'loginData': {
            'username': 'testuser',
            'password': 'Test@1234'
        }
    }
    # Register user
    registrationJson = json.dumps(registrationData).encode('utf-8')
    conn = http.client.HTTPConnection('127.0.0.1:8010')
    conn.request("POST", "/register", body=registrationJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 201
    responseData = response.read()
    responseJson = json.loads(responseData)
    assert responseJson.get('message') == 'Registration successful'
    # Login user
    loginJson = json.dumps(loginData).encode('utf-8')
    conn.request("POST", "/login", body=loginJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 200
    responseData = response.read()
    responseJson = json.loads(responseData)
    assert responseJson.get('message') == 'Login successful'
    assert 'token' in responseJson

if __name__ == "__main__":
    testTokenGeneration()
    testUserRegistrationAndLogin()
    print("All tests passed.")