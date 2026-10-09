import java.time.Instant;

/**
 * One immutable delivery attempt record: attempt number, outcome, timestamp.
 *
 * WHY a separate attempt record (instead of just a status on the
 * notification): the DEAD-LETTER story requires attempt HISTORY - "we tried
 * 3 times at these timestamps, all carrier throttles" is the sentence an
 * ops person needs. A single mutable status field erases that history the
 * moment the next attempt starts. Immutable attempt rows, appended, never
 * rewritten - same reasoning as an audit log.
 */
public final class NotificationAttempt {

    private final int attemptNumber;
    private final String outcome;          // "sent" / failure message
    private final Instant at;

    public NotificationAttempt(int attemptNumber, String outcome) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number is 1-based, got "
                    + attemptNumber);
        }
        if (outcome == null || outcome.trim().isEmpty()) {
            throw new IllegalArgumentException("Attempt outcome cannot be empty");
        }
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.at = Instant.now();
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getAt() {
        return at;
    }

    @Override
    public String toString() {
        return "#" + attemptNumber + " @ " + at + " -> " + outcome;
    }
}
