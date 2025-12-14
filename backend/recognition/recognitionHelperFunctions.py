import json
import random
import os
from PIL import Image, ImageOps
from ultralytics import YOLO
import tensorflow
import numpy as np
import cv2

FOLDER_PATH = 'dog_photos/'

def load_model_and_classes(model_name):
    try:
        model_path = os.path.join('models', f"{model_name}.keras")
        
        model = tensorflow.keras.models.load_model(model_path)
        
        return model
    
    except Exception as e:
        print(f"Error loading model and classes: {e}")
        return None

def load_yolo():
    return YOLO("yolov8n.pt")

def preprocess_image(image):    
    image = image.resize((224, 224))
    
    img_array = np.array(image)
    
    if len(img_array.shape) == 2:  # Grayscale
        img_array = np.stack([img_array] * 3, axis=-1)
    elif img_array.shape[2] == 4:  # RGBA
        img_array = img_array[:, :, :3]
    
    img_array = img_array.astype(np.float32) / 255.0
    
    img_array = np.expand_dims(img_array, axis=0)
    
    return img_array


DOG_CLASS_ID = 16 

def detect_dog_yolo(pil_image, conf_threshold=0.5):
    yolo = load_yolo()

    img = np.array(pil_image)
    img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)

    results = yolo(img)

    print("YOLO detection results:", results)

    dogs = []
    for r in results:
        for box in r.boxes:
            cls = int(box.cls[0])
            conf = float(box.conf[0])
            print(f"Detected class {cls} with confidence {conf}")

            if cls >= 14 and cls <= 23 and conf > conf_threshold:
                dogs.append((box, conf))

    if not dogs:
        return None, None

    box, conf = max(
        dogs,
        key=lambda b: (b[0].xyxy[0][2] - b[0].xyxy[0][0]) *
                      (b[0].xyxy[0][3] - b[0].xyxy[0][1])
    )

    return box, conf

def crop_dog(pil_image, box):
    img = np.array(pil_image)
    x1, y1, x2, y2 = map(int, box.xyxy[0])
    return Image.fromarray(img[y1:y2, x1:x2])

def predict_breed(model, image, class_names, top_k=3):
    try:
        processed_image = preprocess_image(image)
        
        predictions = model.predict(processed_image, verbose=0)[0]
        
        top_indices = np.argsort(predictions)[-top_k:][::-1]
        
        results = []
        for idx in top_indices:
            breed = class_names[idx]
            confidence = float(predictions[idx]) * 100
            results.append((breed, confidence))
        
        return results
    
    except Exception as e:
        print(f"Error in predict_breed: {e}")
        return []

def submitDogPhotoPathHandler(self):
    # Accept multipart/form-data with field name 'photo'
    contentType = self.headers.get('Content-Type')
    if not contentType or not contentType.startswith('multipart/form-data'):
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Unsupported Content-Type')
        return

    try:
        boundary = contentType.split('boundary=')[1]
    except Exception:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Boundary not found')
        return

    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    print(f"Received multipart/form-data of length: {len(post_data)}")

    parts = post_data.split(b'--' + boundary.encode())
    photoData = None
    for part in parts:
        if b'Content-Disposition' in part and b'name="photo"' in part:
            idx = part.find(b'\r\n\r\n')
            if idx != -1:
                photoDataStart = idx + 4
                photoDataEnd = part.rfind(b'\r\n')
                photoData = part[photoDataStart:photoDataEnd]
                break

    if photoData is None:
        self.send_response(400)
        self.end_headers()
        self.wfile.write(b'Photo data not found')
        return

    # Save photo to a local folder
    randomStringName = ''.join(random.choices('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', k=16))
    if not os.path.exists(FOLDER_PATH):
        os.makedirs(FOLDER_PATH)
    photoPath = os.path.join(FOLDER_PATH, f"{randomStringName}.jpg")
    with open(photoPath, 'wb') as f:
        f.write(photoData)

    
    image = Image.open(photoPath).convert('RGB')

    box, conf = detect_dog_yolo(image)
    if box is None or conf < 0.5:
        print("No dog detected in the photo")
        self.send_response(500)
        self.end_headers()
        self.wfile.write(b'No dog detected in the photo')
        return
    
    dog_image = crop_dog(image, box)

    # Save cropped dog image for debugging
    dog_image.save(os.path.join(FOLDER_PATH, f"{randomStringName}_cropped.jpg"))

    model = MODEL
    class_names = CLASS_NAMES
    predictions = predict_breed(model, dog_image, class_names, top_k=1)
    if not predictions:
        self.send_response(500)
        self.end_headers()
        self.wfile.write(b'Error predicting breed')
        return
    breedName, confidence = predictions[0]
    breedName = breedName.lower()




    response_data = {
        'breedName': breedName,
        'assurance': 90.0
    }
    print(f"Predicted breed: {breedName} with confidence {confidence}%")

    self.send_response(200)
    self.end_headers()
    self.wfile.write(json.dumps(response_data).encode('utf-8'))

MODEL = load_model_and_classes('mobilenetv2_finetuned')

def loadClassNames():
    try:
        with open('class_names.txt', 'r') as f:
            class_names = [line.strip() for line in f.readlines()]
        return class_names
    except Exception as e:
        print(f"Error loading class names: {e}")
        return []

CLASS_NAMES = loadClassNames()
YOLO_MODEL = load_yolo()
