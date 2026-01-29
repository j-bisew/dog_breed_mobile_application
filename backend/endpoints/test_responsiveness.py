import threading
import time
import requests
import os
import uuid

# Configuration
BASE_URL = "http://localhost:8000"
PHOTO_URL = f"{BASE_URL}/getDogBreedInfo"
LOGIN_URL = f"{BASE_URL}/loginUser"

# Setup credentials
unique_id = uuid.uuid4().hex[:8]
USERNAME = f"user{unique_id}"        # Removed underscore
PASSWORD = "Password123!" 
EMAIL = f"user{unique_id}@example.com"
NAME = "ResponsiveUser"

def setup_user():
    print("Registering user...")
    resp = requests.post(f"{BASE_URL}/registerUser", json={
        "registrationData": {
            "name": NAME, "username": USERNAME, "email": EMAIL, "password": PASSWORD
        }
    })
    if resp.status_code not in [200, 201]:
         print(f"Registration warning: {resp.text}")

def login_task():
    """
    Tries to log in. This should be fast.
    """
    start = time.time()
    try:
        resp = requests.post(LOGIN_URL, json={
            "loginData": {"username": USERNAME, "password": PASSWORD}
        }, timeout=5)
        duration = time.time() - start
        print(f"[Login Request] Status: {resp.status_code}, Time: {duration:.4f}s")
        return duration
    except Exception as e:
        print(f"[Login Request] Failed: {e}")
        return 999

def photo_task(token):
    """
    Submits a photo. This should be slow (CPU heavy).
    """
    print("[Photo Request] Starting upload...")
    # Find image
    image_path = "backend/endpoints/aaaa.jpg" # Created by previous tests
    if not os.path.exists(image_path):
        # Fallback search
        for root, dirs, files in os.walk("."):
            for file in files:
                if file.endswith(".jpg"):
                    image_path = os.path.join(root, file)
                    break
    
    with open(image_path, "rb") as f:
        requests.post(PHOTO_URL, 
            files={'photo': ('dog.jpg', f, 'image/jpeg')}, 
            headers={'Authorization': f'Bearer {token}'}
        )
    print("[Photo Request] Finished.")

def run_responsiveness_test():
    setup_user()
    
    # Get token
    resp = requests.post(LOGIN_URL, json={"loginData": {"username": USERNAME, "password": PASSWORD}})
    
    if resp.status_code != 200:
        print(f"Login failed: {resp.text}")
        return

    try:
        token = resp.json().get('token')
    except Exception:
        print(f"Failed to parse login response: {resp.text}")
        return

    print("\n--- Responsiveness Test ---")
    print("Scenario: A heavy photo upload starts. While it is processing, a second user tries to login.")
    
    # Start heavy background task
    t_photo = threading.Thread(target=photo_task, args=(token,))
    t_photo.start()
    
    # Give it a moment to hit the server and start crunching numbers
    time.sleep(0.5) 
    
    # Attempt light task
    print("Sending Login request now...")
    login_time = login_task()
    
    t_photo.join()
    
    print("\n--- Analysis ---")
    if login_time < 1.0:
        print("PASS: Login was fast (<1s) even while server was processing a photo.")
        print("This confirms multithreading keeps the server responsive for other users.")
    else:
        print("FAIL: Login was blocked by the photo upload.")

if __name__ == "__main__":
    run_responsiveness_test()
