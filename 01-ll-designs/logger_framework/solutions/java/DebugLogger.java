/**
 * Chain link for DEBUG. Passes through to INFO unconditionally.
 */
public class DebugLogger extends Logger {

    public DebugLogger(LoggerManager manager) {
        super(LogLevel.DEBUG, manager);
    }
}
