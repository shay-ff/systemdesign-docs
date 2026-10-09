# Machine Coding Round — A Playbook

> 🧭 **Navigation**: [← Interview Prep](README.md) | [📍 Full Navigation](../NAVIGATION.md)

The **machine coding round** (90–120 minutes, Flipkart/Uber/Swiggy/CRED style)
is different from the 45-minute LLD design round: here you must produce **working,
runnable, extensible code with tests and a demo**, solo, against the clock. It is
the round most candidates underestimate and most companies use to filter.

This guide is the operating manual. Pair it with [lld-round-guide.md](lld-round-guide.md)
for the discussion-based round.

---

## What's actually being graded

1. **Does it run?** A working demo beats an elegant design that doesn't compile.
2. **Is it extensible?** The interviewer will add a requirement at the end.
3. **Is it clean?** Naming, SRP, no God classes, validation.
4. **Are there tests?** Even a few meaningful tests separate you from the pack.
5. **Did you finish the core?** A complete happy path + key edge cases beats a
   half-built everything.

Note the ordering: **working > elegant.** Do not gold-plate the design at the
expense of a running demo.

---

## The time budget (for 120 minutes)

| Block | Time | Goal |
|---|---|---|
| 1. Clarify + scope | 10 min | Requirements, constraints, what to build first |
| 2. Design sketch | 10 min | Entities, interfaces, patterns — on paper |
| 3. Skeleton | 15 min | All classes with signatures, empty bodies |
| 4. Core logic | 40 min | The happy path, working end to end |
| 5. Edge cases + validation | 20 min | Invalid input, boundaries, the tricky bits |
| 6. Demo | 10 min | A `main` that exercises everything |
| 7. Tests | 10 min | A handful of focused tests |
| 8. Buffer | 15 min | Extensibility pass, cleanup, the follow-up |

**Set checkpoints.** At the 45-minute mark you should have a compiling skeleton
with the core path half-done. If you're behind, **cut scope, not quality** —
better a smaller thing that works.

---

## The workflow

### 1. Clarify (10 min)

Same as the LLD round but **write the requirements down** — you'll refer to them
while coding. Identify the **minimum viable feature set** (the happy path) and
the **must-have edge cases**. Explicitly decide what's out of scope so you don't
wander.

### 2. Design sketch (10 min)

On paper (or a comment block), list entities, their fields/methods, and the
interfaces at the seams. Don't over-draw — you're coding in 10 minutes. The
sketch is a map, not a deliverable.

### 3. Skeleton first (15 min)

Create every file with **full signatures and empty/throwing bodies** so the
project compiles from minute 25. This is the single most important habit: a
compiling skeleton means you always have something to submit, and adding logic
is filling in blanks rather than restructuring.

```java
// Skeleton: compiles, does nothing yet
public class ParkingLot {
    private final List<Level> levels;
    public ParkingLot(List<Level> levels) { this.levels = levels; }
    public Ticket park(Vehicle v) { throw new UnsupportedOperationException("TODO"); }
    public Receipt unpark(Ticket t) { throw new UnsupportedOperationException("TODO"); }
}
```

### 4. Core logic (40 min)

Build the happy path end to end. **Get it running before it's perfect.** A
working park→unpark flow with ugly internals beats a beautiful half-built
system. Commit mentally to "it runs" as the checkpoint.

### 5. Edge cases + validation (20 min)

Now harden: null/empty checks, boundary conditions, illegal states, the
concurrency case. Every public method validates its inputs with a clear message.

### 6. Demo (10 min)

A `main` (or a test) that walks through a realistic scenario and prints what
happened. **The demo is what the interviewer runs.** If it's not obvious how to
exercise your code, you lose points regardless of the logic.

### 7. Tests (10 min)

A handful of focused tests: the happy path, one edge case, one failure case. You
don't need coverage; you need to show you *think* in tests. Even
`assert`-based checks in a `main` count.

