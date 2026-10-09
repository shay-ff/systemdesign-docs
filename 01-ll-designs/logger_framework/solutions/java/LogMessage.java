import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Immutable value object representing one enriched log record.
 *
 * Immutability matters here: the message is created once on the caller
 * thread and then handed to (possibly different) observer sinks and to
 * the async worker -- no defensive copies, no visibility hazards.
 */
public final class LogMessage {
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final LogLevel level;
    private final String text;
    private final Instant timestamp;
    private final String threadName;
    private final String caller;

    public LogMessage(LogLevel level, String text, Instant timestamp,
                      String threadName, String caller) {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Log message text cannot be null or empty");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        if (threadName == null || threadName.trim().isEmpty()) {
            throw new IllegalArgumentException("Thread name cannot be null or empty");
        }
        if (caller == null || caller.trim().isEmpty()) {
            throw new IllegalArgumentException("Caller class name cannot be null or empty");
        }
        this.level = level;
        this.text = text;
        this.timestamp = timestamp;
        this.threadName = threadName;
        this.caller = caller;
    }

    public LogLevel getLevel() { return level; }
    public String getText() { return text; }
    public Instant getTimestamp() { return timestamp; }
    public String getThreadName() { return threadName; }
    public String getCaller() { return caller; }

    /** ISO-8601 time part, e.g. 14:03:22.115 -- cheap enough for a demo. */
    public String formattedTimestamp() {
        return TS_FORMAT.format(timestamp);
    }

    /** Single place that defines the on-the-wire line format. */
    public String render() {
        return String.format("[%s] [%s] [%s] [%s] %s",
                formattedTimestamp(), level, threadName, caller, text);
    }

    @Override
    public String toString() {
        return render();
    }
}
