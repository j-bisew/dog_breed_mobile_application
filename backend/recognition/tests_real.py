import unittest
from unittest.mock import MagicMock
import json
import os
import sys

# Ensure we can import the module in the current directory
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# Import the actual module - this will load the models!
# Ensure dependencies (tensorflow, ultralytics, etc.) are installed in the environment.
import recognitionHelperFunctions

class TestRecognitionReal(unittest.TestCase):
    def setUp(self):
        self.mock_handler = MagicMock()
        self.mock_handler.headers = {}
        self.mock_handler.wfile = MagicMock()
        
        # Find a real dog photo to use for testing
        self.test_image_path = None
        dog_photos_dir = 'dog_photos'
        if os.path.exists(dog_photos_dir):
            # Look for a jpg that isn't a crop
            files = [f for f in os.listdir(dog_photos_dir) if f.lower().endswith('.jpg') and 'cropped' not in f]
            if files:
                self.test_image_path = os.path.join(dog_photos_dir, files[0])
        
        if not self.test_image_path:
            # If no existing photos, maybe check if we are in root and adjust path
            if os.path.exists(os.path.join('backend', 'recognition', 'dog_photos')):
                dog_photos_dir = os.path.join('backend', 'recognition', 'dog_photos')
                files = [f for f in os.listdir(dog_photos_dir) if f.lower().endswith('.jpg') and 'cropped' not in f]
                if files:
                    self.test_image_path = os.path.join(dog_photos_dir, files[0])

        if not self.test_image_path:
             print("WARNING: No dog photos found for testing. Skipping test.")

    def test_submitDogPhoto_real_inference(self):
        if not self.test_image_path:
            self.skipTest("No dog photos found.")

        print(f"Testing with image: {self.test_image_path}")
        
        # Read the real image bytes
        with open(self.test_image_path, 'rb') as f:
            image_data = f.read()
            
        boundary = '----WebKitFormBoundaryTestBoundary'
        
        # Construct multipart body manually
        body = (
            f'--{boundary}\r\n'
            f'Content-Disposition: form-data; name="photo"; filename="test_dog.jpg"\r\n'
            f'Content-Type: image/jpeg\r\n\r\n'
        ).encode('utf-8') + image_data + (
            f'\r\n--{boundary}--\r\n'
        ).encode('utf-8')
        
        self.mock_handler.headers = {
            'Content-Type': f'multipart/form-data; boundary={boundary}',
            'Content-Length': str(len(body))
        }
        
        # Correctly mock rfile.read (it might be called with size argument)
        self.mock_handler.rfile.read.side_effect = lambda size=None: body
        
        # Run the actual handler which calls actual ML functions
        recognitionHelperFunctions.submitDogPhotoPathHandler(self.mock_handler)
        
        # Verify response code 
        # We expect send_response to be called
        self.assertTrue(self.mock_handler.send_response.called)
        args, _ = self.mock_handler.send_response.call_args
        status_code = args[0]
        
        print(f"Handler returned status code: {status_code}")
        
        if status_code == 200:
            # Check JSON response
            write_args, _ = self.mock_handler.wfile.write.call_args
            response_body = write_args[0]
            data = json.loads(response_body.decode('utf-8'))
            print("Response Data:", data)
            self.assertIn('breedName', data)
            self.assertIn('confidence', data)
            self.assertIsInstance(data['confidence'], (int, float))
            
        elif status_code == 515:
            # 515 means no dog detected. 
            print("Model response: No dog detected in the photo.")
            # Check response body confirms this
            write_args, _ = self.mock_handler.wfile.write.call_args
            print("Response text:", write_args[0])
            
        else:
            self.fail(f"Handler failed with status code {status_code}")

if __name__ == '__main__':
    unittest.main()
