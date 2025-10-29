#!/usr/bin/env python3
"""
Python client library for Distributed Lock Service
"""

import json
import time
import uuid
import threading
from typing import Optional, Dict, Any
from contextlib import contextmanager
import requests

class LockTimeoutError(Exception):
    """Raised when lock acquisition times out"""
    pass

class LockReleaseError(Exception):
    """Raised when lock release fails"""
    pass

class DistributedLock:
    """Represents an acquired distributed lock"""
    
    def __init__(self, client: 'DistributedLockClient', lock_name: str, 
                 lock_id: str, renewal_token: Optional[str] = None):
        self.client = client
        self.lock_name = lock_name
        self.lock_id = lock_id
        self.renewal_token = renewal_token
        self._released = False
        self._renewal_thread = None
        
        # Start auto-renewal if token provided
        if renewal_token:
            self._start_auto_renewal()
    
    def _start_auto_renewal(self):
        """Start automatic lock renewal"""
        def renewal_worker():
            while not self._released:
                try:
                    time.sleep(10)  # Renew every 10 seconds
                    if not self._released:
                        result = self.client.renew_lock(self.lock_name, self.lock_id)
                        if not result.get('success'):
                            print(f"Auto-renewal failed for lock {self.lock_name}: {result.get('error')}")
                            break
                except Exception as e:
                    print(f"Auto-renewal error for lock {self.lock_name}: {e}")
                    break
        
        self._renewal_thread = threading.Thread(target=renewal_worker, daemon=True)
        self._renewal_thread.start()
    
    def renew(self, extend_by: int = 30) -> bool:
        """Manually renew the lock"""
        if self._released:
            return False
        
        result = self.client.renew_lock(self.lock_name, self.lock_id, extend_by)
        return result.get('success', False)
    
    def release(self):
        """Release the lock"""
        if self._released:
            return
        
        self._released = True
        
        try:
            result = self.client.release_lock(self.lock_name, self.lock_id)
            if not result.get('success'):
                raise LockReleaseError(f"Failed to release lock: {result.get('error')}")
        finally:
            # Stop renewal thread
            if self._renewal_thread and self._renewal_thread.is_alive():
                self._renewal_thread.join(timeout=1)
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()

class DistributedLockClient:
    """Client for the Distributed Lock Service"""
    
    def __init__(self, base_url: str = "http://localhost:5000", 
                 client_id: Optional[str] = None, timeout: int = 30):
        self.base_url = base_url.rstrip('/')
        self.client_id = client_id or f"client-{uuid.uuid4()}"
        self.default_timeout = timeout
        self.session = requests.Session()
    
    def acquire_lock(self, lock_name: str, timeout: int = None, 
                    wait_timeout: int = 0, auto_renew: bool = False,
                    metadata: Dict[str, Any] = None) -> DistributedLock:
        """Acquire a distributed lock"""
        timeout = timeout or self.default_timeout
        
        request_data = {
            'clientId': self.client_id,
            'timeout': timeout,
            'waitTimeout': wait_timeout,
            'autoRenew': auto_renew,
            'metadata': metadata or {}
        }
        
        response = self.session.post(
            f"{self.base_url}/locks/{lock_name}/acquire",
            json=request_data,
            timeout=wait_timeout + 10 if wait_timeout > 0 else 30
        )
        
        if response.status_code == 200:
            data = response.json()
            if data.get('acquired'):
                return DistributedLock(
                    self, lock_name, data['lockId'], data.get('renewalToken')
                )
            else:
                raise LockTimeoutError(f"Failed to acquire lock: {data.get('error')}")
        else:
            error_data = response.json() if response.headers.get('content-type', '').startswith('application/json') else {}
            raise LockTimeoutError(f"Lock acquisition failed: {error_data.get('error', response.text)}")
    
    def release_lock(self, lock_name: str, lock_id: str) -> Dict[str, Any]:
        """Release a distributed lock"""
        request_data = {
            'clientId': self.client_id,
            'lockId': lock_id
        }
        
        response = self.session.post(
            f"{self.base_url}/locks/{lock_name}/release",
            json=request_data,
            timeout=10
        )
        
        return response.json()
    
    def renew_lock(self, lock_name: str, lock_id: str, extend_by: int = 30) -> Dict[str, Any]:
        """Renew a distributed lock"""
        request_data = {
            'clientId': self.client_id,
            'lockId': lock_id,
            'extendBy': extend_by
        }
        
        response = self.session.post(
            f"{self.base_url}/locks/{lock_name}/renew",
            json=request_data,
            timeout=10
        )
        
        return response.json()
    
    def get_lock_status(self, lock_name: str) -> Dict[str, Any]:
        """Get status of a lock"""
        response = self.session.get(
            f"{self.base_url}/locks/{lock_name}/status",
            timeout=10
        )
        
        return response.json()
    
    def list_locks(self) -> Dict[str, Any]:
        """List all active locks"""
        response = self.session.get(
            f"{self.base_url}/locks",
            timeout=10
        )
        
        return response.json()
    
    @contextmanager
    def lock(self, lock_name: str, timeout: int = None, wait_timeout: int = 0,
             auto_renew: bool = False, metadata: Dict[str, Any] = None):
        """Context manager for acquiring and releasing locks"""
        distributed_lock = self.acquire_lock(
            lock_name, timeout, wait_timeout, auto_renew, metadata
        )
        try:
            yield distributed_lock
        finally:
            distributed_lock.release()

# Example usage
if __name__ == "__main__":
    client = DistributedLockClient("http://localhost:5000")
    
    # Example 1: Basic lock usage
    try:
        with client.lock("user:123", timeout=30) as lock:
            print(f"Lock acquired: {lock.lock_id}")
            time.sleep(5)
            print("Critical section completed")
    except LockTimeoutError as e:
        print(f"Failed to acquire lock: {e}")
    
    # Example 2: Lock with wait timeout
    try:
        with client.lock("resource:456", timeout=60, wait_timeout=10) as lock:
            print(f"Lock acquired after waiting: {lock.lock_id}")
            time.sleep(2)
    except LockTimeoutError as e:
        print(f"Lock acquisition timed out: {e}")
    
    # Example 3: Auto-renewing lock
    try:
        with client.lock("long-task", timeout=30, auto_renew=True) as lock:
            print(f"Auto-renewing lock acquired: {lock.lock_id}")
            time.sleep(45)  # Longer than initial timeout
            print("Long task completed")
    except LockTimeoutError as e:
        print(f"Failed to acquire auto-renewing lock: {e}")
    
    # Example 4: Manual lock management
    try:
        lock = client.acquire_lock("manual-lock", timeout=30)
        print(f"Manually acquired lock: {lock.lock_id}")
        
        # Do some work
        time.sleep(5)
        
        # Manually renew
        if lock.renew(extend_by=60):
            print("Lock renewed successfully")
        
        # Release manually
        lock.release()
        print("Lock released manually")
        
    except LockTimeoutError as e:
        print(f"Manual lock failed: {e}")