/**
 * Chain link for WARN. Passes through to ERROR unconditionally.
 */
public class WarnLogger extends Logger {

    public WarnLogger(LoggerManager manager) {
        super(LogLevel.WARN, manager);
    }
}
