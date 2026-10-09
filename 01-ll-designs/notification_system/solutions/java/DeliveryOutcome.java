import java.util.List;

/**
 * A completed notification's delivery outcome: final status + attempt history
 * + a channel description line. The demo (and any caller) gets ONE object
 * that answers "what happened to NTF-1042?" - status, every attempt, when.
 *
 * Immutable for the same reason NotificationAttempt is: outcomes are history.
 */
public final class DeliveryOutcome {

    private final String notificationId;
    private final NotificationStatus status;
    private final List<NotificationAttempt> attempts;
    private final String detail;

    public DeliveryOutcome(String notificationId, NotificationStatus status,
                           List<NotificationAttempt> attempts, String detail) {
        if (notificationId == null || notificationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Notification id cannot be empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (attempts == null) {
            throw new IllegalArgumentException("Attempts list cannot be null (use empty)");
        }
        this.notificationId = notificationId;
        this.status = status;
        this.attempts = attempts;
        this.detail = detail == null ? "" : detail;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public List<NotificationAttempt> getAttempts() {
        return attempts;
    }

    public String getDetail() {
        return detail;
    }

    /** Compact one-line form for the demo's summary tables. */
    public String summaryLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %-13s attempts=%d", notificationId, status, attempts.size()));
        if (!detail.isEmpty()) {
            sb.append("  (").append(detail).append(")");
        }
        return sb.toString();
    }
}
