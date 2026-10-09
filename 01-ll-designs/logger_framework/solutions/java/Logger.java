import java.util.List;

/**
 * One link in the Chain of Responsibility.
 *
 * Each concrete logger owns exactly one level. The flow for every link:
 *   1. "Is this my level?" -- if yes, dispatch to this level's sinks
 *      (through the manager's observer notification).
 *   2. Either way, hand the message to the next link in the chain.
 *
 * Why a chain here instead of a switch? Adding TRACE means inserting a
 * TraceLogger link -- existing classes are untouched (OCP). Why pass
 * through instead of stop? Because the levels are a *filtering* chain,
 * not a "first handler wins" chain: the message may be of interest to
 * several handlers (e.g. WARN and above share sinks), and the design
 * deliberately lets every link look at the message.
 *
 * Template method: handle() fixes the sequence; only matchesLevel() and
 * publish() vary per subclass.
 */
public abstract class Logger {

    protected final LogLevel handlerLevel;
    private final LoggerManager manager;
    /** Next link, or null for the tail. Volatile for safe runtime rewiring. */
    private volatile Logger next;

    protected Logger(LogLevel handlerLevel, LoggerManager manager) {
        if (handlerLevel == null) {
            throw new IllegalArgumentException("Handler level cannot be null");
        }
        if (manager == null) {
            throw new IllegalArgumentException("LoggerManager cannot be null");
        }
        this.handlerLevel = handlerLevel;
        this.manager = manager;
    }

    /** Chain entry point used by the manager. */
    public final void handle(LogMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Log message cannot be null");
        }
        if (matchesLevel(message.getLevel())) {
            publish(message);
        }
        Logger tail = this.next;
        if (tail != null) {
            tail.handle(message);
        }
    }

    /** Does this link own the given message level? */
    protected boolean matchesLevel(LogLevel level) {
        return level == handlerLevel;
    }

    /** Deliver the message to this level's registered observers. */
    protected void publish(LogMessage message) {
        List<LogSink> sinks = manager.config().sinksFor(handlerLevel);
        for (LogSink sink : sinks) {
            manager.notifyObservers(sink, message);
        }
    }

    /** Wire the next link; returns that link for fluent building. */
    public Logger setNext(Logger nextLogger) {
        this.next = nextLogger;
        return nextLogger;
    }

    public Logger getNext() {
        return next;
    }

    public LogLevel getHandlerLevel() {
        return handlerLevel;
    }
}
