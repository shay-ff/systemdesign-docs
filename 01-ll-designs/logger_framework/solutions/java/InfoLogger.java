/**
 * Chain link for INFO. Passes through to WARN unconditionally.
 */
public class InfoLogger extends Logger {

    public InfoLogger(LoggerManager manager) {
        super(LogLevel.INFO, manager);
    }
}
