package main

import (
	"fmt"
	"runtime"
	"sync"
	"time"
)

// ConcurrencyDemo demonstrates concurrent access to rate limiters.
func ConcurrencyDemo() {
	fmt.Println("=== Concurrency Test ===")

	tokenBucket, _ := NewTokenBucket(10, 5.0)
	slidingWindow, _ := NewSlidingWindowRateLimiter(10, 1*time.Second)

	var wg sync.WaitGroup
	numGoroutines := 5
	requestsPerGoroutine := 5

	// Create multiple goroutines to test concurrent access
	for i := 0; i < numGoroutines; i++ {
		wg.Add(1)
		go func(goroutineID int) {
			defer wg.Done()

			for j := 0; j < requestsPerGoroutine; j++ {
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

				fmt.Printf("Goroutine %d, Request %d: Token=%s, Window=%s\n",
					goroutineID, j+1, tokenStatus, windowStatus)

				time.Sleep(100 * time.Millisecond)
			}
		}(i)
	}

	// Wait for all goroutines to complete
	wg.Wait()
}

// PerformanceComparison compares the performance of different rate limiters.
func PerformanceComparison() {
	fmt.Println("\n=== Performance Comparison ===")

	tokenBucket, _ := NewTokenBucket(1000, 500.0)
	slidingWindow, _ := NewSlidingWindowRateLimiter(1000, 2*time.Second)

	iterations := 50000

	// Test Token Bucket performance
	start := time.Now()
	tokenAllowed := 0
	for i := 0; i < iterations; i++ {
		if tokenBucket.AllowSingleRequest() {
			tokenAllowed++
		}
	}
	tokenBucketTime := time.Since(start)

	// Test Sliding Window performance
	start = time.Now()
	windowAllowed := 0
	for i := 0; i < iterations; i++ {
		if slidingWindow.AllowRequest() {
			windowAllowed++
		}
	}
	slidingWindowTime := time.Since(start)

	fmt.Printf("Token Bucket: %d allowed, %v\n", tokenAllowed, tokenBucketTime)
	fmt.Printf("Sliding Window: %d allowed, %v\n", windowAllowed, slidingWindowTime)
	fmt.Printf("Performance ratio: %.2fx\n", float64(slidingWindowTime)/float64(tokenBucketTime))
}

// MemoryUsageDemo shows memory usage characteristics.
func MemoryUsageDemo() {
	fmt.Println("\n=== Memory Usage Demo ===")

	var m1, m2 runtime.MemStats

	// Measure memory before creating rate limiters
	runtime.GC()
	runtime.ReadMemStats(&m1)

	// Create rate limiters and make many requests
	tokenBucket, _ := NewTokenBucket(1000, 100.0)
	slidingWindow, _ := NewSlidingWindowRateLimiter(1000, 10*time.Second)

	// Make many requests to fill up sliding window
	for i := 0; i < 5000; i++ {
		tokenBucket.AllowSingleRequest()
		slidingWindow.AllowRequest()
	}

	// Measure memory after
	runtime.GC()
	runtime.ReadMemStats(&m2)

	fmt.Printf("Memory used: %d KB\n", (m2.Alloc-m1.Alloc)/1024)
	fmt.Printf("Token bucket tokens: %.2f\n", tokenBucket.GetAvailableTokens())
	fmt.Printf("Sliding window requests: %d\n", slidingWindow.GetRequestCount())
}

// ErrorHandlingDemo demonstrates error handling and edge cases.
func ErrorHandlingDemo() {
	fmt.Println("\n=== Error Handling Demo ===")

	// Test invalid parameters
	_, err := NewTokenBucket(0, 1.0)
	if err != nil {
		fmt.Printf("Expected error for zero capacity: %v\n", err)
	}

	_, err = NewTokenBucket(10, -1.0)
	if err != nil {
		fmt.Printf("Expected error for negative refill rate: %v\n", err)
	}

	_, err = NewSlidingWindowRateLimiter(-1, time.Second)
	if err != nil {
		fmt.Printf("Expected error for negative max requests: %v\n", err)
	}

	_, err = NewSlidingWindowRateLimiter(10, -time.Second)
	if err != nil {
		fmt.Printf("Expected error for negative window size: %v\n", err)
	}

	// Test edge cases
	limiter, _ := NewTokenBucket(1, 0.1) // Very slow refill
	fmt.Printf("Very slow refill - first request: %t\n", limiter.AllowSingleRequest())
	fmt.Printf("Very slow refill - second request: %t\n", limiter.AllowSingleRequest())

	// Test very small window
	smallWindow, _ := NewSlidingWindowRateLimiter(1, 10*time.Millisecond)
	fmt.Printf("Small window - first request: %t\n", smallWindow.AllowRequest())
	time.Sleep(15 * time.Millisecond)
	fmt.Printf("Small window - after window expires: %t\n", smallWindow.AllowRequest())
}

func main() {
	fmt.Println("Rate Limiter Comprehensive Demo (Go)")
	fmt.Println("====================================")

	// Run individual algorithm demos
	DemoTokenBucket()
	fmt.Println()

	DemoSlidingWindow()
	fmt.Println()

	// Run comparison and analysis demos
	ComparativeDemo()
	ConcurrencyDemo()
	PerformanceComparison()
	MemoryUsageDemo()
	ErrorHandlingDemo()

	// Run benchmarks
	BenchmarkTokenBucket()
	BenchmarkSlidingWindow()

	fmt.Println("\nDemo completed!")
}