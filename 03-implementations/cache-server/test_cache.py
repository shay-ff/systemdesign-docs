#!/usr/bin/env python3
"""
Simple test script for cache server functionality
"""

import requests
import json
import time
import sys


def test_cache_server(base_url="http://localhost:8000"):
    """Test basic cache server functionality"""
    
    print("Testing Cache Server Functionality")
    print("=" * 40)
    
    # Test health check
    print("1. Testing health check...")
    try:
        response = requests.get(f"{base_url}/health")
        if response.status_code == 200:
            print("✓ Health check passed")
        else:
            print("✗ Health check failed")
            return False
    except requests.RequestException as e:
        print(f"✗ Cannot connect to server: {e}")
        return False
    
    # Test root endpoint
    print("\n2. Testing root endpoint...")
    response = requests.get(f"{base_url}/")
    if response.status_code == 200:
        info = response.json()
        print(f"✓ Server info: {info['service']} v{info['version']}")
    else:
        print("✗ Root endpoint failed")
    
    # Test SET operation
    print("\n3. Testing SET operation...")
    test_data = {
        "value": {"name": "John Doe", "age": 30, "city": "New York"},
        "ttl": 300
    }
    
    response = requests.put(
        f"{base_url}/cache/user:123",
        json=test_data
    )
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ SET successful: {result['key']}")
    else:
        print("✗ SET operation failed")
        return False
    
    # Test GET operation
    print("\n4. Testing GET operation...")
    response = requests.get(f"{base_url}/cache/user:123")
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ GET successful: {result['value']}")
        
        # Verify data integrity
        if result['value'] == test_data['value']:
            print("✓ Data integrity verified")
        else:
            print("✗ Data integrity check failed")
    else:
        print("✗ GET operation failed")
        return False
    
    # Test GET non-existent key
    print("\n5. Testing GET non-existent key...")
    response = requests.get(f"{base_url}/cache/nonexistent")
    
    if response.status_code == 404:
        print("✓ Non-existent key handled correctly")
    else:
        print("✗ Non-existent key handling failed")
    
    # Test statistics
    print("\n6. Testing statistics...")
    response = requests.get(f"{base_url}/stats")
    
    if response.status_code == 200:
        stats = response.json()
        print(f"✓ Stats retrieved: {stats['total_keys']} keys, {stats['hits']} hits")
    else:
        print("✗ Statistics retrieval failed")
    
    # Test DELETE operation
    print("\n7. Testing DELETE operation...")
    response = requests.delete(f"{base_url}/cache/user:123")
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ DELETE successful: {result['key']}")
    else:
        print("✗ DELETE operation failed")
    
    # Verify deletion
    print("\n8. Verifying deletion...")
    response = requests.get(f"{base_url}/cache/user:123")
    
    if response.status_code == 404:
        print("✓ Key successfully deleted")
    else:
        print("✗ Key deletion verification failed")
    
    # Test TTL functionality
    print("\n9. Testing TTL functionality...")
    short_ttl_data = {
        "value": "This will expire soon",
        "ttl": 2  # 2 seconds
    }
    
    response = requests.put(
        f"{base_url}/cache/ttl_test",
        json=short_ttl_data
    )
    
    if response.status_code == 200:
        print("✓ TTL key set")
        
        # Wait for expiration
        print("  Waiting 3 seconds for expiration...")
        time.sleep(3)
        
        # Try to retrieve expired key
        response = requests.get(f"{base_url}/cache/ttl_test")
        if response.status_code == 404:
            print("✓ TTL expiration working correctly")
        else:
            print("✗ TTL expiration not working")
    
    # Test clear cache
    print("\n10. Testing cache clear...")
    
    # Add some test data
    for i in range(3):
        requests.put(
            f"{base_url}/cache/test:{i}",
            json={"value": f"test_value_{i}"}
        )
    
    # Clear cache
    response = requests.delete(f"{base_url}/cache")
    
    if response.status_code == 200:
        print("✓ Cache cleared successfully")
        
        # Verify cache is empty
        stats_response = requests.get(f"{base_url}/stats")
        if stats_response.status_code == 200:
            stats = stats_response.json()
            if stats['total_keys'] == 0:
                print("✓ Cache clear verification successful")
            else:
                print(f"✗ Cache still has {stats['total_keys']} keys")
    
    print("\n" + "=" * 40)
    print("All tests completed successfully! ✓")
    return True


if __name__ == "__main__":
    # Allow custom URL via command line
    url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000"
    
    success = test_cache_server(url)
    sys.exit(0 if success else 1)