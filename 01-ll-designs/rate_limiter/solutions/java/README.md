# Rate Limiter - Java Implementation

This directory contains Java implementations of different rate limiting algorithms.

## Implementations

1. **TokenBucket.java** - Classic token bucket algorithm
2. **SlidingWindowRateLimiter.java** - Sliding window rate limiter
3. **RateLimiterDemo.java** - Demonstration of both algorithms

## Compilation and Execution

```bash
# Compile all Java files
javac *.java

# Run the demo
java RateLimiterDemo

# Run individual classes
java TokenBucket
java SlidingWindowRateLimiter
```

## Requirements

- Java 8 or higher
- No external dependencies required

## Features

- Thread-safe implementations
- Configurable capacity and refill rates
- Comprehensive error handling
- Performance optimized

## Time Complexity
- Token Bucket: O(1) per request
- Sliding Window: O(log n) per request where n is window size

## Space Complexity
- Token Bucket: O(1)
- Sliding Window: O(n) where n is number of requests in window