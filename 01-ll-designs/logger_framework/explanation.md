# Logger Framework — Design Walkthrough

This is the deep-dive companion to the [README](README.md). It walks the design
entity by entity, records the alternatives that were rejected and *why*, and
ends with the complexity and edge-case analysis an interviewer will probe once
the class diagram is on the whiteboard.

The one-line thesis: **a logger is a pipeline, and every stage of the pipeline
is a place to hang an extension point.** Filter by level, enrich with metadata,
route through a chain, fan out to sinks. Get the seams right and the
Open/Closed Principle falls out for free.

---

## The message lifecycle

Everything in this design is in service of one flow. Trace a single call,
`manager.error("PayoutWorker", "Payout P-55 failed")`, through the system:

```
caller thread
  |
  |  manager.error(caller, text)
  v
[1] VALIDATE  level / caller / text present
   |          (fail fast at the API boundary)
   v
[2] FILTER    level.isEnabled(config.minimumLevel)?
   |          no -> suppressedCount++, return.
   |             Nothing is enriched, nothing is formatted --
   |             the cheapest message is the one never constructed.
   v
[3] ENRICH    MessageEnricher -> immutable LogMessage
   |          (timestamp, producer thread name, caller, level, text)
   v
[4] ROUTE     sync : chainHead.handle(message)   -- chain walks inline
   |          async: asyncAppender.submit(walk)  -- non-blocking offer into
   |                                             a bounded queue; caller returns
   v
[5] CHAIN     DebugLogger -> InfoLogger -> WarnLogger -> ErrorLogger
   |          each link: "is this my level?" -- publish if yes,
   |          and ALWAYS hand the message to the next link
   v
[6] PUBLISH   the owning link reads config.sinksFor(its level)
   |          and notifies each routed sink's observer adapter
   v
[7] SINK      adapter.onLog -> sink.append, one try/catch per
              sink: a failing sink never blocks the others
```

In sync mode, steps 4-7 run on the caller's thread. In async mode the caller
pays only for steps 1-3 plus one non-blocking queue offer, and every append
happens on the single worker thread — which is exactly why per-sink ordering
survives: one thread does all the appends, so each sink sees messages in
submission order.

Two decisions in this flow carry most of the interview weight, and both are
about *ordering*:

1. **Filter before enrich.** We never build a `LogMessage` for a line that will
   be dropped. On a hot path that logs at DEBUG and runs at INFO in production,
   this is the difference between "logging is free" and "logging is the
   bottleneck". Building a timestamped, thread-named, caller-stamped object for
   every discarded line is the classic premature-work bug.

2. **Filter is the manager's job; routing is the chain's job.** These are
   different questions — *should this be logged at all?* versus *which sinks
   want it?* — and conflating them is how you end up with a `switch(level)`
   that has to be edited for every new level. Keeping them separate is what
   makes the chain work.

---

## Entity-by-entity

### `LogLevel` — severity as data, not position

An enum carrying an integer severity. Filtering is one integer comparison
(`this.severity >= minimum.severity`), not an `indexOf()` on a list or a
hand-written comparison table.

The design point is *why the severity lives on the enum*: an ordinal-based
comparison (`DEBUG.ordinal() < ERROR.ordinal()`) silently breaks the day
someone reorders the constants, and a `switch` breaks every time a level is
added. A field on the constant is stable under reordering and extensible —
adding `TRACE` is one constant plus one chain link.

**Rejected:** a `Set<LogLevel>` of "enabled levels" instead of a minimum.
It seems more flexible, but the overwhelming majority of real configurations
are a single threshold, and the threshold form makes `isEnabled` O(1) and the
config a single value to snapshot. The set form is a documented extension, not
the default.

### `LogMessage` — the immutable record

A final class with five fields (level, text, timestamp, thread name, caller),
all set at construction, no setters. Immutability is not decoration here: the
object is created on the caller thread and then read by *other* threads — the
async worker, multiple sink threads. An immutable object is safe to hand across
thread boundaries with no copying and no visibility subtleties. `render()` is
the single place the on-the-wire line format is defined, so every sink agrees
on what a log line looks like.

**Rejected:** a mutable message that each stage decorates in turn. It reads as
"clean" (each enricher adds its field) but it turns a value into shared mutable
state and reintroduces exactly the cross-thread hazards immutability removes.

