/**
 * Mock PUSH sender (stands in for FCM-class push). This one also demonstrates
 * the PERMANENT-failure flag: one specific recipient is a dead device token.
 * Permanent failures do NOT burn the retry budget - the dispatcher drops
 * them straight to the dead-letter log, because retrying an expired token
 * is guaranteed to fail the same way.
 */
public class PushSender implements NotificationSender {

    private final String deadToken;

    /** @param deadToken one device token that fails permanently */
    public PushSender(String deadToken) {
        if (deadToken == null || deadToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Dead token must be non-empty (pass a token to kill)");
        }
        this.deadToken = deadToken;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification) throws SendException {
        if (deadToken.equals(notification.getRecipient())) {
            // Permanent: the app was uninstalled; the token is stale.
            throw new SendException("Push token " + deadToken
                    + " is unregistered (PERMANENT - app uninstalled)", false);
        }
        System.out.println("    [PUSH delivered] " + notification.format());
    }
}
