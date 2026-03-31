import requests
import json
import random
import string
import os

# Configuration
BASE_URL = 'http://localhost:8000'

def generate_random_string(length=10):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

def create_dummy_jpeg(filename='test_image.jpg'):
    # A minimal valid JPEG
    # Start of Image, App0, JFIF, Quantization Tables, Start of Frame, Huffman Tables, Start of Scan, End of Image
    # Just a solid 1x1 pixel image to satisfy image parsers
    minimal_jpeg = (
        b'\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00'
        b'\xff\xdb\x00C\x00\x03\x02\x02\x02\x02\x02\x03\x02\x02\x02\x03'
        b'\x03\x03\x03\x04\x06\x04\x04\x04\x04\x04\x08\x06\x06\x05\x06\t'
        b'\x08\n\n\t\x08\t\t\n\x0c\x0f\x0c\n\x0b\x0e\x0b\t\t\r\x11\r\x0e'
        b'\x0f\x10\x10\x11\x10\n\x0c\x12\x13\x12\x10\x13\x0f\x10\x10\x10'
        b'\xff\xc0\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xff\xc4'
        b'\x00\x1f\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00'
        b'\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b'
        b'\xff\xda\x00\x08\x01\x01\x00\x00\x00?\x00\x05\x7f\xff\xd9'
    )
    with open(filename, 'wb') as f:
        f.write(minimal_jpeg)
    return filename

def run_test():
    print("Starting Optimistic Path Test for Backend Endpoints...")
    print(f"Target URL: {BASE_URL}")

    # Generate User Credentials
    username = generate_random_string()
    password = 'TestPassword123!'
    name = f"User{username}"
    email = f"{username}@example.com"
    
    print(f"Generated user: {username} / {email}")

    # 1. Register
    print("\n[1] Registering User...")
    register_payload = {
        'registrationData': {
            'name': name,
            'username': username,
            'email': email,
            'password': password
        }
    }
    
    try:
        resp = requests.post(f"{BASE_URL}/registerUser", json=register_payload)
        print(f"Status Code: {resp.status_code}")
        
        # Try to print response text/json
        try:
            print(f"Response: {resp.json()}")
        except:
            print(f"Response: {resp.text}")
        
        if resp.status_code in [200, 201]:
             print("SUCCESS: User registered.")
        else:
             print("FAILURE: User registration failed.")
             return
            
    except Exception as e:
        print(f"EXCEPTION during registration: {e}")
        return

    # 2. Login
    print("\n[2] Logging in...")
    login_payload = {
        'loginData': {
            'username': username,
            'password': password
        }
    }
    
    token = None
    try:
        resp = requests.post(f"{BASE_URL}/loginUser", json=login_payload)
        print(f"Status Code: {resp.status_code}")
        
        if resp.status_code == 202:
            try:
                data = resp.json()
                print(f"Response: {data}")
                token = data.get('token')
                if token:
                    print("SUCCESS: Logged in and received token.")
                else:
                    print("FAILURE: Token missing in response.")
                    return
            except Exception as e:
                print(f"FAILURE: Could not parse JSON response: {e}")
                print(f"Raw Response: {resp.text}")
                return
        else:
            print(f"FAILURE: Login failed. Status: {resp.status_code}")
            return

    except Exception as e:
        print(f"EXCEPTION during login: {e}")
        return
        
    # 3. Send Photo (getDogBreedInfo)
    print("\n[3] Sending Photo for Dog Breed Info...")
    
    # Use a real dog photo if available
    real_images_dir = os.path.join('backend', 'recognition', 'dog_photos')
    # Try to find a jpg in that directory
    image_file = None
    if os.path.exists(real_images_dir):
        files = [f for f in os.listdir(real_images_dir) if f.lower().endswith('.jpg') and not 'cropped' in f]
        if files:
            image_file = os.path.join(real_images_dir, files[0])
            print(f"Using real image: {image_file}")
    
    using_temp_file = False
    if not image_file or not os.path.exists(image_file):
        image_file = create_dummy_jpeg()
        using_temp_file = True
        print("Using dummy image (expect failure to detect dog).")
    
    try:
        headers = {
            'Authorization': f'Bearer {token}'
        }
        
        # 'photo' is the field name expected by endpointsHelperFunctions.py
        with open(image_file, 'rb') as f:
            files = {
                'photo': ('test_image.jpg', f, 'image/jpeg')
            }
            
            resp = requests.post(f"{BASE_URL}/getDogBreedInfo", headers=headers, files=files)
        
        print(f"Status Code: {resp.status_code}")
        
        content_type = resp.headers.get('Content-Type', '')
        print(f"Content-Type: {content_type}")

        if 'application/json' in content_type:
            try:
                print(f"Response: {json.dumps(resp.json(), indent=2)}")
            except:
                print(f"Response Text: {resp.text[:500]}...") # Truncate if needed
        elif 'image' in content_type:
            print(f"Response is an image of size {len(resp.content)} bytes.")
        else:
            print(f"Response content (first 500 bytes): {resp.content[:500]}")
            
        if resp.status_code == 200:
            print("SUCCESS: Photo processed.")
        elif resp.status_code == 515 and using_temp_file:
             print("SUCCESS: System correctly rejected dummy image (No dog detected).")
        else:
            print("FAILURE: Photo processing failed.")
            
    except Exception as e:
        print(f"EXCEPTION during photo upload: {e}")
    finally:
        if using_temp_file and os.path.exists(image_file):
            try:
                os.remove(image_file)
            except:
                pass

if __name__ == "__main__":
    run_test()
