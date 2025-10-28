package main

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"
)

// TokenBucket implements a thread-safe token bucket rate limiter.
// The token bucket algorithm maintains a bucket with a fixed capacity of tokens.
// Tokens are added to the bucket at a constant rate. When a request arrives,
// it consumes one token. If no tokens are available, the request is rejected.
//
// Time Complexity: O(1) per request
// Space Complexity: O(1)
type TokenBucket struct {
	capacity   int           // Maximum number of tokens
	tokens     float64       // Current number of tokens
	refillRate float64       // Tokens added per second
	lastRefill time.Time     // Last time tokens were refilled
	mu         sync.Mutex    // Mutex for thread safety
}

// NewTokenBucket creates a new TokenBucket rate limiter.
func NewTokenBucket(capacity int, refillRate float64) (*TokenBucket, error) {
	if capacity <= 0 {
		return nil, errors.New("capacity must be positive")
	}
	if refillRate <= 0 {
		return nil, errors.New("refill rate must be positive")
	}

	return &TokenBucket{
		capacity:   capacity,
		tokens:     float64(capacity), // Start with full bucket
		refillRate: refillRate,
		lastRefill: time.Now(),
	}, nil
}

// AllowRequest attempts to consume tokens for a request.
func (tb *TokenBucket) AllowRequest(tokensRequested int) bool {
	tb.mu.Lock()
	defer tb.mu.Unlock()

	tb.refillTokens()

	if tb.tokens >= float64(tokensRequested) {
		tb.tokens -= float64(tokensRequested)
		return true
	}
	return false
}

// AllowSingleRequest attempts to consume one token for a request.
func (tb *TokenBucket) AllowSingleRequest() bool {
	return tb.AllowRequest(1)
}

// refillTokens adds tokens based on elapsed time since last refill.
func (tb *TokenBucket) refillTokens() {
	now := time.Now()
	elapsed := now.Sub(tb.lastRefill).Seconds()
	tb.lastRefill = now

	// Add tokens based on elapsed time
	tokensToAdd := elapsed * tb.refillRate
	tb.tokens = min(float64(tb.capacity), tb.tokens+tokensToAdd)
}

// GetAvailableTokens returns the current number of available tokens.
func (tb *TokenBucket) GetAvailableTokens() float64 {
	tb.mu.Lock()
	defer tb.mu.Unlock()

	tb.refillTokens()
	return tb.tokens
}

// WaitForToken waits until a token becomes available or context is cancelled.
func (tb *TokenBucket) WaitForToken(ctx context.Context) error {
	ticker := time.NewTicker(10 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			if tb.AllowSingleRequest() {
				return nil
			}
		}
	}
}

// WaitForTokenWithTimeout waits until a token becomes available or timeout occurs.
func (tb *TokenBucket) WaitForTokenWithTimeout(timeout time.Duration) bool {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	return tb.WaitForToken(ctx) == nil
}

// GetCapacity returns the bucket capacity.
func (tb *TokenBucket) GetCapacity() int {
	return tb.capacity
}

// GetRefillRate returns the refill rate in tokens per second.
func (tb *TokenBucket) GetRefillRate() float64 {
	return tb.refillRate
}

// min returns the minimum of two float64 values.
func min(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}

// DemoTokenBucket demonstrates the token bucket rate limiter.
func DemoTokenBucket() {
	fmt.Println("=== Token Bucket Rate Limiter Demo ===")

	// Create a bucket with capacity 5, refill rate 2 tokens/second
	limiter, err := NewTokenBucket(5, 2.0)
	if err != nil {
		fmt.Printf("Error creating token bucket: %v\n", err)
		return
	}

	fmt.Printf("Initial tokens: %.2f\n", limiter.GetAvailableTokens())

	// Make several requests quickly
	for i := 0; i < 8; i++ {
		allowed := limiter.AllowSingleRequest()
		tokens := limiter.GetAvailableTokens()
		status := "BLOCKED"
		if allowed {
			status = "ALLOWED"
		}
		fmt.Printf("Request %d: %s (tokens: %.2f)\n", i+1, status, tokens)
		time.Sleep(200 * time.Millisecond)
	}

	fmt.Println("\nWaiting 2 seconds for token refill...")
	time.Sleep(2 * time.Second)

	fmt.Printf("Tokens after wait: %.2f\n", limiter.GetAvailableTokens())

	// Try a few more requests
	for i := 0; i < 3; i++ {
		allowed := limiter.AllowSingleRequest()
		tokens := limiter.GetAvailableTokens()
		status := "BLOCKED"
		if allowed {
			status = "ALLOWED"
		}
		fmt.Printf("Request %d: %s (tokens: %.2f)\n", i+9, status, tokens)
	}
}

// BenchmarkTokenBucket performs a simple benchmark of the token bucket.
func BenchmarkTokenBucket() {
	fmt.Println("\n=== Token Bucket Benchmark ===")

	limiter, _ := NewTokenBucket(1000, 500.0)
	iterations := 100000

	start := time.Now()
	allowed := 0
	for i := 0; i < iterations; i++ {
		if limiter.AllowSingleRequest() {
			allowed++
		}
	}
	elapsed := time.Since(start)

	fmt.Printf("Processed %d requests in %v\n", iterations, elapsed)
	fmt.Printf("Allowed: %d, Blocked: %d\n", allowed, iterations-allowed)
	fmt.Printf("Throughput: %.0f requests/second\n", float64(iterations)/elapsed.Seconds())
}