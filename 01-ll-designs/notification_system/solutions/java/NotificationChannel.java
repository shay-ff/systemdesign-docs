/**
 * Delivery channels. An enum because channels are DATA (a routing key), not
 * behaviour: the behaviour differences live in the sender classes, one per
 * channel. Adding a channel = enum constant + one sender class + one map
 * entry in the dispatcher config - nothing else changes.
 */
public enum NotificationChannel {
    EMAIL("Email"),
    SMS("SMS"),
    PUSH("Push");

    private final String displayName;

    NotificationChannel(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