### `MessageEnricher` — the metadata strategy

Turns the raw `(level, caller, text)` triple into a `LogMessage`. Kept as its
own class (rather than a private method on the manager) so the enrichment
policy is a seam: a future "add trace-id / request-id" enricher slots in here
without touching the pipeline.

The design carries a deliberate **trade-off worth stating out loud**: the
public API makes the caller pass its own class name
(`manager.info("PaymentService", ...)`), and the class also ships an
`enrichWithStackCapture` variant that walks the stack to discover the caller.
The explicit-parameter path is the default because stack walking costs 10-100×
the log call itself — real frameworks use `StackWalker`, a precomputed
per-logger class name, or logging-aspect instrumentation precisely to avoid it.
Being able to say "I made the cheap choice on purpose, here is the expensive
alternative and when you'd take it" is the point.

### `Logger` and its subclasses — the Chain of Responsibility

`Logger` is the abstract handler; `DebugLogger`, `InfoLogger`, `WarnLogger`,
`ErrorLogger` are four near-identical links, one per level. Each link's
`handle()` does two things:

```java
if (matchesLevel(message.getLevel())) {
    publish(message);          // this is my level -> deliver to my sinks
}
if (next != null) {
    next.handle(message);      // either way -> pass to the next link
}
```

This is the textbook logger chain, and the detail interviewers zoom in on is
**why it passes through instead of stopping at the first match**. A
"first-handler-wins" chain (the shape you see in HTTP middleware) would stop
as soon as it found a handler. Here every link looks at every message, because
the levels are a *filter*, not a *dispatch*: a WARN and an ERROR can share the
file sink, and the design must let each link independently decide whether the
message belongs to it. The chain is a fan-out of independent predicates, not a
race to claim the message.

Why a chain at all, versus a `switch(level)` in the manager? Because adding
`TRACE` to a switch means editing the switch — every new level touches core
code, which fails OCP. Adding `TRACE` here means adding one enum constant and
one `TraceLogger` link; `LoggerManager` and the existing links are untouched.
`setNext()` returns the next link so the chain can be built fluently:

```java
debug.setNext(info).setNext(warn).setNext(error);
```

The `next` pointer is `volatile` so the chain can be rewired at runtime without
tearing — the same discipline the rest of the framework applies to mutable
state.

**Rejected:** a single `Logger` class with an internal `switch`. Simpler for
four levels; a maintenance trap for the fifth.

### `LogSink` and the concrete sinks — the output seam

`LogSink` is a three-method interface: `name()`, `append(LogMessage)`,
`flush()`. `ConsoleSink`, `FileSink`, and `DbSink` implement it. The interface
is deliberately minimal — it describes *where bytes go*, nothing about
registration or configuration. That minimalism is what keeps new sinks cheap to
add.

`FileSink` and `DbSink` are mocks (an in-memory line buffer and a list of fake
`INSERT` strings) so the framework compiles with zero external dependencies and
the demo can prove what each sink received. The interface contract states that
a sink **must swallow or translate its own exceptions** — a failing sink must
never break the chain or starve the other sinks. That contract is enforced at
the adapter (below), not left to each sink author to remember.

**Rejected:** a `Sink` abstract class with a shared `format()` template. No two
sinks share enough behavior to justify inheritance; the interface is smaller
and more honest.

### `SinkFactory` — the creation seam

Turns a declarative spec string into a sink: `"CONSOLE"`, `"FILE:app.log"`,
`"DB:audit_logs"`. The config speaks specs; the factory knows the mapping from
spec to class. Adding a `KafkaSink` means adding one class and one branch here
— callers and the core never change.

The factory is the reason the config can be a *data structure* rather than a
code path. Without it, "which sinks for which level" would have to be
constructed imperatively by every caller, and the whole pluggability story
would collapse into scattered `new` calls.

**Rejected:** letting callers construct sinks directly and register instances.
Fine for the demo, but it couples every caller to concrete sink classes and
loses the ability to drive the whole framework from a config object — the thing
that makes it a *framework* and not a utility.

### `LoggerConfig` — the immutable snapshot

Holds the minimum level, the per-level sink lists, and the async flag — and is
**immutable**. Runtime reconfiguration goes through `LoggerManager.reconfigure`,
which builds a *new* snapshot and swaps it in atomically. The hot path reads
`config` (a `volatile` reference) with no locking; readers either see the old
snapshot or the new one, never a half-updated map.

