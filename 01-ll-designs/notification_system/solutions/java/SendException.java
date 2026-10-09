/**
 * A delivery attempt failed. Carries whether the failure is TRANSIENT (a
 * network blip, a vendor throttle - retrying makes sense) or PERMANENT (bad
 * address, expired token - retrying is pure waste). The retry policy reads
 * this flag: permanent failures skip straight to the dead-letter log, which
 * is the single most useful distinction a notification system can make.
 */
public class SendException extends Exception {

    private final boolean transientFailure;

    public SendException(String message, boolean transientFailure) {
        super(message);
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Exception message cannot be empty");
        }
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
