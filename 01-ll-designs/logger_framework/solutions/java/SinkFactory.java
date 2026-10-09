import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that materializes sinks from a declarative spec.
 *
 * The config only says "FILE:app.log" / "DB:logs" / "CONSOLE"; the
 * factory knows the mapping from spec to class. Adding a new sink type
 * means adding one branch here and the new class -- existing callers
 * and the framework core stay untouched.
 */
public class SinkFactory {

    /** Spec format: "TYPE" or "TYPE:target" (e.g. FILE:app.log, DB:audit). */
    public LogSink create(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            throw new IllegalArgumentException("Sink spec cannot be null or empty (use CONSOLE, FILE:<name> or DB:<table>)");
        }
        String trimmed = spec.trim();
        int colon = trimmed.indexOf(':');
        String type = colon < 0 ? trimmed : trimmed.substring(0, colon).toUpperCase();
        String target = colon < 0 ? "" : trimmed.substring(colon + 1).trim();

        switch (type) {
            case "CONSOLE":
                return new ConsoleSink();
            case "FILE":
                if (target.isEmpty()) {
                    throw new IllegalArgumentException("FILE sink needs a file name, e.g. FILE:app.log");
                }
                return new FileSink(target);
            case "DB":
                if (target.isEmpty()) {
                    throw new IllegalArgumentException("DB sink needs a table name, e.g. DB:audit_logs");
                }
                return new DbSink(target);
            default:
                throw new IllegalArgumentException("Unknown sink type '" + type
                        + "' in spec '" + trimmed + "' (supported: CONSOLE, FILE:<name>, DB:<table>)");
        }
    }

    /** Convenience: build many sinks from many specs. */
    public List<LogSink> createAll(List<String> specs) {
        if (specs == null) {
            throw new IllegalArgumentException("Sink spec list cannot be null");
        }
        List<LogSink> sinks = new ArrayList<>();
        for (String spec : specs) {
            sinks.add(create(spec));
        }
        return sinks;
    }
}
