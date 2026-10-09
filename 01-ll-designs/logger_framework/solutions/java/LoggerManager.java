import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The facade of the framework -- what application code actually touches.
 *
 * Two pattern roles live here:
 *
 *  1. OBSERVER SUBJECT: sinks register as LogObservers; every published
 *     message is broadcast to the relevant observers. Registration uses
 *     a CopyOnWriteArrayList so observers can attach/detach at runtime
 *     without locking the hot path (iterate a snapshot).
 *
 *  2. CHAIN OWNER: the manager builds and owns the
 *     DEBUG -> INFO -> WARN -> ERROR chain once, then routes every
 *     message through its head. The chain -- not the manager -- decides
 *     which levels see the message.
 *
 * Thread safety strategy:
 *  - config is an immutable snapshot swapped atomically (copy-on-write)
 *  - observers live in a lock-free CopyOnWriteArrayList
 *  - chain links are built once and their next pointers are volatile
 *  - the async worker serializes all sink appends in async mode
 */
public class LoggerManager {

    private final MessageEnricher enricher;
    private final CopyOnWriteArrayList<LogObserver> observers;
    private final AtomicLong publishedCount;
    private final AtomicLong suppressedCount;
    private volatile LoggerConfig config;
    private volatile Logger chainHead;
    private volatile AsyncAppender asyncAppender;

    public LoggerManager(LoggerConfig initialConfig) {
        if (initialConfig == null) {
            throw new IllegalArgumentException("Initial LoggerConfig cannot be null");
        }
        this.enricher = new MessageEnricher();
        this.observers = new CopyOnWriteArrayList<>();
        this.publishedCount = new AtomicLong();
        this.suppressedCount = new AtomicLong();
        this.config = initialConfig;
        this.chainHead = buildChain(this);
        this.asyncAppender = initialConfig.isAsyncMode() ? new AsyncAppender() : null;
    }

    /** Build the DEBUG -> INFO -> WARN -> ERROR chain. */
    private static Logger buildChain(LoggerManager manager) {
        Logger debug = new DebugLogger(manager);
        Logger info = new InfoLogger(manager);
        Logger warn = new WarnLogger(manager);
        Logger error = new ErrorLogger(manager);
        debug.setNext(info);
        info.setNext(warn);
        warn.setNext(error);
        return debug;
    }

    // ------------------------------------------------------------------
    // Public logging API (the facade)
    // ------------------------------------------------------------------

    public void debug(String caller, String text) { log(LogLevel.DEBUG, caller, text); }
    public void info(String caller, String text)  { log(LogLevel.INFO, caller, text); }
    public void warn(String caller, String text)  { log(LogLevel.WARN, caller, text); }
    public void error(String caller, String text) { log(LogLevel.ERROR, caller, text); }

    /**
     * Core pipeline: filter -> enrich -> route through the chain ->
     * deliver to sinks (sync) or to the async worker (async).
     */
    public void log(LogLevel level, String caller, String text) {
        if (level == null) {
            throw new IllegalArgumentException("Log level cannot be null");
        }
        if (caller == null || caller.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Caller class name cannot be null or empty (pass the logging class, e.g. getClass().getSimpleName())");
        }
        if (text == null) {
            throw new IllegalArgumentException("Log text cannot be null (empty is rejected at message build)");
        }

        // 1. Filter FIRST: never build/enrich a message that will be dropped.
        if (!level.isEnabled(config.getMinimumLevel())) {
            suppressedCount.incrementAndGet();
            return;
        }

        // 2. Enrich (timestamp, thread, caller) into an immutable record.
        LogMessage message = enricher.enrich(level, caller, text);
        publishedCount.incrementAndGet();

        // 3. Route through the chain of responsibility.
        // 4. Deliver per mode.
        if (config.isAsyncMode()) {
            AsyncAppender appender = this.asyncAppender;
            if (appender != null) {
                appender.submit(() -> chainHead.handle(message));
            } else {
                chainHead.handle(message);
            }
        } else {
            chainHead.handle(message);
        }
    }

    // ------------------------------------------------------------------
    // Observer registration (runtime pluggability)
    // ------------------------------------------------------------------

