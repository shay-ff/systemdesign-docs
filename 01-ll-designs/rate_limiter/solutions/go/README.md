# Rate Limiter - Go Implementation

This directory contains Go implementations of different rate limiting algorithms.

## Implementations

1. **token_bucket.go** - Classic token bucket algorithm
2. **sliding_window.go** - Sliding window rate limiter
3. **main.go** - Demonstration of both algorithms

## Running the Code

```bash
# Run all demos
go run *.go

# Run individual files
go run token_bucket.go
go run sliding_window.go

# Build executable
go build -o rate_limiter *.go
./rate_limiter
```

## Requirements

- Go 1.16 or higher
- No external dependencies required

## Features

- Goroutine-safe implementations using sync.Mutex
- Context support for cancellation
- Efficient memory usage
- High-performance implementations

## Time Complexity
- Token Bucket: O(1) per request
- Sliding Window: O(log n) per request where n is window size

## Space Complexity
- Token Bucket: O(1)
- Sliding Window: O(n) where n is number of requests in window