# Logger Framework — Low Level Design

A pluggable, extensible logging framework in the spirit of Log4j/SLF4J: multiple log levels, multiple output sinks (console, file, DB mock), message enrichment, and an optional asynchronous append mode with backpressure. This is the canonical Chain of Responsibility + Observer showcase problem — asked at Flipkart, Uber, Microsoft, and most Indian product companies.

## Problem Statement

Design a logger framework that:

1. Supports log levels — DEBUG, INFO, WARN, ERROR — with level-based filtering (a logger set to INFO should not emit DEBUG).
2. Writes each log message to one or more **sinks** (Console, File, Database mock) — and the set of sinks can differ per level (e.g. everything to console, only ERROR to the DB sink).
3. Enriches each message with metadata — timestamp, thread name, caller class name — before it reaches a sink.
4. Supports an **asynchronous mode**: callers return immediately, a background worker appends messages.
5. Allows new sinks and new levels to be added **without touching existing code** (OCP).
6. Allows a sink to be attached/detached at runtime.

## Functional Requirements

| # | Requirement |
|---|-------------|
| F1 | `log(level, message)` API; level filtering against a configurable minimum level |
| F2 | Per-level sink routing: each `LogLevel` maps to a set of `LogSink` instances |
| F3 | Message enrichment: timestamp, thread name, caller class (explicit caller param — see clarifying questions) |
| F4 | Async append mode via a single background worker thread |
| F5 | Dynamic sink add/remove at runtime (Observer registration) |
| F6 | Sink configuration via a programmatic config object (no files) |

## Non-Functional Requirements

