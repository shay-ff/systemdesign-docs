/**
 * SMS variant: 160-character world, no subject, terse body. The template's
 * subject framing is skipped because the base class only prints Subject when
 * non-empty - SMS passes an empty subject.
 */
public class SmsNotification extends Notification {

    public SmsNotification(String recipient, String message,
                           NotificationPriority priority) {
        // SMS has no subject line - pass empty and the template skips it.
        super(recipient, "", message, NotificationChannel.SMS, priority);
        if (recipient == null || !recipient.matches("[0-9]{10,13}")) {
            throw new IllegalArgumentException(
                "SMS recipient must be a 10-13 digit phone number, got '"
                        + recipient + "'");
        }
    }

    @Override
    protected String formatBody() {
        // SMS bodies get truncated hard at 160 chars - carrier reality.
        String body = getMessage();
        return body.length() <= 160 ? body : body.substring(0, 157) + "...";
    }

    @Override
    public Notification withId(String explicitNotificationId) {
        SmsNotification copy = new SmsNotification(
                getRecipient(), getMessage(), getPriority());
        return copy.overrideId(explicitNotificationId);
    }
}
