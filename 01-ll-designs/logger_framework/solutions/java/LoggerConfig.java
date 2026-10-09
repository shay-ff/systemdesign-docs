import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of logger configuration:
 *   - the minimum enabled level (global filter),
 *   - which sinks receive messages of each level,
 *   - whether appends happen synchronously or via the async worker.
 *
 * Immutability: the LoggerManager hot path reads this object from many
 * threads, so it must be safely publishable without locks. Runtime
 * sink changes go through LoggerManager.reconfigure(), which swaps in
 * a whole new snapshot -- copy-on-write.
 */
public final class LoggerConfig {

    private final LogLevel minimumLevel;
    private final Map<LogLevel, List<LogSink>> sinksByLevel;
    private final boolean asyncMode;

    private LoggerConfig(LogLevel minimumLevel,
                         Map<LogLevel, List<LogSink>> sinksByLevel,
                         boolean asyncMode) {
        this.minimumLevel = minimumLevel;
        this.sinksByLevel = sinksByLevel;
        this.asyncMode = asyncMode;
    }

    /** Builder-style entry point. */
    public static Builder builder(LogLevel minimumLevel) {
        return new Builder(minimumLevel);
    }

    public LogLevel getMinimumLevel() { return minimumLevel; }

    public boolean isAsyncMode() { return asyncMode; }

    /** Sinks registered for a level; empty list means the level goes nowhere. */
    public List<LogSink> sinksFor(LogLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        List<LogSink> sinks = sinksByLevel.get(level);
        return sinks == null ? Collections.<LogSink>emptyList() : sinks;
    }

    /** All sinks across all levels, deduplicated, in registration order. */
    public List<LogSink> allSinks() {
        List<LogSink> all = new ArrayList<>();
        Set<LogSink> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<LogSink, Boolean>());
        for (LogLevel level : LogLevel.values()) {
            for (LogSink sink : sinksFor(level)) {
                if (seen.add(sink)) {
                    all.add(sink);
                }
            }
        }
        return all;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LoggerConfig{min=").append(minimumLevel)
          .append(", async=").append(asyncMode).append(", sinks={");
        for (LogLevel level : LogLevel.values()) {
            List<LogSink> sinks = sinksFor(level);
            if (!sinks.isEmpty()) {
                sb.append(level).append("->[");
                for (int i = 0; i < sinks.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(sinks.get(i).name());
                }
                sb.append("] ");
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    /**
     * Mutable builder used only during (re)configuration; the product is
     * an immutable snapshot.
     */
    public static final class Builder {
        private final LogLevel minimumLevel;
        private final Map<LogLevel, List<LogSink>> sinksByLevel =
                new EnumMap<>(LogLevel.class);
        private boolean asyncMode = false;

        Builder(LogLevel minimumLevel) {
            if (minimumLevel == null) {
                throw new IllegalArgumentException("Minimum log level cannot be null");
            }
            this.minimumLevel = minimumLevel;
        }

        public Builder addSink(LogLevel level, LogSink sink) {
            if (level == null) {
                throw new IllegalArgumentException("Log level cannot be null");
            }
            if (sink == null) {
                throw new IllegalArgumentException("Log sink cannot be null");
            }
            List<LogSink> sinks = sinksByLevel.get(level);
            if (sinks == null) {
                sinks = new ArrayList<>();
                sinksByLevel.put(level, sinks);
            }
            if (!sinks.contains(sink)) {
                sinks.add(sink);
            }
            return this;
        }

        public Builder async(boolean async) {
            this.asyncMode = async;
            return this;
        }

        public LoggerConfig build() {
            Map<LogLevel, List<LogSink>> copy = new EnumMap<>(LogLevel.class);
            for (Map.Entry<LogLevel, List<LogSink>> e : sinksByLevel.entrySet()) {
                copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            }
            return new LoggerConfig(minimumLevel, Collections.unmodifiableMap(copy), asyncMode);
        }
    }
}