    /** Attach a sink at runtime; it becomes a live observer immediately. */
    public void attachSink(LogSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("Cannot attach a null sink");
        }
        observers.addIfAbsent(new SinkObserverAdapter(sink));
        // Also register the sink for every level that already has sinks,
        // mirroring the most common intent: "I want this sink to see
        // what the framework is currently routing."
        LoggerConfig current = config;
        LoggerConfig.Builder builder = LoggerConfig.builder(current.getMinimumLevel())
                .async(current.isAsyncMode());
        for (LogLevel level : LogLevel.values()) {
            for (LogSink existing : current.sinksFor(level)) {
                builder.addSink(level, existing);
            }
            builder.addSink(level, sink);
        }
        this.config = builder.build();
    }

    /** Detach a previously attached sink observer. */
    public boolean detachSink(LogSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("Cannot detach a null sink");
        }
        boolean removedObserver = observers.removeIf(o ->
                o instanceof SinkObserverAdapter && ((SinkObserverAdapter) o).getSink() == sink);
        boolean removedFromConfig = false;
        LoggerConfig current = config;
        LoggerConfig.Builder builder = LoggerConfig.builder(current.getMinimumLevel())
                .async(current.isAsyncMode());
        for (LogLevel level : LogLevel.values()) {
            for (LogSink existing : current.sinksFor(level)) {
                if (existing != sink) {
                    builder.addSink(level, existing);
                } else {
                    removedFromConfig = true;
                }
            }
        }
        this.config = builder.build();
        return removedObserver || removedFromConfig;
    }

    /**
     * Notify one sink-as-observer about a message. Called by chain links.
     * Iteration safety: the observer list is copy-on-write; sinks are
     * matched by identity and invoked with full isolation (see adapter).
     */
    public void notifyObservers(LogSink targetSink, LogMessage message) {
        if (targetSink == null || message == null) {
            return;
        }
        // Fast path: if this sink has a registered observer adapter, use
        // it (keeps the adapter's error isolation). Otherwise call the
        // sink directly under try/catch.
        for (LogObserver observer : observers) {
            if (observer instanceof SinkObserverAdapter
                    && ((SinkObserverAdapter) observer).getSink() == targetSink) {
                observer.onLog(message);
                return;
            }
        }
        try {
            targetSink.append(message);
        } catch (RuntimeException e) {
            System.err.println("Sink " + targetSink.name() + " failed to append: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Configuration and lifecycle
    // ------------------------------------------------------------------

    /** Atomically swap in a new configuration snapshot. */
    public void reconfigure(LoggerConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("New LoggerConfig cannot be null");
        }
        boolean wasAsync = this.config.isAsyncMode();
        this.config = newConfig;
        if (newConfig.isAsyncMode() && !wasAsync) {
            this.asyncAppender = new AsyncAppender();
        } else if (!newConfig.isAsyncMode() && wasAsync) {
            AsyncAppender old = this.asyncAppender;
            this.asyncAppender = null;
            if (old != null) {
                old.shutdown();
            }
        }
    }

    /** Graceful shutdown: flush the async worker if running. */
    public void shutdown() {
        AsyncAppender appender = this.asyncAppender;
        if (appender != null) {
            appender.shutdown();
        }
        for (LogSink sink : config.allSinks()) {
            try {
                sink.flush();
            } catch (RuntimeException ignored) {
                // Shutdown must never throw on a misbehaving sink.
            }
        }
    }

    // ------------------------------------------------------------------
    // Introspection (used by the demo and by metrics)
    // ------------------------------------------------------------------

    public LoggerConfig config() {
        return config;
    }

    public Logger getChainHead() {
        return chainHead;
    }

    public long getPublishedCount() { return publishedCount.get(); }
    public long getSuppressedCount() { return suppressedCount.get(); }

    public long getAsyncDroppedCount() {
        AsyncAppender appender = this.asyncAppender;
        return appender == null ? 0L : appender.getDroppedCount();
    }

    /** Render the chain for narrative output: DEBUG -> INFO -> WARN -> ERROR. */
    public String describeChain() {
        StringBuilder sb = new StringBuilder();
        Logger link = chainHead;
        while (link != null) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(link.getHandlerLevel());
            link = link.getNext();
        }
        return sb.toString();
    }
}
