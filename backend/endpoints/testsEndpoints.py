import endpointsHelperFunctions
import http.client
import json
import random

photoPath = 'temp_photo.jpg'
url = '127.0.0.1:8000'

name = 'USER'
username = ''.join(random.choices('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', k=10))
email = ''.join(random.choices('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', k=6))+'@example.com'
password = 'Password!23'

token = ''

def testUserRegistration():
    registrationData = {
        'registrationData': {
            'name': name,
            'username': username,
            'email': email,
            'password': password
        }
    }
    registrationJson = json.dumps(registrationData).encode('utf-8')
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/registerUser", body=registrationJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 201
    responseData = response.read()
    print(responseData)

def testSameUsernameRegistration():
    registrationData = {
        'registrationData': {
            'name': name,
            'username': username,
            'email': 'different'+email,
            'password': password
        }
    }
    registrationJson = json.dumps(registrationData).encode('utf-8')
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/registerUser", body=registrationJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 400
    responseData = response.read()
    print(responseData)

def testSameEmailRegistration():
    registrationData = {
        'registrationData': {
            'name': name,
            'username': 'different'+username,
            'email': email,
            'password': password
        }
    }
    registrationJson = json.dumps(registrationData).encode('utf-8')
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/registerUser", body=registrationJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 400
    responseData = response.read()
    print(responseData)

def testUserLogin():
    global token
    loginData = {
        'loginData': {
            'username': username,
            'password': password
        }
    }
    loginJson = json.dumps(loginData).encode('utf-8')
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/loginUser", body=loginJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 200
    responseData = response.read()
    print(responseData)
    responseJson = json.loads(responseData.decode('utf-8'))
    token = responseJson.get('token', '')
    assert token != ''

def testInvalidUserLogin():
    loginData = {
        'loginData': {
            'username': username,
            'password': 'WrongPassword'
        }
    }
    loginJson = json.dumps(loginData).encode('utf-8')
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/loginUser", body=loginJson, headers={'Content-Type': 'application/json'})
    response = conn.getresponse()
    assert response.status == 401
    responseData = response.read()
    print(responseData)

def testUnauthorizedGetDogBreedInfo():
    photo = open(photoPath, 'rb').read()
    boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW'
    dataList = []
    dataList.append('--' + boundary)
    dataList.append('Content-Disposition: form-data; name="photo"; filename="{}"'.format(photoPath))
    dataList.append('Content-Type: application/octet-stream')
    dataList.append('')
    dataList.append(photo.decode('ISO-8859-1'))
    dataList.append('--' + boundary + '--')
    body = '\r\n'.join(dataList).encode('ISO-8859-1')
    headers = {
        'Content-Type': 'multipart/form-data; boundary={}'.format(boundary),
        'Content-Length': str(len(body))
    }
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/getDogBreedInfo", body=body, headers=headers)
    response = conn.getresponse()
    assert response.status == 401
    responseData = response.read()
    print(responseData)

def testIncorrectTokenGetDogBreedInfo():
    photo = open(photoPath, 'rb').read()
    boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW'
    dataList = []
    dataList.append('--' + boundary)
    dataList.append('Content-Disposition: form-data; name="photo"; filename="{}"'.format(photoPath))
    dataList.append('Content-Type: application/octet-stream')
    dataList.append('')
    dataList.append(photo.decode('ISO-8859-1'))
    dataList.append('--' + boundary + '--')
    body = '\r\n'.join(dataList).encode('ISO-8859-1')
    headers = {
        'Content-Type': 'multipart/form-data; boundary={}'.format(boundary),
        'Content-Length': str(len(body)),
        'Authorization': 'Bearer IncorrectToken'
    }
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/getDogBreedInfo", body=body, headers=headers)
    response = conn.getresponse()
    assert response.status == 401
    responseData = response.read()
    print(responseData)



def testGetDogBreedInfo():
    photo = open(photoPath, 'rb').read()
    boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW'
    dataList = []
    dataList.append('--' + boundary)
    dataList.append('Content-Disposition: form-data; name="photo"; filename="{}"'.format(photoPath))
    dataList.append('Content-Type: application/octet-stream')
    dataList.append('')
    dataList.append(photo.decode('ISO-8859-1'))
    dataList.append('--' + boundary + '--')
    body = '\r\n'.join(dataList).encode('ISO-8859-1')
    headers = {
        'Content-Type': 'multipart/form-data; boundary={}'.format(boundary),
        'Content-Length': str(len(body)),
        'Authorization': 'Bearer {}'.format(token)
    }
    conn = http.client.HTTPConnection(url)
    conn.request("POST", "/getDogBreedInfo", body=body, headers=headers)
    response = conn.getresponse()
    assert response.status == 200
    responseData = response.read()
    print(responseData)
    responseJson = json.loads(responseData)
    assert 'breedFullName' in responseJson
    assert 'breedDescription' in responseJson
    assert responseJson['mainPhotoData'] is not None

    # Save the received main photo data to verify it's correct
    mainPhotoData = responseJson['mainPhotoData'].encode('latin1')
    with open('received_main_photo.jpg', 'wb') as f:
        f.write(mainPhotoData)




if __name__ == "__main__":
    testUserRegistration()
    testUserLogin()
    testGetDogBreedInfo()
    print("All tests passed.")