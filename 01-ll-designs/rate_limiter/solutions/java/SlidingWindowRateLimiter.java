import java.util.LinkedList;
import java.util.Queue;

/**
 * Sliding Window Rate Limiter Implementation
 * 
 * Maintains a sliding window of requests and allows requests only if
 * the count within the window doesn't exceed the limit.
 * 
 * Time Complexity: O(log n) per request where n is window size
 * Space Complexity: O(n) where n is number of requests in window
 */
public class SlidingWindowRateLimiter {
    private final int maxRequests;
    private final long windowSizeMs;
    private final Queue<Long> requestTimestamps;
    private final Object lock = new Object();
    
    /**
     * Creates a new SlidingWindowRateLimiter.
     * 
     * @param maxRequests Maximum number of requests allowed in the window
     * @param windowSizeMs Size of the sliding window in milliseconds
     * @throws IllegalArgumentException if maxRequests or windowSizeMs is non-positive
     */
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeMs) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("Max requests must be positive");
        }
        if (windowSizeMs <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        
        this.maxRequests = maxRequests;
        this.windowSizeMs = windowSizeMs;
        this.requestTimestamps = new LinkedList<>();
    }
    
    /**
     * Attempts to allow a request based on the sliding window.
     * 
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowRequest() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            
            // Remove old requests outside the window
            removeOldRequests(now);
            
            // Check if we can allow this request
            if (requestTimestamps.size() < maxRequests) {
                requestTimestamps.offer(now);
                return true;
            }
            return false;
        }
    }
    
    /**
     * Removes requests that are outside the current sliding window.
     * 
     * @param currentTime Current timestamp in milliseconds
     */
    private void removeOldRequests(long currentTime) {
        long cutoffTime = currentTime - windowSizeMs;
        
        while (!requestTimestamps.isEmpty() && requestTimestamps.peek() <= cutoffTime) {
            requestTimestamps.poll();
        }
    }
    
    /**
     * Gets the current number of requests in the sliding window.
     * 
     * @return Number of requests in the current window
     */
    public int getRequestCount() {
        synchronized (lock) {
            removeOldRequests(System.currentTimeMillis());
            return requestTimestamps.size();
        }
    }
    
    /**
     * Gets the maximum number of requests allowed in the window.
     * 
     * @return Maximum requests per window
     */
    public int getMaxRequests() {
        return maxRequests;
    }
    
    /**
     * Gets the window size in milliseconds.
     * 
     * @return Window size in milliseconds
     */
    public long getWindowSizeMs() {
        return windowSizeMs;
    }
    
    /**
     * Calculates the time until the next request can be allowed.
     * 
     * @return Time in milliseconds until next request can be allowed, or 0 if immediate
     */
    public long getTimeUntilNextAllowedRequest() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            removeOldRequests(now);
            
            if (requestTimestamps.size() < maxRequests) {
                return 0; // Can make request immediately
            }
            
            // Need to wait until the oldest request in window expires
            Long oldestRequest = requestTimestamps.peek();
            if (oldestRequest != null) {
                return Math.max(0, (oldestRequest + windowSizeMs) - now);
            }
            
            return 0;
        }
    }
    
    /**
     * Demo method to show sliding window rate limiter in action.
     */
    public static void demo() {
        System.out.println("=== Sliding Window Rate Limiter Demo ===");
        
        // Allow 3 requests per 2-second window
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 2000);
        
        // Make several requests quickly
        for (int i = 0; i < 6; i++) {
            boolean allowed = limiter.allowRequest();
            int count = limiter.getRequestCount();
            System.out.printf("Request %d: %s (window count: %d)%n", 
                i + 1, allowed ? "ALLOWED" : "BLOCKED", count);
            
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("\nWaiting 2.5 seconds for window to slide...");
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Try more requests after window slides
        for (int i = 0; i < 3; i++) {
            boolean allowed = limiter.allowRequest();
            int count = limiter.getRequestCount();
            System.out.printf("Request %d: %s (window count: %d)%n", 
                i + 7, allowed ? "ALLOWED" : "BLOCKED", count);
        }
    }
    
    /**
     * Main method for standalone execution.
     */
    public static void main(String[] args) {
        demo();
    }
}