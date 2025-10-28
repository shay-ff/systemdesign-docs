/**
 * Enhanced Token Bucket Rate Limiter Implementation - C++
 * 
 * This is an enhanced version with thread safety, better documentation,
 * and additional features for production use.
 * 
 * Compile: g++ -std=c++17 -pthread token_bucket.cpp -o token_bucket
 * Run: ./token_bucket
 */

#include <iostream>
#include <chrono>
#include <thread>
#include <mutex>
#include <stdexcept>
#include <algorithm>

/**
 * Thread-safe Token Bucket Rate Limiter
 * 
 * The token bucket algorithm maintains a bucket with a fixed capacity of tokens.
 * Tokens are added to the bucket at a constant rate. When a request arrives,
 * it consumes one token. If no tokens are available, the request is rejected.
 * 
 * Time Complexity: O(1) per request
 * Space Complexity: O(1)
 */
class TokenBucket {
private:
    double capacity_;
    double tokens_;
    double refill_rate_;
    std::chrono::steady_clock::time_point last_refill_;
    mutable std::mutex mutex_;

    void refill_tokens() {
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration<double>(now - last_refill_).count();
        last_refill_ = now;
        
        // Add tokens based on elapsed time
        double tokens_to_add = elapsed * refill_rate_;
        tokens_ = std::min(capacity_, tokens_ + tokens_to_add);
    }

public:
    /**
     * Constructor for TokenBucket
     * @param capacity Maximum number of tokens the bucket can hold
     * @param refill_rate Number of tokens added per second
     */
    TokenBucket(double capacity, double refill_rate) 
        : capacity_(capacity), tokens_(capacity), refill_rate_(refill_rate) {
        if (capacity <= 0) {
            throw std::invalid_argument("Capacity must be positive");
        }
        if (refill_rate <= 0) {
            throw std::invalid_argument("Refill rate must be positive");
        }
        last_refill_ = std::chrono::steady_clock::now();
    }

    /**
     * Attempts to consume tokens for a request
     * @param tokens_requested Number of tokens to consume (default: 1)
     * @return true if request is allowed, false if rate limited
     */
    bool allow_request(int tokens_requested = 1) {
        std::lock_guard<std::mutex> lock(mutex_);
        refill_tokens();
        
        if (tokens_ >= tokens_requested) {
            tokens_ -= tokens_requested;
            return true;
        }
        return false;
    }

    /**
     * Gets the current number of available tokens
     * @return Current token count
     */
    double get_available_tokens() const {
        std::lock_guard<std::mutex> lock(mutex_);
        const_cast<TokenBucket*>(this)->refill_tokens();
        return tokens_;
    }

    /**
     * Gets the bucket capacity
     * @return Maximum number of tokens the bucket can hold
     */
    double get_capacity() const {
        return capacity_;
    }

    /**
     * Gets the refill rate
     * @return Number of tokens added per second
     */
    double get_refill_rate() const {
        return refill_rate_;
    }

    /**
     * Waits until a token becomes available
     * @param timeout_ms Maximum time to wait in milliseconds (0 for no timeout)
     * @return true if token acquired, false if timeout occurred
     */
    bool wait_for_token(int timeout_ms = 0) {
        auto start_time = std::chrono::steady_clock::now();
        
        while (true) {
            if (allow_request()) {
                return true;
            }
            
            if (timeout_ms > 0) {
                auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - start_time).count();
                if (elapsed >= timeout_ms) {
                    return false;
                }
            }
            
            // Sleep for a short time before checking again
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }
};

/**
 * Demonstration of the enhanced token bucket rate limiter
 */
void demo_token_bucket() {
    std::cout << "=== Enhanced Token Bucket Rate Limiter Demo ===" << std::endl;
    
    try {
        // Create a bucket with capacity 5, refill rate 2 tokens/second
        TokenBucket limiter(5.0, 2.0);
        
        std::cout << "Initial tokens: " << limiter.get_available_tokens() << std::endl;
        
        // Make several requests quickly
        for (int i = 0; i < 8; i++) {
            bool allowed = limiter.allow_request();
            double tokens = limiter.get_available_tokens();
            std::cout << "Request " << (i + 1) << ": " 
                      << (allowed ? "ALLOWED" : "BLOCKED") 
                      << " (tokens: " << tokens << ")" << std::endl;
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }
        
        std::cout << "\nWaiting 2 seconds for token refill..." << std::endl;
        std::this_thread::sleep_for(std::chrono::seconds(2));
        
        std::cout << "Tokens after wait: " << limiter.get_available_tokens() << std::endl;
        
        // Try a few more requests
        for (int i = 0; i < 3; i++) {
            bool allowed = limiter.allow_request();
            double tokens = limiter.get_available_tokens();
            std::cout << "Request " << (i + 9) << ": " 
                      << (allowed ? "ALLOWED" : "BLOCKED") 
                      << " (tokens: " << tokens << ")" << std::endl;
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
    }
}

/**
 * Performance benchmark for the token bucket
 */
void benchmark_token_bucket() {
    std::cout << "\n=== Token Bucket Benchmark ===" << std::endl;
    
    TokenBucket limiter(1000, 500.0);
    const int iterations = 100000;
    
    auto start = std::chrono::high_resolution_clock::now();
    int allowed = 0;
    
    for (int i = 0; i < iterations; i++) {
        if (limiter.allow_request()) {
            allowed++;
        }
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);
    
    std::cout << "Processed " << iterations << " requests in " 
              << duration.count() << " microseconds" << std::endl;
    std::cout << "Allowed: " << allowed << ", Blocked: " << (iterations - allowed) << std::endl;
    std::cout << "Throughput: " << (iterations * 1000000.0 / duration.count()) 
              << " requests/second" << std::endl;
}

int main() {
    demo_token_bucket();
    benchmark_token_bucket();
    return 0;
}