### 8. Buffer (15 min)

Reserve time to:
- Run the follow-up requirement ("now add X").
- Clean up dead code and TODOs.
- Re-run the demo to confirm it still works.

---

## Language-specific notes

### Java (most common for Indian product companies)
- Use `enum` for fixed sets (types, states) — interviewers love it.
- Interfaces for strategies; avoid pattern-soup.
- `java.util.*` and `java.util.concurrent.*` are fair game.
- No external libraries unless stated.
- Validation: `IllegalArgumentException` / `IllegalStateException` with messages.
- Don't `synchronized` everything "just in case" — name why.

### Python
- `dataclasses` for entities; `abc` for interfaces (if the interviewer wants
  explicit interfaces) or duck typing.
- Type hints help readability.
- `enum.Enum` for fixed sets.

### General
- **Consistent style** matters more than a specific style.
- **Meaningful names** — `PricingStrategy`, not `PS`.
- **Small classes** — a class over ~150 lines is usually doing too much.

---

## The concurrency question (again)

In a machine coding round you may be asked to *implement* thread-safety, not just
describe it. Have the patterns ready:

```java
// Pessimistic: lock the seat, then book
synchronized (seat) {
    if (!seat.isAvailable()) throw new SeatUnavailableException();
    seat.book();
}

// Optimistic: CAS with retry
while (true) {
    int v = seat.version();
    if (!seat.compareAndSetAvailable(v)) continue;
    if (seat.book(v)) break;
}
```

Know when each applies: pessimistic for high contention on a single resource
(a seat), optimistic for low contention with retries. See
`../01-ll-designs/bookmyshow/` and `../01-ll-designs/atm/` for full examples.

---

## Common mistakes

1. **No compiling skeleton** — you run out of time and submit broken code.
2. **Gold-plating** — 30 minutes on a beautiful abstraction, no working demo.
3. **No demo** — the interviewer can't see it work.
4. **Ignoring the follow-up** — the extension is often worth more than the core.
5. **Silent coding** — narrate your decisions; the interviewer is grading your
   thinking.
6. **Not testing** — even one test signals a professional habit.
7. **Wandering scope** — building features nobody asked for while the core is
   incomplete.

---

## A concrete 120-minute example (Parking Lot)

| Time | Activity |
|---|---|
| 0–10 | Clarify: vehicle types, spot sizes, pricing, ticket/receipt, concurrency? |
| 10–20 | Sketch: Vehicle, Spot, Level, Ticket, PricingStrategy, ParkingLot |
| 20–35 | Skeleton: all classes, signatures, throws |
| 35–70 | Core: park → find spot → ticket → unpark → price → receipt |
| 70–90 | Edge: full lot, wrong-size spot, invalid ticket, concurrent park |
| 90–100 | Demo: park 3 vehicles, unpark 1, show the receipt |
| 100–110 | Tests: happy path + full-lot failure + double-unpark |
| 110–120 | Follow-up: "add weekend pricing" → new PricingStrategy, done |

This is exactly the structure the repo's `../01-ll-designs/parking_lot/` follows.

---

## Pre-round checklist

- [ ] I can set up a project in my target language in < 5 min.
- [ ] I default to a compiling skeleton before logic.
- [ ] I always write a demo `main`.
- [ ] I always write at least 2–3 tests.
- [ ] I reserve 15 min for the follow-up requirement.
- [ ] I narrate my design decisions while coding.
- [ ] I know my concurrency patterns cold.

## Practice

The repo's `../01-ll-designs/` problems are all machine-coding-grade. Practice
by **re-implementing them from scratch, timed**, then comparing to the provided
solution. Do one per day; the goal is a reflex for the skeleton-first workflow.

---

*Related: [LLD Round Guide](lld-round-guide.md) ·
[LLD Patterns Cheatsheet](lld-patterns-cheatsheet.md)*
