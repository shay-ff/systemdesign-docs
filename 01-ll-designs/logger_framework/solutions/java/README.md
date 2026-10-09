# Logger Framework — Java Implementation

Java 11, no external libraries, no `package` declarations (repo convention: one
top-level class per file, compiled side by side).

## Design Patterns

- **Chain of Responsibility** — `Logger` → `DebugLogger` → `InfoLogger` →
  `WarnLogger` → `ErrorLogger`. Each link owns one level, publishes if it
  matches, and always passes the message to the next link. Adding a level is
  inserting one link; no `switch` anywhere.
- **Observer** — `LoggerManager` is the subject; `LogObserver` is the
  subscriber. Sinks attach/detach at runtime via `attachSink`/`detachSink`.
- **Adapter** — `SinkObserverAdapter` lets a minimal `LogSink` act as a
  `LogObserver` without coupling the two SPIs.
- **Factory** — `SinkFactory` turns config specs (`"FILE:app.log"`) into sink
  instances; new sinks plug in with one class and one branch.
- **Strategy** — `MessageEnricher` (metadata capture) and
  `AsyncAppender.OverflowPolicy` (backpressure behaviour) are swappable.

## Class-by-Class

| File | Class / Type | Responsibility |
|---|---|---|
| `LogLevel.java` | `LogLevel` (enum) | DEBUG/INFO/WARN/ERROR with numeric severity; O(1) `isEnabled` filter. Severity is data on the constant, not ordinal position |
| `LogMessage.java` | `LogMessage` | Immutable record (level, text, timestamp, thread, caller); `render()` is the single line-format definition |
| `MessageEnricher.java` | `MessageEnricher` | Builds a `LogMessage` from a raw triple; also ships `enrichWithStackCapture` (documented expensive alternative) |
| `Logger.java` | `Logger` (abstract) | Chain link base: `handle()` template method, `matchesLevel()`, `publish()`, volatile `next` |
| `DebugLogger.java` | `DebugLogger` | Chain link for DEBUG |
| `InfoLogger.java` | `InfoLogger` | Chain link for INFO |
| `WarnLogger.java` | `WarnLogger` | Chain link for WARN |
| `ErrorLogger.java` | `ErrorLogger` | Chain link for ERROR |
| `LogSink.java` | `LogSink` (interface) | Output SPI: `name()`, `append()`, `flush()` |
| `ConsoleSink.java` | `ConsoleSink` | Writes rendered lines to stdout (synchronized) |
| `FileSink.java` | `FileSink` | Mock file sink; in-memory line buffer, inspectable via `getLines()` |
| `DbSink.java` | `DbSink` | Mock DB sink; records fake `INSERT` strings, inspectable via `getInsertedRows()` |
| `SinkFactory.java` | `SinkFactory` | Materializes sinks from specs (`CONSOLE`, `FILE:<name>`, `DB:<table>`) |
| `LogObserver.java` | `LogObserver` (interface) | Observer contract: `onLog(message)` |
| `SinkObserverAdapter.java` | `SinkObserverAdapter` | Adapts a `LogSink` to `LogObserver`; isolates sink exceptions |
| `LoggerConfig.java` | `LoggerConfig` (+ `Builder`) | Immutable config snapshot: min level, per-level sinks, async flag |
| `LoggerManager.java` | `LoggerManager` | Facade + observer subject + chain owner; the log pipeline |
| `AsyncAppender.java` | `AsyncAppender` (+ `OverflowPolicy`) | Single-worker async engine with bounded queue + drop-oldest |
| `LoggerDemo.java` | `LoggerDemo` | Seven-section narrative demo |

## Run

```bash
cd solutions/java

# Java 22+ single-file source launcher (handles sibling classes):
java LoggerDemo.java

# Java 11+ classic:
javac *.java && java LoggerDemo
```

## Demo Sections

1. **Configure sinks** — `SinkFactory` + `LoggerConfig`: DEBUG→console,
   INFO→console, WARN→console+file, ERROR→console+file+DB; chain printed as
   `DEBUG -> INFO -> WARN -> ERROR`.
2. **Synchronous logging** — one message per level walks the chain; only the
   routed sinks receive each line.
3. **Level filtering** — minimum raised to WARN; DEBUG/INFO suppressed and
   counted (`getSuppressedCount()`).
4. **Dynamic sink attach/detach** — an `alerts.log` sink is attached at
   runtime and starts receiving; the DB sink is detached and stops receiving.
5. **Async mode** — callers submit from multiple threads and return
   immediately; the single worker drains the queue preserving per-sink order.
6. **Backpressure** — a capacity-4 queue with a blocked worker; submissions
   beyond capacity are dropped under the DROP_OLDEST policy and counted.
7. **Shutdown and final state** — published/suppressed counters, file line
   count, DB row count.

Total runtime under 3 seconds; no external dependencies, no disk I/O (the file
and DB sinks are mocks).
