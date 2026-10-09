/**
 * Push variant: title + body + a fake device token check. Push framing is
 * visually different (tagged), which shows the template's open/close hooks
 * earning their keep.
 */
public class PushNotification extends Notification {

    public PushNotification(String recipient, String subject, String message,
                            NotificationPriority priority) {
        super(recipient, subject, message, NotificationChannel.PUSH, priority);
        if (recipient == null || !recipient.startsWith("device:")) {
            throw new IllegalArgumentException(
                "Push recipient must be a device token starting with 'device:', got '"
                        + recipient + "'");
        }
    }

    @Override
    protected String formatBody() {
        // Push renders the subject as the alert title.
        return "Title: " + (getSubject().isEmpty() ? "(untitled)" : getSubject())
                + "\nAlert: " + getMessage();
    }

    @Override
    protected String openSeparator() {
        return "<<<< " + getChannel() + " alert >>>>";
    }

    @Override
    protected String closeSeparator() {
        return "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<";
    }

    @Override
    public Notification withId(String explicitNotificationId) {
        PushNotification copy = new PushNotification(
                getRecipient(), getSubject(), getMessage(), getPriority());
        return copy.overrideId(explicitNotificationId);
    }
}
