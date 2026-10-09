import java.time.Instant;

/**
 * Strategy for decorating a raw message with metadata before dispatch.
 *
 * The framework core only knows "give me a LogMessage for this text";
 * where the metadata comes from is pluggable. The default
 * implementation captures wall-clock time, the publishing thread's
 * name, and the caller class passed explicitly by the logging API.
 *
 * Production alternative (documented trade-off): derive the caller via
 * StackWalker -- zero API noise, but 10-100x the cost of the log call
 * itself, which is why real frameworks use caller-id tricks or make
 * the logger instance carry the class name.
 */
public class MessageEnricher {

    /** Enrich a raw (level, caller, text) triple into an immutable record. */
    public LogMessage enrich(LogLevel level, String caller, String text) {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        if (caller == null || caller.trim().isEmpty()) {
            throw new IllegalArgumentException("Caller class name cannot be null or empty");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Log text cannot be null or empty");
        }
        return new LogMessage(level, text.trim(), Instant.now(),
                Thread.currentThread().getName(), caller.trim());
    }

    /**
     * Variant that captures the caller by walking the current stack.
     * Kept for the demo/discussion: convenient but expensive, so the
     * public API prefers the explicit-caller path.
     */
    public LogMessage enrichWithStackCapture(LogLevel level, String text) {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Log text cannot be null or empty");
        }
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        String caller = walker.walk(frames -> frames
                .skip(2) // skip enrichWithStackCapture + its caller (LoggerManager)
                .map(f -> f.getDeclaringClass().getSimpleName())
                .findFirst()
                .orElse("UNKNOWN"));
        return new LogMessage(level, text.trim(), Instant.now(),
                Thread.currentThread().getName(), caller);
    }
}
