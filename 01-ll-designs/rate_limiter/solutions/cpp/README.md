# Rate Limiter - C++ Implementation

This directory contains C++ implementations of different rate limiting algorithms.

## Implementations

1. **token_bucket.cpp** - Enhanced token bucket algorithm with thread safety
2. **sliding_window.cpp** - Sliding window rate limiter implementation
3. **rate_limiter_demo.cpp** - Comprehensive demonstration program

## Compilation and Execution

```bash
# Compile individual files
g++ -std=c++17 -pthread token_bucket.cpp -o token_bucket
g++ -std=c++17 -pthread sliding_window.cpp -o sliding_window
g++ -std=c++17 -pthread rate_limiter_demo.cpp -o demo

# Run executables
./token_bucket
./sliding_window
./demo

# Compile all together
g++ -std=c++17 -pthread *.cpp -o rate_limiter_suite
./rate_limiter_suite
```

## Requirements

- C++17 or higher
- pthread library (for thread safety)
- Standard library support for chrono and thread

## Features

- Thread-safe implementations using std::mutex
- High-performance with minimal overhead
- Exception safety and error handling
- Comprehensive benchmarking capabilities
- Memory-efficient implementations

## Time Complexity
- Token Bucket: O(1) per request
- Sliding Window: O(log n) per request where n is window size

## Space Complexity
- Token Bucket: O(1)
- Sliding Window: O(n) where n is number of requests in window