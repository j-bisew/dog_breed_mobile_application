import endpointsHelperFunctions
import http.client
import json

photoPath = 'mainPhoto.jpg'
url = '127.0.0.1:8000'

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
        'Content-Length': str(len(body))
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
    testGetDogBreedInfo()
    print("Test for getDogBreedInfo passed.")