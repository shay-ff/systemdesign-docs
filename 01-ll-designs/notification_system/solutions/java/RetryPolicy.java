/**
 * Generic, reusable retry budget: max attempts + exponential backoff.
 *
 * WHY EXPONENTIAL backoff: a downstream that just failed N times is likely
 * saturated; hammering it at a constant interval is a self-inflicted DDoS.
 * Each failure doubles the wait (attempt 1 waits b, attempt 2 waits 2b,
 * attempt 3 waits 4b...), giving the vendor time to drain its queue.
 *
 * WHY a CLASS (and not "retry 3 times" inline in the dispatcher): retry
 * semantics are a POLICY - different channels can run different budgets
 * (email: 3 attempts with 1s backoff; billing-critical: 6 attempts with a
 * cap). Injecting the policy means the dispatcher code has ZERO retry
 * branches; changing the budget is configuration, not a code edit.
 *
 * Units: milliseconds, deliberately tiny for the demo (base 150ms) so the
 * whole story - 3 attempts with exponential waits - plays out in under two
 * seconds. A production base would be seconds-to-minutes; say that out
 * loud. The cap prevents pathological waits when maxAttempts is large.
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;

    public RetryPolicy(int maxAttempts, long baseBackoffMillis, long maxBackoffMillis) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Max attempts must be >= 1, got "
                    + maxAttempts);
        }
        if (baseBackoffMillis < 0) {
            throw new IllegalArgumentException("Base backoff cannot be negative, got "
                    + baseBackoffMillis + "ms");
        }
        if (maxBackoffMillis < baseBackoffMillis) {
            throw new IllegalArgumentException("Max backoff (" + maxBackoffMillis
                    + "ms) cannot be below base (" + baseBackoffMillis + "ms)");
        }
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    /** True when one more attempt is allowed (attempts are 1-based). */
    public boolean canRetry(int attemptsSoFar) {
        if (attemptsSoFar < 0) {
            throw new IllegalArgumentException("Attempts cannot be negative, got "
                    + attemptsSoFar);
        }
        return attemptsSoFar < maxAttempts;
    }

    /**
     * Backoff BEFORE attempt N (1-based): base * 2^(n-1), capped.
     * attempt 1 -> base, attempt 2 -> 2x base, attempt 3 -> 4x base...
     */
    public long backoffMillisBefore(int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number is 1-based, got "
                    + attemptNumber);
        }
        long shift = attemptNumber - 1;
        // Guard the shift: 2^63 overflow is not a real risk with sane caps,
        // but cap EARLY so huge attempt numbers saturate at maxBackoff.
        if (shift >= 40) {
            return maxBackoffMillis;
        }
        long backoff = baseBackoffMillis * (1L << shift);
        return Math.min(backoff, maxBackoffMillis);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Override
    public String toString() {
        return "RetryPolicy[maxAttempts=" + maxAttempts + ", base=" + baseBackoffMillis
                + "ms, cap=" + maxBackoffMillis + "ms, exponential]";
    }
}
