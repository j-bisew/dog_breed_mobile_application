import unittest
from unittest.mock import MagicMock, patch
import json
import sys
import os

# Add parent directory to path to import modules
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# Mock external ML libraries to avoid ImportError if not installed
sys.modules['ultralytics'] = MagicMock()
sys.modules['tensorflow'] = MagicMock()
sys.modules['tensorflow.keras'] = MagicMock()
sys.modules['tensorflow.keras.models'] = MagicMock()
# We might need to mock numpy slightly better if used at module level, but here it's likely fine
sys.modules['numpy'] = MagicMock()
sys.modules['cv2'] = MagicMock()
# PIL is imported as "from PIL import Image, ImageOps"
mock_pil = MagicMock()
sys.modules['PIL'] = mock_pil


# Patch module level loading functions BEFORE import
with patch('recognitionHelperFunctions.load_model_and_classes') as mock_load_model, \
     patch('recognitionHelperFunctions.load_yolo') as mock_load_yolo, \
     patch('recognitionHelperFunctions.loadClassNames') as mock_load_names:
    
    mock_load_model.return_value = MagicMock()
    mock_load_yolo.return_value = MagicMock()
    mock_load_names.return_value = ['poodle', 'bulldog']
    
    import recognitionHelperFunctions

class TestRecognitionFunctions(unittest.TestCase):

    def setUp(self):
        self.mock_handler = MagicMock()
        self.mock_handler.headers = {}
        self.mock_handler.wfile = MagicMock()

    @patch('recognitionHelperFunctions.detect_dog_yolo')
    @patch('recognitionHelperFunctions.crop_dog')
    @patch('recognitionHelperFunctions.predict_breed')
    @patch('os.makedirs') # Prevent folder creation
    @patch('builtins.open', new_callable=unittest.mock.mock_open) # Prevent file writing
    @patch('PIL.Image.open')
    @patch('os.path.exists')
    def test_submitDogPhotoPathHandler_success(self, mock_exists, mock_img_open, mock_file, mock_makedirs, mock_predict, mock_crop, mock_detect):
        # Setup
        boundary = 'boundary'
        # Construct multipart body
        content = (
            b'--boundary\r\n'
            b'Content-Disposition: form-data; name="photo"; filename="dog.jpg"\r\n\r\n'
            b'fakeimagedata\r\n'
            b'--boundary--'
        )
        self.mock_handler.headers = {
            'Content-Type': f'multipart/form-data; boundary={boundary}',
            'Content-Length': str(len(content))
        }
        self.mock_handler.rfile.read.return_value = content
        
        mock_exists.return_value = True

        # Mock Object detection
        mock_box = MagicMock()
        mock_conf = 0.95
        mock_detect.return_value = (mock_box, mock_conf)
        
        # Mock Crop
        mock_dog_img = MagicMock()
        mock_crop.return_value = mock_dog_img

        # Mock Prediction
        mock_predict.return_value = [('poodle', 98.5)]

        # Execute
        recognitionHelperFunctions.submitDogPhotoPathHandler(self.mock_handler)

        # Verify
        self.mock_handler.send_response.assert_called_with(200)
        
        # Check response
        args, _ = self.mock_handler.wfile.write.call_args
        # The first argument to write is bytes
        response_bytes = args[0]
        response = json.loads(response_bytes.decode('utf-8'))
        
        self.assertEqual(response['breedName'], 'poodle')
        self.assertEqual(response['confidence'], 98.5)

    def test_submitDogPhotoPathHandler_bad_content_type(self):
        self.mock_handler.headers = {'Content-Type': 'application/json'}
        recognitionHelperFunctions.submitDogPhotoPathHandler(self.mock_handler)
        self.mock_handler.send_response.assert_called_with(400)
        
    @patch('recognitionHelperFunctions.detect_dog_yolo')
    @patch('os.makedirs')
    @patch('builtins.open', new_callable=unittest.mock.mock_open)
    @patch('PIL.Image.open')
    def test_submitDogPhotoPathHandler_no_dog(self, mock_img_open, mock_file, mock_dirs, mock_detect):
        boundary = 'boundary'
        content = (
            b'--boundary\r\n'
            b'Content-Disposition: form-data; name="photo"; filename="dog.jpg"\r\n\r\n'
            b'fakeimagedata\r\n'
            b'--boundary--'
        )
        self.mock_handler.headers = {
            'Content-Type': f'multipart/form-data; boundary={boundary}',
            'Content-Length': str(len(content))
        }
        self.mock_handler.rfile.read.return_value = content
        
        # Mock no dog detected (None, None or conf < 0.5)
        mock_detect.return_value = (None, 0.0)
        
        recognitionHelperFunctions.submitDogPhotoPathHandler(self.mock_handler)
        
        self.mock_handler.send_response.assert_called_with(515)

if __name__ == '__main__':
    unittest.main()
