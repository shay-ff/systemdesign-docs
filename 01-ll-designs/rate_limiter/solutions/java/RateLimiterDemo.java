/**
 * Comprehensive demonstration of rate limiting algorithms.
 * 
 * This class showcases both Token Bucket and Sliding Window rate limiters
 * with various scenarios and performance comparisons.
 */
public class RateLimiterDemo {
    
    /**
     * Demonstrates concurrent access to rate limiters.
     */
    public static void demonstrateConcurrency() {
        System.out.println("=== Concurrency Test ===");
        
        TokenBucket tokenBucket = new TokenBucket(10, 5.0);
        SlidingWindowRateLimiter slidingWindow = new SlidingWindowRateLimiter(10, 1000);
        
        // Create multiple threads to test concurrent access
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 5; j++) {
                    boolean tokenAllowed = tokenBucket.allowRequest();
                    boolean windowAllowed = slidingWindow.allowRequest();
                    
                    System.out.printf("Thread %d, Request %d: Token=%s, Window=%s%n",
                        threadId, j + 1, 
                        tokenAllowed ? "ALLOWED" : "BLOCKED",
                        windowAllowed ? "ALLOWED" : "BLOCKED");
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Compares performance characteristics of different rate limiters.
     */
    public static void performanceComparison() {
        System.out.println("\n=== Performance Comparison ===");
        
        TokenBucket tokenBucket = new TokenBucket(1000, 500.0);
        SlidingWindowRateLimiter slidingWindow = new SlidingWindowRateLimiter(1000, 2000);
        
        int iterations = 10000;
        
        // Test Token Bucket performance
        long startTime = System.nanoTime();
        int tokenAllowed = 0;
        for (int i = 0; i < iterations; i++) {
            if (tokenBucket.allowRequest()) {
                tokenAllowed++;
            }
        }
        long tokenBucketTime = System.nanoTime() - startTime;
        
        // Test Sliding Window performance
        startTime = System.nanoTime();
        int windowAllowed = 0;
        for (int i = 0; i < iterations; i++) {
            if (slidingWindow.allowRequest()) {
                windowAllowed++;
            }
        }
        long slidingWindowTime = System.nanoTime() - startTime;
        
        System.out.printf("Token Bucket: %d allowed, %.2f ms%n", 
            tokenAllowed, tokenBucketTime / 1_000_000.0);
        System.out.printf("Sliding Window: %d allowed, %.2f ms%n", 
            windowAllowed, slidingWindowTime / 1_000_000.0);
        System.out.printf("Performance ratio: %.2fx%n", 
            (double) slidingWindowTime / tokenBucketTime);
    }
    
    /**
     * Demonstrates burst handling capabilities.
     */
    public static void burstHandlingDemo() {
        System.out.println("\n=== Burst Handling Demo ===");
        
        // Token bucket allows bursts up to capacity
        TokenBucket tokenBucket = new TokenBucket(5, 1.0);
        
        // Sliding window spreads requests evenly
        SlidingWindowRateLimiter slidingWindow = new SlidingWindowRateLimiter(5, 5000);
        
        System.out.println("Making 10 rapid requests:");
        
        for (int i = 0; i < 10; i++) {
            boolean tokenAllowed = tokenBucket.allowRequest();
            boolean windowAllowed = slidingWindow.allowRequest();
            
            System.out.printf("Request %d: Token=%s, Window=%s%n",
                i + 1,
                tokenAllowed ? "ALLOWED" : "BLOCKED",
                windowAllowed ? "ALLOWED" : "BLOCKED");
        }
        
        // Wait and try again
        System.out.println("\nWaiting 3 seconds...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        System.out.println("Making 5 more requests:");
        for (int i = 0; i < 5; i++) {
            boolean tokenAllowed = tokenBucket.allowRequest();
            boolean windowAllowed = slidingWindow.allowRequest();
            
            System.out.printf("Request %d: Token=%s, Window=%s%n",
                i + 11,
                tokenAllowed ? "ALLOWED" : "BLOCKED",
                windowAllowed ? "ALLOWED" : "BLOCKED");
        }
    }
    
    /**
     * Main method to run all demonstrations.
     */
    public static void main(String[] args) {
        System.out.println("Rate Limiter Comprehensive Demo");
        System.out.println("===============================");
        
        // Run individual demos
        TokenBucket.demo();
        System.out.println();
        
        SlidingWindowRateLimiter.demo();
        System.out.println();
        
        // Run comparison demos
        demonstrateConcurrency();
        performanceComparison();
        burstHandlingDemo();
        
        System.out.println("\nDemo completed!");
    }
}