/**
 * Email variant: the richest formatting - subject line + paragraphs. The
 * template supplies the To/Subject framing; this class only writes the body.
 */
public class EmailNotification extends Notification {

    public EmailNotification(String recipient, String subject, String message,
                              NotificationPriority priority) {
        super(recipient, subject, message, NotificationChannel.EMAIL, priority);
        if (recipient == null || !recipient.contains("@")) {
            throw new IllegalArgumentException(
                "Email recipient must contain '@', got '" + recipient + "'");
        }
    }

    @Override
    protected String formatBody() {
        // Emails carry the full message with a signature footer.
        return getMessage() + "\n\n--\nRazorpay Notifications (no-reply)";
    }

    @Override
    public Notification withId(String explicitNotificationId) {
        EmailNotification copy = new EmailNotification(
                getRecipient(), getSubject(), getMessage(), getPriority());
        return copy.overrideId(explicitNotificationId);
    }
}
