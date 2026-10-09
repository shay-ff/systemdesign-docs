import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Log levels with numeric severity for O(1) comparisons.
 *
 * Design note: severity is stored on the enum so filtering is a single
 * integer comparison instead of an indexOf() on a list. Adding a new
 * level (TRACE, FATAL) means adding one enum constant and one chain
 * link -- no core code changes (Open/Closed Principle).
 */
public enum LogLevel {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * @return true if a message of this level should be emitted,
     *         given the configured minimum level.
     */
    public boolean isEnabled(LogLevel minimum) {
        if (minimum == null) {
            throw new IllegalArgumentException("Minimum log level cannot be null");
        }
        return this.severity >= minimum.severity;
    }
}
