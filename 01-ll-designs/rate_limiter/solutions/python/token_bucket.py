#!/usr/bin/env python3
"""
Token Bucket Rate Limiter Implementation

The token bucket algorithm maintains a bucket with a fixed capacity of tokens.
Tokens are added to the bucket at a constant rate. When a request arrives,
it consumes one token. If no tokens are available, the request is rejected.

Time Complexity: O(1) per request
Space Complexity: O(1)
"""

import time
import threading
from typing import Optional


class TokenBucket:
    """
    Thread-safe token bucket rate limiter implementation.
    
    Args:
        capacity: Maximum number of tokens the bucket can hold
        refill_rate: Number of tokens added per second
    """
    
    def __init__(self, capacity: int, refill_rate: float):
        if capacity <= 0:
            raise ValueError("Capacity must be positive")
        if refill_rate <= 0:
            raise ValueError("Refill rate must be positive")
            
        self.capacity = capacity
        self.tokens = float(capacity)  # Start with full bucket
        self.refill_rate = refill_rate
        self.last_refill = time.time()
        self._lock = threading.Lock()
    
    def allow_request(self, tokens_requested: int = 1) -> bool:
        """
        Check if a request can be allowed based on available tokens.
        
        Args:
            tokens_requested: Number of tokens to consume (default: 1)
            
        Returns:
            True if request is allowed, False if rate limited
        """
        with self._lock:
            self._refill_tokens()
            
            if self.tokens >= tokens_requested:
                self.tokens -= tokens_requested
                return True
            return False
    
    def _refill_tokens(self) -> None:
        """Refill tokens based on elapsed time since last refill."""
        now = time.time()
        elapsed = now - self.last_refill
        self.last_refill = now
        
        # Add tokens based on elapsed time
        tokens_to_add = elapsed * self.refill_rate
        self.tokens = min(self.capacity, self.tokens + tokens_to_add)
    
    def get_available_tokens(self) -> float:
        """Get current number of available tokens."""
        with self._lock:
            self._refill_tokens()
            return self.tokens
    
    def wait_for_token(self, timeout: Optional[float] = None) -> bool:
        """
        Wait until a token becomes available.
        
        Args:
            timeout: Maximum time to wait in seconds (None for no timeout)
            
        Returns:
            True if token acquired, False if timeout occurred
        """
        start_time = time.time()
        
        while True:
            if self.allow_request():
                return True
                
            if timeout and (time.time() - start_time) >= timeout:
                return False
                
            # Sleep for a short time before checking again
            time.sleep(0.01)


class SlidingWindowRateLimiter:
    """
    Sliding window rate limiter implementation.
    
    Maintains a sliding window of requests and allows requests only if
    the count within the window doesn't exceed the limit.
    
    Time Complexity: O(log n) per request where n is window size
    Space Complexity: O(n) where n is number of requests in window
    """
    
    def __init__(self, max_requests: int, window_size: float):
        if max_requests <= 0:
            raise ValueError("Max requests must be positive")
        if window_size <= 0:
            raise ValueError("Window size must be positive")
            
        self.max_requests = max_requests
        self.window_size = window_size
        self.requests = []  # List of request timestamps
        self._lock = threading.Lock()
    
    def allow_request(self) -> bool:
        """
        Check if a request can be allowed based on sliding window.
        
        Returns:
            True if request is allowed, False if rate limited
        """
        with self._lock:
            now = time.time()
            
            # Remove old requests outside the window
            cutoff_time = now - self.window_size
            self.requests = [req_time for req_time in self.requests if req_time > cutoff_time]
            
            # Check if we can allow this request
            if len(self.requests) < self.max_requests:
                self.requests.append(now)
                return True
            return False
    
    def get_request_count(self) -> int:
        """Get current number of requests in the sliding window."""
        with self._lock:
            now = time.time()
            cutoff_time = now - self.window_size
            self.requests = [req_time for req_time in self.requests if req_time > cutoff_time]
            return len(self.requests)


def demo_token_bucket():
    """Demonstrate token bucket rate limiter."""
    print("=== Token Bucket Rate Limiter Demo ===")
    
    # Create a bucket with capacity 5, refill rate 2 tokens/second
    limiter = TokenBucket(capacity=5, refill_rate=2.0)
    
    print(f"Initial tokens: {limiter.get_available_tokens():.2f}")
    
    # Make several requests quickly
    for i in range(8):
        allowed = limiter.allow_request()
        tokens = limiter.get_available_tokens()
        print(f"Request {i+1}: {'ALLOWED' if allowed else 'BLOCKED'} (tokens: {tokens:.2f})")
        time.sleep(0.2)  # Small delay between requests
    
    print("\nWaiting 2 seconds for token refill...")
    time.sleep(2)
    
    print(f"Tokens after wait: {limiter.get_available_tokens():.2f}")
    
    # Try a few more requests
    for i in range(3):
        allowed = limiter.allow_request()
        tokens = limiter.get_available_tokens()
        print(f"Request {i+9}: {'ALLOWED' if allowed else 'BLOCKED'} (tokens: {tokens:.2f})")


def demo_sliding_window():
    """Demonstrate sliding window rate limiter."""
    print("\n=== Sliding Window Rate Limiter Demo ===")
    
    # Allow 3 requests per 2-second window
    limiter = SlidingWindowRateLimiter(max_requests=3, window_size=2.0)
    
    # Make several requests quickly
    for i in range(6):
        allowed = limiter.allow_request()
        count = limiter.get_request_count()
        print(f"Request {i+1}: {'ALLOWED' if allowed else 'BLOCKED'} (window count: {count})")
        time.sleep(0.3)
    
    print("\nWaiting 2.5 seconds for window to slide...")
    time.sleep(2.5)
    
    # Try more requests after window slides
    for i in range(3):
        allowed = limiter.allow_request()
        count = limiter.get_request_count()
        print(f"Request {i+7}: {'ALLOWED' if allowed else 'BLOCKED'} (window count: {count})")


if __name__ == "__main__":
    demo_token_bucket()
    demo_sliding_window()