/**
 * Mock SMS sender (stands in for an SMS gateway API). Deterministic failure
 * scripting exactly like EmailSender; the demo uses it for the
 * "retries exhausted -> DEAD_LETTER" story: every attempt fails, the retry
 * budget runs out, and the notification lands in the dead-letter log with a
 * full attempt history.
 */
public class SmsSender implements NotificationSender {

    private final int failAllAttemptsUpTo;

    /**
     * @param failAllAttemptsUpTo every attempt numbered <= this fails
     *        (Integer.MAX_VALUE = fail forever: nothing but the retry
     *        budget can stop it)
     */
    public SmsSender(int failAllAttemptsUpTo) {
        if (failAllAttemptsUpTo < 1) {
            throw new IllegalArgumentException(
                "Failure bound must be >= 1, got " + failAllAttemptsUpTo);
        }
        this.failAllAttemptsUpTo = failAllAttemptsUpTo;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification) throws SendException {
        int attempt = notification.getAttemptCount();
        if (attempt <= failAllAttemptsUpTo) {
            throw new SendException("SMS gateway rejected attempt " + attempt
                    + " (carrier throttle - transient)", true);
        }
        System.out.println("    [SMS delivered] " + notification.format());
    }
}
