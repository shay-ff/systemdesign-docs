/**
 * Immutable event pushed to every registered NotificationListener.
 *
 * Production would add a recipient list, delivery retries and idempotency
 * keys; for this LLD the observer bus is deliberately minimal (see
 * explanation.md - the queue/retry story is the documented production gap).
 */
public final class Notification {
    private final NotificationKind kind;
    private final String meetingId;
    private final String message;

    public Notification(NotificationKind kind, String meetingId, String message) {
        if (kind == null) {
            throw new IllegalArgumentException("Notification kind cannot be null");
        }
        if (meetingId == null || meetingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Notification meeting id cannot be null or empty");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Notification message cannot be null or empty");
        }
        this.kind = kind;
        this.meetingId = meetingId;
        this.message = message.trim();
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "[" + kind + " " + meetingId + "] " + message;
    }
}
