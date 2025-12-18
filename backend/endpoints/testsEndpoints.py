import endpointsHelperFunctions
import http.client
import json
import random

photoPath = 'mainPhoto.jpg'
url = 'host.docker.internal:8000'

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
    content_type = response.getheader('Content-Type') or ''
    responseData = response.read()
    print(responseData[:200])

    # If the response is multipart, parse the parts: first should be JSON metadata,
    # second should be the binary image. Otherwise, try to parse JSON as before.
    if content_type.startswith('multipart/') and 'boundary=' in content_type:
        boundary = content_type.split('boundary=')[1]
        parts = responseData.split(b'--' + boundary.encode())
        metadata = None
        image_bytes = None
        for part in parts:
            if not part or part == b'--' or part == b'--\r\n':
                continue
            # strip leading/trailing CRLF
            part = part.strip(b'\r\n')
            header_end = part.find(b'\r\n\r\n')
            if header_end == -1:
                continue
            headers_blob = part[:header_end].decode('utf-8', errors='ignore')
            body_blob = part[header_end+4:]
            if 'application/json' in headers_blob:
                try:
                    metadata = json.loads(body_blob.decode('utf-8'))
                except Exception:
                    metadata = None
            elif headers_blob.lower().find('image') != -1 or b'Content-Type: image' in part:
                image_bytes = body_blob

        assert metadata is not None, 'Metadata JSON part missing or invalid'
        assert 'breedFullName' in metadata
        assert 'breedDescription' in metadata
        assert image_bytes is not None and len(image_bytes) > 0

        with open('received_main_photo.jpg', 'wb') as f:
            f.write(image_bytes)
    else:
        # Fallback: old behavior expecting JSON with latin1-encoded photo
        try:
            responseJson = json.loads(responseData.decode('utf-8'))
        except Exception:
            assert False, 'Response not JSON and not multipart'
        assert 'breedFullName' in responseJson
        assert 'breedDescription' in responseJson
        assert responseJson.get('mainPhotoData') is not None
        mainPhotoData = responseJson['mainPhotoData'].encode('latin1')
        with open('received_main_photo.jpg', 'wb') as f:
            f.write(mainPhotoData)




if __name__ == "__main__":
    testUserRegistration()
    testUserLogin()
    testGetDogBreedInfo()
    print("All tests passed.")