The per-level sink list is what delivers requirement F2 ("ERROR goes to
console + file + DB, DEBUG only to console"): a level maps to a *set* of sinks,
so routing is data, not branching. `sinksFor(level)` is a single map lookup.
`allSinks()` deduplicates across levels by identity, which is what `shutdown()`
iterates to flush each sink exactly once.

**Rejected:** mutable config with synchronized accessors. Every log call would
contend on the config lock; copy-on-write makes reads free and writes rare.

### `LogObserver` and `SinkObserverAdapter` — the runtime-pluggability seam

`LogObserver` is the observer interface (`onLog(LogMessage)`).
`SinkObserverAdapter` wraps a `LogSink` so it can act as an observer. The
manager keeps observers in a `CopyOnWriteArrayList`, so sinks attach and detach
at runtime while the hot path iterates a stable snapshot with no locking.

The subtle design question — *why an adapter instead of just making `LogSink`
extend `LogObserver`?* — has a clean answer: the sink SPI should stay minimal
(`append`/`flush`/`name`). Forcing every future sink author to also understand
the observer lifecycle would couple the output contract to the registration
mechanism. The adapter keeps the two roles orthogonal: a sink knows how to
write, an observer knows how to subscribe, and the adapter is the one place
they meet.

The adapter is also where **sink isolation** lives. `onLog` wraps
`sink.append` in a `try/catch`:

```java
try { sink.append(message); }
catch (RuntimeException e) { System.err.println(...); }
```

One throwing sink cannot break the notify loop for the others. In production
that catch would bump a metrics counter and route to an error sink; here it
prints and moves on. The important thing is that the failure mode is *isolated
by construction*, not by convention.

**Rejected:** manager calling sinks directly with no observer layer. Then sinks
could only be added by mutating the manager's internals, and "add a sink at
runtime" would be an invasive operation instead of `attachSink(...)`.

### `LoggerManager` — the facade and the two roles it plays

The class application code actually touches. It plays two pattern roles at once,
and naming both is worth doing in the round:

- **Observer subject** — sinks register as `LogObserver`s; published messages
  are broadcast. The observer list is a `CopyOnWriteArrayList`, so
  `attachSink`/`detachSink` are safe against a concurrent notify.
- **Chain owner** — builds the `DEBUG -> INFO -> WARN -> ERROR` chain once and
  routes every message through its head.

The public API is `debug/info/warn/error` plus `log(level, caller, text)`.
`log` is the pipeline: filter -> enrich -> route -> deliver. `reconfigure`
swaps the config snapshot and creates or tears down the async appender to match
the new mode; `shutdown` flushes the async worker and every sink.

`attachSink` does one thing that looks odd until you see the intent: it
registers the sink as an observer *and* adds it to every level's sink list,
"mirroring the most common intent — I want this sink to see what the framework
is currently routing." It is a convenience that matches the demo's narrative
("attach an alerts sink, watch ERRORs flow to it") without forcing callers to
re-specify the per-level routing by hand.

Thread-safety strategy, collected in one place: config is an immutable snapshot
swapped atomically; observers live in a lock-free copy-on-write list; chain
links are built once and their `next` pointers are volatile; in async mode a
single worker serializes all appends.

### `AsyncAppender` — the backpressure decision

The most interview-rich class. Callers `submit` a `Runnable` and return
immediately; a **single background worker** drains a **bounded** queue and
appends. The queue bound is the whole point — an unbounded queue is an
OutOfMemoryError with extra steps.

When the queue is full, the configured policy fires. The design ships
`DROP_OLDEST` as the default and documents all three:

| Policy | Preserves | Cost |
|---|---|---|
| **BLOCK** the producer | every message (at-least-once) | reintroduces latency into the app thread — the thing async mode exists to remove; under sustained burst the app degrades to sync throughput with extra hops |
| **DROP_NEWEST** | history / ordering | discards exactly the freshest messages — usually the error that triggered the burst |
| **DROP_OLDEST** (default) | the newest evidence | log stream has *visible* gaps (a dropped counter); accepted cost under overload |

`DROP_OLDEST` is the honest default for logs: bounded memory, non-blocking
callers, and the freshest diagnosis survives. The failure mode (gaps) is
observable via `getDroppedCount()`, which is what lets you alert on it.

**Why a single worker thread** and not a pool? It guarantees per-sink FIFO
ordering — a log file whose lines are out of order is far harder to read, and
ordering is a property log consumers rely on. The cost is that one thread's
throughput is the ceiling; the standard scale-up is to *shard* workers by sink
or by thread-id while preserving ordering *within* a shard. Saying this
trade-off out loud is the difference between "used a thread pool" and
"understood why ordering constrains the pool".

---

## Complexity

| Operation | Cost | Notes |
|---|---|---|
| `log()` — filtered out | O(1) | one integer comparison; no object built |
| `log()` — sync, accepted | O(L + S) | L chain links, S sinks for the matched level |
| `log()` — async, accepted | O(1) enqueue | O(1) amortized (plus O(1) eviction under DROP_OLDEST) |
| `attachSink` / `detachSink` | O(L·S) | rebuilds the config snapshot; rare, off the hot path |
| `reconfigure` | O(L·S) | builds a new snapshot, atomic swap |
| `shutdown` | O(S) | flush each distinct sink once |

The hot path (`log`) is O(L + S) with L = 4 and S small — effectively constant,
and O(1) when suppressed. The expensive operations are all reconfiguration,
which happens at startup or on an admin action, never per message.

---

## Edge cases the design handles

1. **A sink throws mid-append.** `SinkObserverAdapter.onLog` catches it; the
   other sinks still receive the message, and the chain continues. Isolation by
   construction.
2. **The async queue overflows.** `DROP_OLDEST` evicts the head; the drop is
   counted. Callers never block (under the default policy).
3. **A message is logged before any sink is configured for its level.**
   `sinksFor` returns an empty list; the link publishes to nothing. The message
   is not an error — it is simply unrouted. (A production variant would warn on
   empty routing at config time.)
4. **Reconfigure from sync to async (or back) at runtime.** `reconfigure`
   creates or shuts down the appender to match; the config snapshot and the
   appender stay consistent.
5. **Shutdown with messages still queued.** `AsyncAppender.shutdown` drains (or
   awaits termination with a timeout) before returning, so buffered lines are
   not silently lost.
6. **A log call races with `attachSink`.** The observer list is copy-on-write:
   the in-flight notify sees a consistent snapshot; the new sink starts
   receiving from the next message.
7. **Empty or null inputs.** Every public entry point validates and throws
   `IllegalArgumentException` with a message that names the fix (e.g. "pass the
   logging class, e.g. getClass().getSimpleName()").

---

## Testing strategy

The framework is testable precisely because its seams are injected:

- **Chain routing** — build a manager with a recording sink per level, log one
  message per level, assert exactly the expected sinks saw it. No I/O.
- **Filtering** — raise the minimum level, assert `getSuppressedCount()` and
  that no sink was touched.
- **Runtime attach/detach** — attach a sink, log, detach, log; assert the
  boundary message counts.
- **Async ordering** — log N messages, shut down (drains), assert the sink
  received them in submission order.
- **Backpressure** — construct an `AsyncAppender` with a tiny capacity and a
  blocked worker, submit more than capacity, assert `getDroppedCount()`.
- **Sink isolation** — a sink that throws; assert the others still received the
  message.

Every one of these runs in-process with no external dependencies, because the
mocks (`FileSink`, `DbSink`) are real classes with inspectable state.

---

## What this problem is really testing

Strip away the logger dressing and the interviewer is checking four things:

1. **Do you reach for Chain of Responsibility when the problem is "N handlers,
   each independently interested"?** (And can you explain why it beats a switch?)
2. **Do you understand the Observer pattern's runtime-registration payoff?**
   ("Add a sink without restarting" is the requirement that forces it.)
3. **Can you reason about backpressure and ordering in an async pipeline?**
   (The bounded queue, the drop policy, the single worker.)
4. **Do you apply OCP concretely** — new sink, new level, new enricher, each a
   new class and zero edits to the core?

If you can walk the lifecycle diagram, defend the chain-passes-through choice,
and explain the drop-oldest trade-off, you have covered the round.

---

*See [README.md](README.md) for the problem statement and pattern summary, and
[design.puml](design.puml) for the class diagram.*
