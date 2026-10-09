/**
 * Demo listener: prints every event to stdout so the observer flow is visible
 * in the run log. An EmailNotificationListener or SmsNotificationListener
 * would implement the same interface with no scheduler changes.
 */
public class ConsoleNotificationListener implements NotificationListener {

    @Override
    public void onNotification(Notification event) {
        System.out.println("    >> notification: " + event);
    }
}
