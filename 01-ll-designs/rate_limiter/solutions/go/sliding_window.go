package main

import (
	"errors"
	"fmt"
	"sync"
	"time"
)

// SlidingWindowRateLimiter implements a sliding window rate limiter.
// Maintains a sliding window of requests and allows requests only if
// the count within the window doesn't exceed the limit.
//
// Time Complexity: O(log n) per request where n is window size
// Space Complexity: O(n) where n is number of requests in window
type SlidingWindowRateLimiter struct {
	maxRequests    int           // Maximum requests allowed in window
	windowSize     time.Duration // Size of the sliding window
	requests       []time.Time   // Slice of request timestamps
	mu             sync.Mutex    // Mutex for thread safety
}

// NewSlidingWindowRateLimiter creates a new sliding window rate limiter.
func NewSlidingWindowRateLimiter(maxRequests int, windowSize time.Duration) (*SlidingWindowRateLimiter, error) {
	if maxRequests <= 0 {
		return nil, errors.New("max requests must be positive")
	}
	if windowSize <= 0 {
		return nil, errors.New("window size must be positive")
	}

	return &SlidingWindowRateLimiter{
		maxRequests: maxRequests,
		windowSize:  windowSize,
		requests:    make([]time.Time, 0),
	}, nil
}

// AllowRequest checks if a request can be allowed based on the sliding window.
func (sw *SlidingWindowRateLimiter) AllowRequest() bool {
	sw.mu.Lock()
	defer sw.mu.Unlock()

	now := time.Now()

	// Remove old requests outside the window
	sw.removeOldRequests(now)

	// Check if we can allow this request
	if len(sw.requests) < sw.maxRequests {
		sw.requests = append(sw.requests, now)
		return true
	}
	return false
}

// removeOldRequests removes requests that are outside the current sliding window.
func (sw *SlidingWindowRateLimiter) removeOldRequests(currentTime time.Time) {
	cutoffTime := currentTime.Add(-sw.windowSize)

	// Find the first request that's still within the window
	validIndex := 0
	for i, reqTime := range sw.requests {
		if reqTime.After(cutoffTime) {
			validIndex = i
			break
		}
		validIndex = i + 1
	}

	// Remove old requests by slicing
	if validIndex > 0 {
		if validIndex >= len(sw.requests) {
			sw.requests = sw.requests[:0] // Clear all requests
		} else {
			sw.requests = sw.requests[validIndex:]
		}
	}
}

// GetRequestCount returns the current number of requests in the sliding window.
func (sw *SlidingWindowRateLimiter) GetRequestCount() int {
	sw.mu.Lock()
	defer sw.mu.Unlock()

	sw.removeOldRequests(time.Now())
	return len(sw.requests)
}

// GetMaxRequests returns the maximum number of requests allowed in the window.
func (sw *SlidingWindowRateLimiter) GetMaxRequests() int {
	return sw.maxRequests
}

// GetWindowSize returns the window size.
func (sw *SlidingWindowRateLimiter) GetWindowSize() time.Duration {
	return sw.windowSize
}

// GetTimeUntilNextAllowedRequest calculates the time until the next request can be allowed.
func (sw *SlidingWindowRateLimiter) GetTimeUntilNextAllowedRequest() time.Duration {
	sw.mu.Lock()
	defer sw.mu.Unlock()

	now := time.Now()
	sw.removeOldRequests(now)

	if len(sw.requests) < sw.maxRequests {
		return 0 // Can make request immediately
	}

	// Need to wait until the oldest request in window expires
	if len(sw.requests) > 0 {
		oldestRequest := sw.requests[0]
		waitTime := oldestRequest.Add(sw.windowSize).Sub(now)
		if waitTime > 0 {
			return waitTime
		}
	}

	return 0
}

// Reset clears all request history.
func (sw *SlidingWindowRateLimiter) Reset() {
	sw.mu.Lock()
	defer sw.mu.Unlock()

	sw.requests = sw.requests[:0]
}

// DemoSlidingWindow demonstrates the sliding window rate limiter.
func DemoSlidingWindow() {
	fmt.Println("=== Sliding Window Rate Limiter Demo ===")

	// Allow 3 requests per 2-second window
	limiter, err := NewSlidingWindowRateLimiter(3, 2*time.Second)
	if err != nil {
		fmt.Printf("Error creating sliding window limiter: %v\n", err)
		return
	}

	// Make several requests quickly
	for i := 0; i < 6; i++ {
		allowed := limiter.AllowRequest()
		count := limiter.GetRequestCount()
		status := "BLOCKED"
		if allowed {
			status = "ALLOWED"
		}
		fmt.Printf("Request %d: %s (window count: %d)\n", i+1, status, count)
		time.Sleep(300 * time.Millisecond)
	}

	fmt.Println("\nWaiting 2.5 seconds for window to slide...")
	time.Sleep(2500 * time.Millisecond)

	// Try more requests after window slides
	for i := 0; i < 3; i++ {
		allowed := limiter.AllowRequest()
		count := limiter.GetRequestCount()
		status := "BLOCKED"
		if allowed {
			status = "ALLOWED"
		}
		fmt.Printf("Request %d: %s (window count: %d)\n", i+7, status, count)
	}
}

// BenchmarkSlidingWindow performs a simple benchmark of the sliding window limiter.
func BenchmarkSlidingWindow() {
	fmt.Println("\n=== Sliding Window Benchmark ===")

	limiter, _ := NewSlidingWindowRateLimiter(1000, 2*time.Second)
	iterations := 10000

	start := time.Now()
	allowed := 0
	for i := 0; i < iterations; i++ {
		if limiter.AllowRequest() {
			allowed++
		}
	}
	elapsed := time.Since(start)

	fmt.Printf("Processed %d requests in %v\n", iterations, elapsed)
	fmt.Printf("Allowed: %d, Blocked: %d\n", allowed, iterations-allowed)
	fmt.Printf("Throughput: %.0f requests/second\n", float64(iterations)/elapsed.Seconds())
}

// ComparativeDemo demonstrates both algorithms side by side.
func ComparativeDemo() {
	fmt.Println("\n=== Comparative Demo: Burst Handling ===")

	// Token bucket allows bursts up to capacity
	tokenBucket, _ := NewTokenBucket(5, 1.0)

	// Sliding window spreads requests evenly
	slidingWindow, _ := NewSlidingWindowRateLimiter(5, 5*time.Second)

	fmt.Println("Making 10 rapid requests:")

	for i := 0; i < 10; i++ {
		tokenAllowed := tokenBucket.AllowSingleRequest()
		windowAllowed := slidingWindow.AllowRequest()

		tokenStatus := "BLOCKED"
		if tokenAllowed {
			tokenStatus = "ALLOWED"
		}
		windowStatus := "BLOCKED"
		if windowAllowed {
			windowStatus = "ALLOWED"
		}

		fmt.Printf("Request %d: Token=%s, Window=%s\n", i+1, tokenStatus, windowStatus)
	}

	// Wait and try again
	fmt.Println("\nWaiting 3 seconds...")
	time.Sleep(3 * time.Second)

	fmt.Println("Making 5 more requests:")
	for i := 0; i < 5; i++ {
		tokenAllowed := tokenBucket.AllowSingleRequest()
		windowAllowed := slidingWindow.AllowRequest()

		tokenStatus := "BLOCKED"
		if tokenAllowed {
			tokenStatus = "ALLOWED"
		}
		windowStatus := "BLOCKED"
		if windowAllowed {
			windowStatus = "ALLOWED"
		}

		fmt.Printf("Request %d: Token=%s, Window=%s\n", i+11, tokenStatus, windowStatus)
	}
}