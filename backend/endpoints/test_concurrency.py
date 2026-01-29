import threading
import http.client
import time
import json
import statistics

# Configuration
TARGET_HOST = "localhost" # or 127.0.0.1
TARGET_PORT = 8000
CONCURRENT_REQUESTS = 50

def make_request(request_id):
    """
    Makes a request to the server and returns the status code and duration.
    """
    start_time = time.time()
    try:
        conn = http.client.HTTPConnection(TARGET_HOST, TARGET_PORT, timeout=10)
        # We use the root endpoint which should just return 200 OK
        conn.request("GET", "/")
        response = conn.getresponse()
        # Read response to ensure request completes
        response.read()
        conn.close()
        duration = time.time() - start_time
        return response.status, duration, None
    except Exception as e:
        return None, time.time() - start_time, str(e)

def run_concurrent_test():
    print(f"Starting concurrency test with {CONCURRENT_REQUESTS} threads against {TARGET_HOST}:{TARGET_PORT}")
    
    threads = []
    results = [None] * CONCURRENT_REQUESTS
    
    def thread_task(index):
        results[index] = make_request(index)

    start_total = time.time()

    # Create and start threads
    for i in range(CONCURRENT_REQUESTS):
        t = threading.Thread(target=thread_task, args=(i,))
        threads.append(t)
        t.start()

    # Wait for all threads to complete
    for t in threads:
        t.join()
        
    end_total = time.time()
    total_duration = end_total - start_total

    # Analyze results
    success_count = 0
    errors = []
    durations = []
    
    for status, duration, error in results:
        if error:
            errors.append(error)
        else:
            durations.append(duration)
            if status == 200:
                success_count += 1
            else:
                errors.append(f"Status {status}")

    print("\n--- Test Results ---")
    print(f"Total Time: {total_duration:.4f}s")
    print(f"Successful Requests: {success_count}/{CONCURRENT_REQUESTS}")
    print(f"Failed Requests: {len(errors)}")
    
    if durations:
        avg_latency = statistics.mean(durations)
        print(f"Average Request Latency: {avg_latency:.4f}s")
        print(f"Max Request Latency: {max(durations):.4f}s")
        print(f"Min Request Latency: {min(durations):.4f}s")
        
        estimated_sequential_time = avg_latency * CONCURRENT_REQUESTS
        speedup = estimated_sequential_time / total_duration
        print(f"Estimated Sequential Time: {estimated_sequential_time:.4f}s")
        print(f"Concurrency Speedup Factor: {speedup:.2f}x")
        
        if speedup > 2:
            print("VERIFIED: Significant parallel processing detected.")
        else:
            print("WARNING: Low speedup. Concurrency might not be effective or overhead is high.")

    
    if errors:
        print("\nErrors encountered:")
        for e in errors[:5]: # Show first 5 errors
            print(f"- {e}")
        if len(errors) > 5:
            print(f"... and {len(errors)-5} more.")

    # Success criteria: All requests successful and reasonably fast
    # With threading, 50 requests shouldn't take 50x latency.
    if success_count == CONCURRENT_REQUESTS:
        print("\nSUCCESS: Server handled concurrent requests.")
    else:
        print("\nFAILURE: Some requests failed.")

if __name__ == "__main__":
    run_concurrent_test()
