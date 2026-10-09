/**
 * Minimal built-in token bucket for PER-CHANNEL rate limiting.
 *
 * Deliberately SIMPLE - a notification demo should not re-teach rate
 * limiting. The repo already has the fuller treatment with sliding windows
 * and refill math in `01-ll-designs/rate_limiter/` (TokenBucket.java with
 * continuous refill + SlidingWindowRateLimiter.java); this class is the
 * inlined minimum the dispatcher needs: allow(n) -> true/false.
 *
 * WHY limit per channel at all (the interview point): vendors enforce
 * quotas (SMS gateways: messages/second; SMTP servers: connections/minute).
 * Exceed them and the vendor throttles EVERYTHING - including your urgent
 * traffic - so the sender-side limiter is the defensive move that keeps
 * the vendor-side 429s from happening in the first place.
 *
 * Thread-safety: synchronized - the bucket is tiny shared state read by
 * every worker thread on every send decision; a lock-free version is
 * possible (AtomicReference on an immutable bucket snapshot) but adds
 * subtlety a demo does not need. The rate_limiter/ problem covers it.
 */
public class TokenBucketRateLimiter {

    private final int capacity;
    private final double refillTokensPerMillis;
    private double tokens;
    private long lastRefillNanos;

    /**
     * @param capacity bucket size (burst allowance)
     * @param refillTokensPerSecond steady refill rate
     */
    public TokenBucketRateLimiter(int capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got " + capacity);
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException(
                "Refill rate must be positive, got " + refillTokensPerSecond);
        }
        this.capacity = capacity;
        this.refillTokensPerMillis = refillTokensPerSecond / 1000.0;
        this.tokens = capacity; // start full: an idle system may burst
        this.lastRefillNanos = System.nanoTime();
    }

    /** Consumes one token; false = the channel is over budget right now. */
    public synchronized boolean allow() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedMillis = (now - lastRefillNanos) / 1_000_000.0;
        if (elapsedMillis > 0) {
            tokens = Math.min(capacity, tokens + elapsedMillis * refillTokensPerMillis);
            lastRefillNanos = now;
        }
    }

    @Override
    public String toString() {
        return "TokenBucket[burst=" + capacity + ", refill="
                + String.format("%.1f/s", refillTokensPerMillis * 1000.0) + "]";
    }
}