- **Extensibility (OCP)**: a new sink (e.g. `SyslogSink`, `KafkaSink`) or new level (e.g. `TRACE`, `FATAL`) requires zero changes to `LoggerManager` or existing sinks.
- **Thread safety**: multiple application threads may log concurrently.
- **Bounded memory**: async mode must not buffer unboundedly — a bounded queue with an explicit overflow policy (drop-oldest).
- **Low latency in sync mode**: filtering happens before any formatting work (don't build strings for messages that will be discarded).
- **No busy-waiting**: async worker parks when idle.

## Clarifying Questions an Interviewer Expects You to Ask

Asking these is part of the evaluation — they map directly to design decisions:

1. **"Who decides whether a message is logged — the level, the sink, or both?"** → A per-logger minimum level (set-level filtering) plus per-level sink routing. Both are needed: the level gates *whether* we log; the config decides *where* it goes.
2. **"Can one message go to multiple sinks?"** → Yes. ERROR might go to console + file + DB; DEBUG only to console. This is why a level maps to a *set* of sinks, not one.
3. **"How do we capture the caller class name — stack walking?"** → In production, `StackWalker` (Java 9+) or the log4j trick of `new Throwable().getStackTrace()`, but both are expensive. We take an explicit `String caller` parameter and document the trade-off (interviewers love this nuance).
4. **"What happens if the async queue fills up?"** → Explicit policy: drop-oldest (newest messages win, which is right for error diagnosis). Alternatives: drop-newest, block the caller (backpressure), or spill to disk.
5. **"Do we need log rotation / file locking?"** → Out of scope for LLD; `FileSink` writes to a mock in-memory file buffer here, rotation is listed under extensions.
6. **"Is a message that fails in one sink retried? Do other sinks still get it?"** → Sinks are isolated: one failing sink never blocks the others (try/catch per sink).

## Core Entities

| Entity | Role |
|--------|------|
| `LogLevel` | Enum: DEBUG(0) < INFO(1) < WARN(2) < ERROR(3), with numeric severity for comparisons |
| `LoggerConfig` | Immutable config: minimum level + level→sinks map + async flag |
| `LoggerManager` | Facade + Observer subject: holds config, exposes `debug/info/warn/error`, notifies observers |
| `LogObserver` | Observer interface — a sink is registered as an observer of the manager |
| `Logger` | Abstract handler in the Chain of Responsibility; one concrete subclass per level |
| `LogSink` | Sink interface: `append(LogMessage)`, `name()`, `flush()` |
| `ConsoleSink` / `FileSink` / `DbSink` | Concrete sinks (File = in-memory buffer mock, Db = mock insert) |
| `LogMessage` | Immutable value object: level, text, timestamp, thread, caller |
| `MessageEnricher` | Strategy that adds metadata before dispatch |
| `SinkFactory` | Creates sinks by name from config (Factory pattern) |
| `AsyncAppender` | Single-thread executor wrapper with a bounded queue + drop-oldest policy |

## Design Patterns Used (with justification)

| Pattern | Where | Why this pattern |
|---------|-------|------------------|
| **Chain of Responsibility** | `Logger` → `DebugLogger` → `InfoLogger` → `WarnLogger` → `ErrorLogger` | Classic logger use-case: each handler checks "is this my level?" — if yes, dispatch to its sinks; either way, pass to the next handler. Adding a level = inserting one link; no `switch` statement anywhere. This is *the* textbook example interviewers expect. |
| **Observer** | `LoggerManager` ↔ `LogObserver` (sinks) | Sinks subscribe/unsubscribe at runtime; the manager broadcasts enriched messages without knowing sink types. Enables the "dynamically add a sink" requirement cleanly. |
| **Factory** | `SinkFactory` | Config says `"FILE"`; factory turns that string into a `FileSink`. Core never `new`s sinks directly, so new sinks plug in via the factory alone. |
| **Strategy** | `MessageEnricher`, overflow policy | Enrichment algorithm and queue-overflow behaviour are swappable strategies rather than hard-coded. |
| **Template Method (light)** | `Logger.log()` flow | Base class fixes the sequence (filter → enrich → chain → publish); subclasses only supply the level-specific decision. |

**Rejected alternatives** (see [explanation.md](explanation.md) for the full discussion):

- *A single `LoggerManager` with a `switch(level)`* — fails OCP: every new level edits the switch. Chain wins on extensibility.
- *Manager calling sinks directly* (no Observer) — sinks could not be added at runtime without mutating manager internals; Observer gives a stable registration API.
- *Per-sink-thread async* — N threads for N sinks is wasteful; one single-threaded appender preserves per-sink ordering, which log consumers rely on.

## How to Run

```bash
cd solutions/java

# Option 1: single-file source launcher (Java 11+)
java LoggerDemo.java

# Option 2: compile then run
javac *.java && java LoggerDemo
```

The demo prints `===`-delimited sections: sink configuration, chain pass-through, async mode, dynamic sink attach/detach, and overflow policy. Total runtime under 3 seconds.

## Extension Questions Interviewers Ask

1. How would you add a `TRACE` or `FATAL` level without touching the manager? *(insert a chain link + enum value — no core edits)*
2. How would you add a Kafka sink? *(implement `LogSink`, register in `SinkFactory` — two files touched, zero core files)*
3. What's your backpressure story at 1M msg/sec? *(measure: bounded queue + drop counters exposed as metrics; optionally a `BLOCK` policy variant behind the same interface)*
4. How do you guarantee ordering in async mode? *(single worker thread = per-sink FIFO; discuss what breaks if you shard the worker pool by sink)*
5. How would log rotation work? *(decorator around FileSink, or a `RollingPolicy` strategy checked on each append)*
6. How would you make this distributed? *(KafkaSink + aggregation; sampling for DEBUG in prod)*
7. Where do format/layout concerns go? *(a `LogFormatter` Strategy injected into sinks — kept out of scope here to stay focused)*

## Files Structure

```
logger_framework/
├── README.md                       # This file
├── design.puml                     # PlantUML class diagram
├── explanation.md                   # Design walkthrough, trade-offs, edge cases
└── solutions/
    └── java/
        ├── README.md                # Class-by-class guide + run instructions
        └── *.java                   # 13 source files, one top-level class per file
```
