import java.util.ArrayList;
import java.util.List;

/**
 * In-memory observer bus. Subscribers attach once; every calendar event is
 * fanned out to all of them. Adding an email listener later is one new class
 * plus one subscribe() call - zero scheduler changes.
 */
public class NotificationService {
    private final List<NotificationListener> listeners = new ArrayList<>();

    public void subscribe(NotificationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Notification listener cannot be null");
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public boolean unsubscribe(NotificationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Notification listener cannot be null");
        }
        return listeners.remove(listener);
    }

    /** Broadcast one event to every listener. Isolation: one throwing
     *  listener must not starve the others. */
    public void notify(Notification event) {
        if (event == null) {
            throw new IllegalArgumentException("Notification cannot be null");
        }
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotification(event);
            } catch (RuntimeException e) {
                System.err.println("Listener " + listener.getClass().getSimpleName()
                    + " failed on " + event + ": " + e.getMessage());
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
