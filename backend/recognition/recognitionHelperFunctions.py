import json
import random
import os

FOLDER_PATH = 'dog_photos/'

def submitDogPhotoPathHandler(self):
    # {photoData: "<base64-encoded-photo>"}
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received photo data: {post_data}")
    photoData = post_data.decode('latin1')
    photoData = json.loads(photoData).get('photoData', '')

    # TODO : Identify dog breed from photoData

    # Save photo to a local folder

    randomStringName = ''.join(random.choices('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', k=16))
    if not os.path.exists(FOLDER_PATH):
        os.makedirs(FOLDER_PATH)
    photoPath = os.path.join(FOLDER_PATH, f"{randomStringName}.jpg")
    with open(photoPath, 'wb') as f:
        f.write(photoData.encode('latin1'))

    breedName = "poodle"  # Placeholder for identified breed name

    response_data = {
        'breedName': breedName,
        'assurance': 90.0  # Placeholder for assurance level
    }
    
    self.send_response(200)
    self.end_headers()
    self.wfile.write(json.dumps(response_data).encode('utf-8'))
