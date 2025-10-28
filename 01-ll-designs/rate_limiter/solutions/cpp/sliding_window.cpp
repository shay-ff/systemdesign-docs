/**
 * Sliding Window Rate Limiter Implementation - C++
 * 
 * Maintains a sliding window of requests and allows requests only if
 * the count within the window doesn't exceed the limit.
 * 
 * Compile: g++ -std=c++17 -pthread sliding_window.cpp -o sliding_window
 * Run: ./sliding_window
 */

#include <iostream>
#include <chrono>
#include <thread>
#include <mutex>
#include <deque>
#include <stdexcept>
#include <algorithm>

/**
 * Thread-safe Sliding Window Rate Limiter
 * 
 * Time Complexity: O(log n) per request where n is window size
 * Space Complexity: O(n) where n is number of requests in window
 */
class SlidingWindowRateLimiter {
private:
    int max_requests_;
    std::chrono::milliseconds window_size_;
    std::deque<std::chrono::steady_clock::time_point> request_timestamps_;
    mutable std::mutex mutex_;

    void remove_old_requests(const std::chrono::steady_clock::time_point& current_time) {
        auto cutoff_time = current_time - window_size_;
        
        // Remove requests older than the window
        while (!request_timestamps_.empty() && request_timestamps_.front() <= cutoff_time) {
            request_timestamps_.pop_front();
        }
    }

public:
    /**
     * Constructor for SlidingWindowRateLimiter
     * @param max_requests Maximum number of requests allowed in the window
     * @param window_size_ms Size of the sliding window in milliseconds
     */
    SlidingWindowRateLimiter(int max_requests, int window_size_ms) 
        : max_requests_(max_requests), window_size_(window_size_ms) {
        if (max_requests <= 0) {
            throw std::invalid_argument("Max requests must be positive");
        }
        if (window_size_ms <= 0) {
            throw std::invalid_argument("Window size must be positive");
        }
    }

    /**
     * Attempts to allow a request based on the sliding window
     * @return true if request is allowed, false if rate limited
     */
    bool allow_request() {
        std::lock_guard<std::mutex> lock(mutex_);
        auto now = std::chrono::steady_clock::now();
        
        // Remove old requests outside the window
        remove_old_requests(now);
        
        // Check if we can allow this request
        if (static_cast<int>(request_timestamps_.size()) < max_requests_) {
            request_timestamps_.push_back(now);
            return true;
        }
        return false;
    }

    /**
     * Gets the current number of requests in the sliding window
     * @return Number of requests in the current window
     */
    int get_request_count() const {
        std::lock_guard<std::mutex> lock(mutex_);
        const_cast<SlidingWindowRateLimiter*>(this)->remove_old_requests(
            std::chrono::steady_clock::now());
        return static_cast<int>(request_timestamps_.size());
    }

    /**
     * Gets the maximum number of requests allowed in the window
     * @return Maximum requests per window
     */
    int get_max_requests() const {
        return max_requests_;
    }

    /**
     * Gets the window size in milliseconds
     * @return Window size in milliseconds
     */
    int get_window_size_ms() const {
        return static_cast<int>(window_size_.count());
    }

    /**
     * Calculates the time until the next request can be allowed
     * @return Time in milliseconds until next request can be allowed, or 0 if immediate
     */
    int get_time_until_next_allowed_request() const {
        std::lock_guard<std::mutex> lock(mutex_);
        auto now = std::chrono::steady_clock::now();
        const_cast<SlidingWindowRateLimiter*>(this)->remove_old_requests(now);
        
        if (static_cast<int>(request_timestamps_.size()) < max_requests_) {
            return 0; // Can make request immediately
        }
        
        // Need to wait until the oldest request in window expires
        if (!request_timestamps_.empty()) {
            auto oldest_request = request_timestamps_.front();
            auto wait_time = (oldest_request + window_size_) - now;
            auto wait_ms = std::chrono::duration_cast<std::chrono::milliseconds>(wait_time);
            return std::max(0, static_cast<int>(wait_ms.count()));
        }
        
        return 0;
    }

    /**
     * Resets the rate limiter by clearing all request history
     */
    void reset() {
        std::lock_guard<std::mutex> lock(mutex_);
        request_timestamps_.clear();
    }
};

/**
 * Demonstration of the sliding window rate limiter
 */
void demo_sliding_window() {
    std::cout << "=== Sliding Window Rate Limiter Demo ===" << std::endl;
    
    try {
        // Allow 3 requests per 2-second window
        SlidingWindowRateLimiter limiter(3, 2000);
        
        // Make several requests quickly
        for (int i = 0; i < 6; i++) {
            bool allowed = limiter.allow_request();
            int count = limiter.get_request_count();
            std::cout << "Request " << (i + 1) << ": " 
                      << (allowed ? "ALLOWED" : "BLOCKED") 
                      << " (window count: " << count << ")" << std::endl;
            std::this_thread::sleep_for(std::chrono::milliseconds(300));
        }
        
        std::cout << "\nWaiting 2.5 seconds for window to slide..." << std::endl;
        std::this_thread::sleep_for(std::chrono::milliseconds(2500));
        
        // Try more requests after window slides
        for (int i = 0; i < 3; i++) {
            bool allowed = limiter.allow_request();
            int count = limiter.get_request_count();
            std::cout << "Request " << (i + 7) << ": " 
                      << (allowed ? "ALLOWED" : "BLOCKED") 
                      << " (window count: " << count << ")" << std::endl;
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
    }
}

/**
 * Performance benchmark for the sliding window limiter
 */
void benchmark_sliding_window() {
    std::cout << "\n=== Sliding Window Benchmark ===" << std::endl;
    
    SlidingWindowRateLimiter limiter(1000, 2000);
    const int iterations = 50000;
    
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

/**
 * Demonstrates memory usage characteristics
 */
void memory_usage_demo() {
    std::cout << "\n=== Memory Usage Demo ===" << std::endl;
    
    SlidingWindowRateLimiter limiter(1000, 10000); // 10 second window
    
    // Fill up the window with requests
    std::cout << "Making 5000 requests to fill window..." << std::endl;
    int allowed = 0;
    for (int i = 0; i < 5000; i++) {
        if (limiter.allow_request()) {
            allowed++;
        }
    }
    
    std::cout << "Requests in window: " << limiter.get_request_count() << std::endl;
    std::cout << "Allowed: " << allowed << std::endl;
    
    // Wait for window to partially expire
    std::cout << "Waiting 5 seconds for partial window expiry..." << std::endl;
    std::this_thread::sleep_for(std::chrono::seconds(5));
    
    std::cout << "Requests in window after 5s: " << limiter.get_request_count() << std::endl;
    
    // Wait for full window expiry
    std::cout << "Waiting 6 more seconds for full window expiry..." << std::endl;
    std::this_thread::sleep_for(std::chrono::seconds(6));
    
    std::cout << "Requests in window after full expiry: " << limiter.get_request_count() << std::endl;
}

int main() {
    demo_sliding_window();
    benchmark_sliding_window();
    memory_usage_demo();
    return 0;
}