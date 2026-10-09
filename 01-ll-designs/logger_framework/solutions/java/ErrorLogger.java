/**
 * Chain link for ERROR -- the tail of the chain.
 */
public class ErrorLogger extends Logger {

    public ErrorLogger(LoggerManager manager) {
        super(LogLevel.ERROR, manager);
    }
}
