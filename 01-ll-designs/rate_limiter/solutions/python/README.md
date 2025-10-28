# Rate Limiter - Python Implementation

This directory contains Python implementations of different rate limiting algorithms.

## Implementations

1. **Token Bucket** (`token_bucket.py`) - Classic token bucket algorithm
2. **Sliding Window** (`sliding_window.py`) - Sliding window rate limiter

## Running the Code

```bash
# Token Bucket
python token_bucket.py

# Sliding Window
python sliding_window.py
```

## Requirements

- Python 3.7+
- No external dependencies required

## Time Complexity
- Token Bucket: O(1) per request
- Sliding Window: O(log n) per request where n is window size

## Space Complexity
- Token Bucket: O(1)
- Sliding Window: O(n) where n is number of requests in window