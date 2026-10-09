/**
 * Delivery lifecycle:
 *
 *   QUEUED -> SENT
 *   QUEUED -> (retrying) QUEUED -> SENT
 *   QUEUED -> (retries exhausted) DEAD_LETTER
 *   QUEUED -> DUPLICATE (deduped on redelivery - same notificationId seen)
 *   QUEUED -> RATE_LIMITED (per-channel limiter deferred it; it re-queues)
 *
 * FAILED is the terminal per-attempt outcome; DEAD_LETTER is the terminal
 * NOTIFICATION outcome after the retry budget is spent. The dead-letter log
 * is what an ops person greps at 2am - it must exist as a first-class
 * concept, not an exception message.
 */
public enum NotificationStatus {
    QUEUED("Queued"),
    SENT("Sent"),
    FAILED("Failed attempt - retrying"),
    DEAD_LETTER("Dead letter - retries exhausted"),
    DUPLICATE("Duplicate - deduped by notificationId"),
    RATE_LIMITED("Deferred by per-channel rate limiter");

    private final String label;

    NotificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return name();
    }
}
