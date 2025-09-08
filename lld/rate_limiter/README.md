Token Bucket Rate Limiter (simulation) in C++.
Demonstrates algorithm — not a production distributed implementation.

## Build & Run
```bash
cd lld/rate_limiter/src
g++ -std=c++17 token_bucket.cpp -o token && ./token
```

## Notes
- Simulates refill rate and bucket capacity; extend with time-based tests.
