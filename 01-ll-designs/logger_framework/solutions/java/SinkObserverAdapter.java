/**
 * Adapter that lets any LogSink act as a LogObserver.
 *
 * Why an adapter instead of making LogSink extend LogObserver? Because
 * the sink SPI should stay minimal (append/flush/name); forcing every
 * future sink to also know about observer lifecycle would couple the
 * output contract to the registration mechanism. The adapter keeps the
 * two roles orthogonal.
 */
public class SinkObserverAdapter implements LogObserver {

    private final LogSink sink;

    public SinkObserverAdapter(LogSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("Log sink cannot be null");
        }
        this.sink = sink;
    }

    public LogSink getSink() {
        return sink;
    }

    @Override
    public void onLog(LogMessage message) {
        if (message == null) {
            return; // defensive: never blow up the notify loop
        }
        try {
            sink.append(message);
        } catch (RuntimeException e) {
            // Sink isolation: one broken sink must not break the
            // observer loop for the others. A production build would
            // route this to an error sink / metrics counter.
            System.err.println("Sink " + sink.name() + " failed to append: " + e.getMessage());
        }
    }
}
