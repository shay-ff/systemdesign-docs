import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mock file sink. In production this would open a BufferedWriter and
 * worry about rotation; for the LLD we keep an in-memory line buffer so
 * the demo can show what "the file" received without touching the disk.
 *
 * Thread safety: the buffer is guarded by its own monitor so the async
 * worker and the demo thread can both append safely.
 */
public class FileSink implements LogSink {

    private final String fileName;
    private final List<String> lines;

    public FileSink(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        this.fileName = fileName.trim();
        this.lines = new ArrayList<>();
    }

    @Override
    public String name() {
        return "FILE:" + fileName;
    }

    @Override
    public synchronized void append(LogMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        lines.add(message.render());
    }

    @Override
    public synchronized void flush() {
        // No-op for the mock: the buffer is the "file".
    }

    /** Snapshot of the file contents (defensive copy). */
    public synchronized List<String> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public String getFileName() {
        return fileName;
    }
}
