/**
 * Sink abstraction: where a log message physically goes.
 *
 * Any new output (Kafka topic, syslog, Elastic) is a new implementation
 * of this interface plus one line in SinkFactory -- the core framework
 * never changes. That is the Open/Closed principle made concrete.
 */
public interface LogSink {

    /** Human-readable sink name, used by the factory and demo output. */
    String name();

    /**
     * Append one enriched message. Implementations must swallow their own
     * exceptions (or translate them) -- a failing sink must never break
     * the chain or starve other sinks.
     */
    void append(LogMessage message);

    /** Flush any buffered state. Called on shutdown. */
    void flush();
}
