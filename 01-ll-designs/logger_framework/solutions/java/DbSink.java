import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mock database sink. Simulates an inserts table; in production this
 * would be a JDBC batch insert with its own retry logic. Kept as a mock
 * so the framework compiles with zero external dependencies.
 */
public class DbSink implements LogSink {

    private final String tableName;
    private final List<String> insertedRows;

    public DbSink(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        this.tableName = tableName.trim();
        this.insertedRows = new ArrayList<>();
    }

    @Override
    public String name() {
        return "DB:" + tableName;
    }

    @Override
    public synchronized void append(LogMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        // Mock "INSERT INTO logs VALUES (...)" -- recorded as a string row.
        insertedRows.add(String.format("INSERT INTO %s (%d, '%s', '%s')",
                tableName, message.getTimestamp().toEpochMilli(),
                message.getLevel(), message.getText()));
    }

    @Override
    public synchronized void flush() {
        // Real impl would execute a batch commit here.
    }

    public synchronized List<String> getInsertedRows() {
        return Collections.unmodifiableList(new ArrayList<>(insertedRows));
    }
}
