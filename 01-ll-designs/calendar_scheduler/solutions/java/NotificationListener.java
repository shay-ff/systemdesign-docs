/**
 * Observer contract: anything that wants to hear about calendar events.
 *
 * An interface (not java.util.Observable) because Observable is deprecated
 * since Java 9 and is class-based - an interface keeps listeners open for
 * extension: email, SMS or webhook listeners are new classes, and the
 * scheduler never changes (OCP).
 */
public interface NotificationListener {
    void onNotification(Notification event);
}
