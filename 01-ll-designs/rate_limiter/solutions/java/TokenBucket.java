/**
 * Token Bucket Rate Limiter Implementation
 * 
 * The token bucket algorithm maintains a bucket with a fixed capacity of tokens.
 * Tokens are added to the bucket at a constant rate. When a request arrives,
 * it consumes one token. If no tokens are available, the request is rejected.
 * 
 * Time Complexity: O(1) per request
 * Space Complexity: O(1)
 */
public class TokenBucket {
    private final int capacity;
    private final double refillRate;
    private double tokens;
    private long lastRefillTime;
    private final Object lock = new Object();
    
    /**
     * Creates a new TokenBucket rate limiter.
     * 
     * @param capacity Maximum number of tokens the bucket can hold
     * @param refillRate Number of tokens added per second
     * @throws IllegalArgumentException if capacity or refillRate is non-positive
     */
    public TokenBucket(int capacity, double refillRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (refillRate <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive");
        }
        
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity; // Start with full bucket
        this.lastRefillTime = System.nanoTime();
    }
    
    /**
     * Attempts to consume tokens for a request.
     * 
     * @param tokensRequested Number of tokens to consume (default: 1)
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowRequest(int tokensRequested) {
        synchronized (lock) {
            refillTokens();
            
            if (tokens >= tokensRequested) {
                tokens -= tokensRequested;
                return true;
            }
            return false;
        }
    }
    
    /**
     * Attempts to consume one token for a request.
     * 
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowRequest() {
        return allowRequest(1);
    }
    
    /**
     * Refills tokens based on elapsed time since last refill.
     */
    private void refillTokens() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillTime) / 1_000_000_000.0;
        lastRefillTime = now;
        
        // Add tokens based on elapsed time
        double tokensToAdd = elapsedSeconds * refillRate;
        tokens = Math.min(capacity, tokens + tokensToAdd);
    }
    
    /**
     * Gets the current number of available tokens.
     * 
     * @return Current token count
     */
    public double getAvailableTokens() {
        synchronized (lock) {
            refillTokens();
            return tokens;
        }
    }
    
    /**
     * Waits until a token becomes available.
     * 
     * @param timeoutMs Maximum time to wait in milliseconds (0 for no timeout)
     * @return true if token acquired, false if timeout occurred
     * @throws InterruptedException if thread is interrupted while waiting
     */
    public boolean waitForToken(long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        
        while (true) {
            if (allowRequest()) {
                return true;
            }
            
            if (timeoutMs > 0 && (System.currentTimeMillis() - startTime) >= timeoutMs) {
                return false;
            }
            
            // Sleep for a short time before checking again
            Thread.sleep(10);
        }
    }
    
    /**
     * Gets the bucket capacity.
     * 
     * @return Maximum number of tokens the bucket can hold
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Gets the refill rate.
     * 
     * @return Number of tokens added per second
     */
    public double getRefillRate() {
        return refillRate;
    }
    
    /**
     * Demo method to show token bucket in action.
     */
    public static void demo() {
        System.out.println("=== Token Bucket Rate Limiter Demo ===");
        
        // Create a bucket with capacity 5, refill rate 2 tokens/second
        TokenBucket limiter = new TokenBucket(5, 2.0);
        
        System.out.printf("Initial tokens: %.2f%n", limiter.getAvailableTokens());
        
        // Make several requests quickly
        for (int i = 0; i < 8; i++) {
            boolean allowed = limiter.allowRequest();
            double tokens = limiter.getAvailableTokens();
            System.out.printf("Request %d: %s (tokens: %.2f)%n", 
                i + 1, allowed ? "ALLOWED" : "BLOCKED", tokens);
            
            try {
                Thread.sleep(200); // Small delay between requests
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("\nWaiting 2 seconds for token refill...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        System.out.printf("Tokens after wait: %.2f%n", limiter.getAvailableTokens());
        
        // Try a few more requests
        for (int i = 0; i < 3; i++) {
            boolean allowed = limiter.allowRequest();
            double tokens = limiter.getAvailableTokens();
            System.out.printf("Request %d: %s (tokens: %.2f)%n", 
                i + 9, allowed ? "ALLOWED" : "BLOCKED", tokens);
        }
    }
    
    /**
     * Main method for standalone execution.
     */
    public static void main(String[] args) {
        demo();
    }
}