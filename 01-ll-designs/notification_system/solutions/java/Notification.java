import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A notification: one message, one channel, one recipient, one priority,
 * one identity.
 *
 * TEMPLATE METHOD: format() fixes the skeleton (header + body + footer with
 * channel-appropriate separators) while subclasses supply the channel's
 * body via formatBody(). Why template over, say, a format() per subclass
 * duplicating the header/footer? Because the outer structure is shared -
 * one change to the framing (say, adding a tenant tag) happens HERE once,
 * not three times.
 *
 * IDENTITY - THE KEY FIELD: notificationId is the dedupe key. At-least-once
 * delivery means the SAME notification can be delivered (or attempted) more
 * than once - network retries, worker crashes, upstream redeliveries. The
 * receiver (or our own sink) drops the second arrival by notificationId.
 * Without a stable id, dedupe is impossible; without dedupe, at-least-once
 * means "the user got the OTP message twice". Identity is a REQUIREMENT of
 * the delivery semantics, not an afterthought.
 *
 * attemptCount is an AtomicInteger because multiple worker threads never
 * touch the same notification concurrently in this design (each is polled
 * off the queue by exactly one worker), but the counter is also read by the
 * retry scheduler thread - cheap thread-safety, no downside.
 */
public abstract class Notification {

    private static final java.util.concurrent.atomic.AtomicLong ID_GEN =
            new java.util.concurrent.atomic.AtomicLong(1000);

    private String notificationId; // final-in-spirit: see overrideId()
    private final String recipient;
    private final String subject;
    private final String message;
    private final NotificationChannel channel;
    private final NotificationPriority priority;
    private final Instant createdAt;
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    protected Notification(String recipient, String subject, String message,
                           NotificationChannel channel, NotificationPriority priority) {
        if (recipient == null || recipient.trim().isEmpty()) {
            throw new IllegalArgumentException(
                channel + " recipient cannot be null or empty");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        // Auto-generated id; the demo also constructs "redelivered" copies
        // with an EXPLICIT id (via withId/overrideId) to simulate an
        // upstream at-least-once replay carrying the same identity.
        this.notificationId = "NTF-" + ID_GEN.incrementAndGet();
        this.recipient = recipient;
        this.subject = subject == null ? "" : subject;
        this.message = message;
        this.channel = channel;
        this.priority = priority;
        this.createdAt = Instant.now();
    }

    // ------------------------------------------------------ template method

    /**
     * The formatting skeleton. final so subclasses cannot skip the framing;
     * they only decide how the BODY reads.
     */
    public final String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(openSeparator()).append("\n");
        sb.append("To: ").append(recipient).append("\n");
        if (!subject.isEmpty()) {
            sb.append("Subject: ").append(subject).append("\n");
        }
        sb.append(formatBody());
        sb.append("\n").append(closeSeparator());
        return sb.toString();
    }

    /** Channel-specific body (each subclass renders its own). */
    protected abstract String formatBody();

    /** Channel-specific framing (e.g. email ---- lines vs push [ ] tags). */
    protected String openSeparator() {
        return "---- " + channel + " notification ----";
    }

    protected String closeSeparator() {
        return "--------------------------------";
    }

    // ------------------------------------------------------------ accessors

    public String getNotificationId() {
        return notificationId;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int incrementAttempt() {
        return attemptCount.incrementAndGet();
    }

    public int getAttemptCount() {
        return attemptCount.get();
    }

    /**
     * A copy of this notification carrying an EXPLICIT id - the seam the
     * demo uses to simulate an upstream at-least-once replay (the same
     * message redelivered with the same id). Concrete classes implement the
     * copying via withId(); the base guarantees the id swap through this
     * guarded method. Keeping this on the base class (rather than the demo
     * reaching into fields) preserves encapsulation: only a notification
     * can forge its own identity, and only before it is ever enqueued.
     */
    public Notification overrideId(String explicitNotificationId) {
        if (explicitNotificationId == null || explicitNotificationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Explicit notification id cannot be empty");
        }
        if (!this.notificationId.equals(explicitNotificationId)
                && this.attemptCount.get() != 0) {
            // Guard: an id may only be forged on a FRESH copy - never on a
            // notification that has already been attempted/delivered. This
            // keeps ids stable once delivery begins (audit integrity).
            throw new IllegalStateException("Cannot change id after delivery attempts began: "
                    + this.notificationId + " -> " + explicitNotificationId);
        }
        this.notificationId = explicitNotificationId;
        return this;
    }

    /** Copy-with-id hook; subclasses build their own type. */
    public abstract Notification withId(String explicitNotificationId);

    @Override
    public String toString() {
        return "[" + notificationId + " " + priority + " " + channel
                + " -> " + recipient + "]";
    }
}
