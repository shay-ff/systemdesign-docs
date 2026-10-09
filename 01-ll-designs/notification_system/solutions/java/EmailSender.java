/**
 * Mock EMAIL sender (stands in for SMTP or a SES-class API).
 *
 * FAILURE SCRIPTING - the piece that makes the retry demo honest: instead of
 * a random failure rate (nondeterministic - a demo can flake), the sender
 * takes a SCRIPT of attempt numbers that must fail. "Fail the first 2
 * attempts, succeed the 3rd" is exactly what a transient SMTP greylist looks
 * like, and it is fully deterministic: the same demo run produces the same
 * retry-then-succeed story every time.
 *
 * A second knob (failAllToRecipient) makes PERMANENT failures possible for
 * one specific recipient - a bounced address - so the demo can show a
 * permanent failure skipping retries into the dead-letter log.
 */
public class EmailSender implements NotificationSender {

    private final java.util.Set<Integer> failOnAttempts;

    /** @param failOnAttempts attempt numbers (1-based) that must fail */
    public EmailSender(int... failOnAttempts) {
        this.failOnAttempts = new java.util.HashSet<>();
        for (int attempt : failOnAttempts) {
            if (attempt < 1) {
                throw new IllegalArgumentException(
                    "Attempt numbers are 1-based, got " + attempt);
            }
            this.failOnAttempts.add(attempt);
        }
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) throws SendException {
        int attempt = notification.getAttemptCount();
        if (failOnAttempts.contains(attempt)) {
            // Transient: SMTP 4xx greylisting - retry later, it will pass.
            throw new SendException("Email gateway transient failure on attempt "
                    + attempt + " (greylisted - retry)", true);
        }
        // Success - the mock "delivers" the fully formatted email.
        System.out.println("    [EMAIL delivered] " + notification.format());
    }
}
