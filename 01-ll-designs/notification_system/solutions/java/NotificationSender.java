/**
 * Strategy interface: ONE channel's delivery mechanism.
 *
 * WHY a sender interface per channel instead of if (channel == EMAIL) in
 * the dispatcher: each channel is a different vendor integration (SMTP /
 * an SMS gateway like a Telnyx-class API / FCM-style push) with different
 * failure modes (SMTP greylists transiently; SMS gateways throttle by
 * destination country; push tokens go stale). The dispatcher must not know
 * any of that - it knows "send this, tell me what happened". Swapping
 * vendors = new implementation, zero dispatcher changes (OCP).
 *
 * CHECKED exception SendException: a failed send is an EXPECTED business
 * outcome (transient outage, throttle) that the retry policy consumes -
 * not a bug for the catch-and-log path.
 */
public interface NotificationSender {

    /** The channel this sender handles. */
    NotificationChannel getChannel();

    /**
     * Sends one formatted notification. Throws SendException on failure;
     * the retry policy decides whether that failure is retryable (it always
     * is in this design - these are transient mock failures).
     */
    void send(Notification notification) throws SendException;
}
