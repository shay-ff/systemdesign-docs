/**
 * Writes log lines to standard output.
 *
 * Synchronized because System.out is itself a shared resource and the
 * async worker thread will call append() concurrently with other threads.
 */
public class ConsoleSink implements LogSink {

    private final Object printLock = new Object();

    @Override
    public String name() {
        return "CONSOLE";
    }

    @Override
    public void append(LogMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        synchronized (printLock) {
            System.out.println(message.render());
        }
    }

    @Override
    public void flush() {
        System.out.flush();
    }
}
