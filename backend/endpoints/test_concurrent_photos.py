import threading
import time
import os
import requests
import statistics
import sys
import uuid
from concurrent.futures import ThreadPoolExecutor

# Configuration
BASE_URL = "http://localhost:8000"
TARGET_URL = f"{BASE_URL}/getDogBreedInfo"
CONCURRENT_REQUESTS = 250
TIMEOUT = 120

def register_and_login():
    """
    Registers a fresh user and logs in to get an auth token.
    """
    unique_id = uuid.uuid4().hex[:8]  # Alphanumeric only
    username = f"loadtest{unique_id}" # Alphanumeric only
    password = "Password123!"         # Needs special char
    email = f"loadtest{unique_id}@example.com"
    name = "LoadTestUser"             # Alphanumeric only (no spaces)

    print(f"Registering user: {username}...")
    
    # Register
    reg_data = {
        "registrationData": {
            "name": name,
            "username": username,
            "email": email,
            "password": password
        }
    }
    
    try:
        reg_resp = requests.post(f"{BASE_URL}/registerUser", json=reg_data)
        if reg_resp.status_code not in [200, 201, 202]:
            print(f"Registration failed: {reg_resp.text}")
            return None
    except Exception as e:
        print(f"Registration exception: {e}")
        return None
        
    print("Logging in...")
    # Login
    login_data = {
        "loginData": {
            "username": username,
            "password": password
        }
    }
    
    try:
        login_resp = requests.post(f"{BASE_URL}/loginUser", json=login_data)
        if login_resp.status_code in [200, 202]:
            data = login_resp.json()
            token = data.get('token')
            print("Login successful, token received.")
            return token
        else:
            print(f"Login failed: {login_resp.text}")
            return None
    except Exception as e:
        print(f"Login exception: {e}")
        return None

def find_dog_photo():
    # Helper to locate a test image
    search_paths = [
        'backend/recognition/dog_photos',
        '../recognition/dog_photos',
        'dog_photos'
    ]
    
    # Try current directory first
    current_dir = os.getcwd()
    print(f"Searching for photos starting from: {current_dir}")
    
    # Recursive search if simple paths fail
    for root, dirs, files in os.walk(current_dir):
        for file in files:
            if file.lower().endswith('.jpg') and 'cropped' not in file:
                 return os.path.join(root, file)

    return None

def submit_photo(image_path, request_id, token):
    """
    Submits a photo and returns (success, duration, details)
    """
    start_time = time.time()
    headers = {'Authorization': f'Bearer {token}'}
    
    try:
        with open(image_path, 'rb') as img:
            files = {'photo': ('test_dog.jpg', img, 'image/jpeg')}
            response = requests.post(TARGET_URL, files=files, headers=headers, timeout=TIMEOUT)
            
        duration = time.time() - start_time
        
        if response.status_code == 200:
            content_type = response.headers.get('Content-Type', '')
            details = f"Status 200, Type: {content_type}"
            return True, duration, details
        else:
            return False, duration, f"Status {response.status_code}: {response.text[:100]}"
            
    except Exception as e:
        return False, time.time() - start_time, str(e)

def run_photo_concurrency_test():
    token = register_and_login()
    if not token:
        print("ERROR: Authentication failed. Aborting test.")
        return

    image_path = find_dog_photo()
    if not image_path:
        print("ERROR: No suitable dog photo found for testing.")
        return

    print(f"Using test image: {image_path}")
    file_size = os.path.getsize(image_path) / 1024
    print(f"Image size: {file_size:.2f} KB")

    print(f"\nStarting photo submission test with {CONCURRENT_REQUESTS} threads...")
    
    results = []
    start_total = time.time()

    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as executor:
        # Submit all tasks
        future_to_id = {
            executor.submit(submit_photo, image_path, i, token): i 
            for i in range(CONCURRENT_REQUESTS)
        }
        
        # Gather results
        for future in future_to_id:
            results.append(future.result())

    end_total = time.time()
    total_duration = end_total - start_total
    
    # Analysis
    successful = [r for r in results if r[0]]
    failed = [r for r in results if not r[0]]
    durations = [r[1] for r in results]
    
    print("\n--- Parallel Photo Submission Results ---")
    print(f"Total Wall Clock Time: {total_duration:.4f}s")
    print(f"Successful: {len(successful)}/{CONCURRENT_REQUESTS}")
    print(f"Failed: {len(failed)}")
    
    if durations:
        avg = statistics.mean(durations)
        print(f"Average Latency: {avg:.4f}s")
        print(f"Max Latency: {max(durations):.4f}s")
        print(f"Min Latency: {min(durations):.4f}s")
        
        throughput = len(successful) / total_duration
        print(f"Throughput: {throughput:.2f} requests/sec")
        
        # Speedup estimation
        estimated_seq = sum(durations)
        speedup = estimated_seq / total_duration
        # print(f"Sum of Latencies (Est. Sequential): {estimated_seq:.4f}s")
        # print(f"Concurrency Speedup Factor: {speedup:.2f}x")

    if failed:
        print("\nFailures:")
        for r in failed[:5]:
            print(f"- {r[2]}")

    # --- SEQUENTIAL TEST FOR VALIDATION ---
    SEQUENTIAL_COUNT = 5
    print(f"\n--- Running Sequential Validation (N={SEQUENTIAL_COUNT}) ---")
    print("This actually runs requests one-by-one to measure baseline performance.")
    
    seq_start = time.time()
    seq_durations = []
    
    for i in range(SEQUENTIAL_COUNT):
        print(f"Sequential Request {i+1}/{SEQUENTIAL_COUNT}...", end='', flush=True)
        success, duration, details = submit_photo(image_path, i, token)
        seq_durations.append(duration)
        print(f" Done ({duration:.2f}s) [{'OK' if success else 'FAIL'}]")
        
    seq_total = time.time() - seq_start
    seq_avg = statistics.mean(seq_durations)
    
    print(f"\nSequential Total Time ({SEQUENTIAL_COUNT} reqs): {seq_total:.4f}s")
    print(f"Sequential Average Latency: {seq_avg:.4f}s")
    
    # Compare
    # Speedup = (Sequential Avg / Concurrent Total Duration per Request) roughly
    # Or better: Speedup = Throughput Concurrent / Throughput Sequential
    
    seq_throughput = SEQUENTIAL_COUNT / seq_total
    print(f"Sequential Throughput: {seq_throughput:.2f} requests/sec")
    
    real_speedup = throughput / seq_throughput
    print(f"\n>>> FINAL VERDICT <<<")
    print(f"Concurrent Throughput: {throughput:.2f} req/s")
    print(f"Sequential Throughput: {seq_throughput:.2f} req/s")
    print(f"REAL Speedup Factor: {real_speedup:.2f}x")

if __name__ == "__main__":
    run_photo_concurrency_test()
