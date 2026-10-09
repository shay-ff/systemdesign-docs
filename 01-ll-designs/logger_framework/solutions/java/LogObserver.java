/**
 * Observer interface: an object that wants to be notified whenever the
 * framework publishes a log message.
 *
 * The concrete sink observers (ConsoleSink etc. implement this via the
 * LogObserverAdapter) can attach/detach at runtime, which is what makes
 * "dynamically add a sink" a one-liner for callers.
 */
public interface LogObserver {
    void onLog(LogMessage message);
}
