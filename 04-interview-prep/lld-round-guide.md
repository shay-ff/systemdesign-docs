# The LLD Interview Round — A Playbook

> 🧭 **Navigation**: [← Interview Prep](README.md) | [📍 Full Navigation](../NAVIGATION.md)

This is the operating manual for the **45–60 minute low-level design round**.
It is the single most under-prepared round by candidates with 2–5 years of
experience, because it looks like "just write classes" and is actually a
structured design conversation with a rubric.

Read this once end-to-end, then use the [framework](#the-5-phase-framework)
checklist in practice. Pair it with [machine-coding-guide.md](machine-coding-guide.md)
for the longer coding round, and [lld-patterns-cheatsheet.md](lld-patterns-cheatsheet.md)
for the pattern→problem map.

---

## What the interviewer is actually grading

You are not being graded on whether the code compiles (usually it doesn't even
need to). You are being graded on five things, roughly in this order:

1. **Requirements clarification** — did you pin down scope before designing?
2. **Class/entity modelling** — are the right nouns and responsibilities
   identified, with clean boundaries?
3. **Design principles** — SOLID, especially SRP and OCP; appropriate patterns,
   not pattern-soup.
4. **Extensibility** — when the interviewer adds a requirement, does your design
   absorb it without a rewrite?
5. **Communication** — can you explain *why* each decision, and discuss
   trade-offs?

Notice that "writing lots of code" is not on the list. Candidates who start
typing in minute two and produce 400 lines of code usually score *worse* than
candidates who spend 10 minutes clarifying and 30 minutes designing cleanly.

---

## The 5-phase framework

Budget for a 45-minute round:

| Phase | Time | Goal |
|---|---|---|
| 1. Clarify | 5–8 min | Nail requirements + scope; surface ambiguities |
| 2. Entities | 5 min | Identify core classes, their responsibilities, relationships |
| 3. Design | 10–15 min | Class diagram, interfaces, patterns, key methods |
| 4. Deep-dive | 10–15 min | Walk the critical flow; handle edge cases; code key parts |
| 5. Extend | 5–7 min | Absorb the interviewer's follow-up requirement |

The phases are not rigid — you'll loop back — but the *shape* is: talk first,
design second, code third.

---

### Phase 1 — Clarify (5–8 min)

Ask 5–8 sharp questions. The goal is not to enumerate every feature but to find
the 2–3 decisions that reshape the design. Good questions are **specific and
consequential**:

For any problem, ask:
- **Scope:** what's in, what's out? (payments: "do we handle refunds? partial?")
- **Scale/constraints:** how many users/objects? single machine or distributed?
- **Concurrency:** can multiple operations race? (This one changes everything —
  see the concurrency section below.)
- **Extensibility hints:** "should I design for adding new X later?"
- **Edge cases:** the specific ambiguities of *this* problem.

**Worked example — Splitwise:**
> "A few things to pin down: (1) Splits can be equal, by percentage, or by exact
> amounts — should I support all three? (2) Currency — single currency, and do I
> need to worry about rounding when a split doesn't divide evenly? (3) Do users
> need to see raw pairwise balances or just a simplified 'who pays whom'? (4)
> Should removing a user with outstanding balances be allowed?"

Each question maps to a design decision: (1) a `Split` hierarchy, (2) integer
paise + a remainder rule, (3) a simplification service, (4) a validation guard.
That is what a strong clarify phase looks like.

**Anti-pattern:** "Should I use a database?" — too vague, and not a design
decision you make in LLD (it's in-memory).

---

### Phase 2 — Entities (5 min)

Name the **nouns** and give each a one-line responsibility. State them out
loud: "I see these core entities: User, Group, Expense, Split, and a
BalanceService." Then check for SRP violations before you draw.

**The responsibility test:** if you can't describe a class's job in one sentence
without "and", it's doing too much.

**Worked example — Parking Lot:** Vehicle, ParkingSpot, Level, Ticket,
PricingPolicy, ParkingLot (the orchestrator). Notice `PricingPolicy` is separate
from `ParkingLot` — pricing is a policy that varies, so it's its own thing
(and later a Strategy).

**Identify the varying part early.** The thing most likely to change is the
thing that most deserves an interface. In Parking Lot it's pricing; in Splitwise
it's the split type; in Rate Limiter it's the algorithm; in Elevator it's the
scheduling policy. Naming this early sets up Phase 3.

---

### Phase 3 — Design (10–15 min)

Draw the class diagram (boxes and lines are fine — the interviewer wants to see
relationships). For each class: key fields, key methods, and **which pattern (if
any) it embodies**.

**Patterns: reach for them deliberately, not decoratively.** A design with five
patterns where one would do reads as inexperienced. The common LLD patterns and
when they genuinely apply:

| Pattern | Use when | Canonical LLD example |
|---|---|---|
| **Strategy** | An algorithm/policy varies and should be swappable | Pricing, split type, elevator scheduling, rate-limit algorithm |
| **State** | An object's behavior changes with its state | Vending machine, ATM, booking state, elevator car |
| **Observer** | One change must notify many listeners | Logger sinks, notification fan-out, calendar invites |
| **Factory** | Object creation should be decoupled from use | Sink factory, vehicle factory, shape factory |
| **Decorator** | Add behavior without changing the class | Crooked dice, log formatters, priced add-ons |
| **Chain of Responsibility** | N handlers, each independently interested | Logger levels, approval chains |
| **Singleton** | Exactly one shared instance | Config, a single game board — *use sparingly* |
| **Builder** | Complex object construction | Query builders, immutable config |

**Interfaces at the seams.** Every place you say "this might vary" gets an
interface. `PaymentMethod`, `PricingStrategy`, `LogSink`, `VendingMachineState`.

**State the SOLID story out loud.** "ParkingLot depends on the PricingPolicy
*interface*, so adding weekend pricing is a new class, not an edit to
ParkingLot — that's the Open/Closed Principle." Interviewers are listening for
exactly this vocabulary applied to *your* diagram.

---

### Phase 4 — Deep-dive (10–15 min)

Pick the **critical flow** and walk it step by step, naming the objects and
methods involved. This is where you prove the design works, not just looks
clean.

"User parks a car: `ParkingLot.park(vehicle)` → find a free spot of the right
type → create a `Ticket` with a timestamp → mark the spot occupied → return the
ticket."

Then hit the **edge cases** — this is what separates strong from average:
- The invalid input (non-member, negative amount, illegal state transition).
- The boundary (cache full, spot of the wrong size, split that doesn't divide).
- The **concurrency case** (below).

**Code the hard 20%.** If asked to write code, write the *interesting* part —
the eviction logic, the state transition, the settlement algorithm — with real
signatures and validation. You don't need every getter.

---

### Concurrency — the differentiator

For a candidate with ~2 years' experience, **thread-safety is the most common
gap** and the fastest way to stand out. Always ask "can two operations race?"
and answer it concretely:

- **Read-modify-write on shared state** (e.g. "is this seat free? → book it")
  is the classic race. Fixes: a lock (`synchronized`/`ReentrantLock`), an
  atomic operation, or optimistic concurrency (a version/CAS).
- **Booking systems** (BookMyShow, parking) — lock the seat/spot; discuss
  pessimistic (lock first) vs. optimistic (CAS, retry on conflict).
- **Counters/balances** (Splitwise, ATM) — guard the account with a lock or use
  atomic types.
- **Name the trade-off:** coarse lock (simple, low throughput) vs. fine-grained
  (complex, scalable) vs. lock-free (hard, fastest). Say which you'd pick and
  why.

You don't need to *implement* the concurrency in the 45-min round, but you must
**name it and propose the fix.** See `../01-ll-designs/bookmyshow/explanation.md`
and `../01-ll-designs/atm/` for worked examples.

---

### Phase 5 — Extend (5–7 min)

The interviewer will add a requirement: "now add weekend pricing," "support
partial refunds," "add a new payment method." **This is the real test of your
design.** A good answer:

1. Names *what changes* and *what doesn't*.
2. Shows the new class/interface.
3. Doesn't rewrite the core.

"Adding weekend pricing: I implement a new `PricingStrategy`. `ParkingLot`
doesn't change — it already depends on the interface. That's the OCP payoff."

If your design *can't* absorb it cleanly, **say so honestly and show the
refactor** — "my current `Spot` has the type baked in; I'd extract a `SpotType`
strategy here." Honesty about a limitation beats pretending.

---

## Anti-patterns that sink candidates

1. **Jumping to code.** No requirements, no entities — straight to `class
   Main`. Fails the rubric's first two criteria.
2. **God class.** One `SystemManager` that does everything. Violates SRP
   visibly.
3. **Pattern soup.** Factory-for-a-factory when a plain constructor works.
   Patterns are tools, not trophies.
4. **Ignoring concurrency.** "It's single-threaded" when the interviewer clearly
   hinted at contention.
5. **Silent designing.** Thinking without talking. The interviewer grades your
   reasoning; if they can't hear it, it doesn't count.
6. **Defending a bad design.** When the interviewer pokes a hole, engage with it
   instead of defending. "Good point — that breaks if X; let me adjust."
7. **No validation/edge cases.** A design that only handles the happy path is
   incomplete.

---

## The checklist (memorise this)

Before you say "I'm done," confirm:

- [ ] I clarified scope and found the 2–3 shaping decisions.
- [ ] I named the entities with single responsibilities.
- [ ] I identified the part most likely to vary and made it an interface.
- [ ] I used patterns deliberately and can justify each.
- [ ] I walked the critical flow end to end.
- [ ] I handled the invalid, boundary, and concurrency cases.
- [ ] I stated the SOLID story for my key seams.
- [ ] I absorbed the follow-up requirement without a rewrite.

---

## Practice plan

1. Pick a problem from `../01-ll-designs/` you *haven't* read.
2. Set a 45-minute timer. Do phases 1–3 on paper, talking out loud.
3. Compare against the repo's design. Where did you differ? Which is better and
   why?
4. Do one **per day** for two weeks. The repetition builds the pattern reflexes
   that make the real round feel routine.

The repo's `../04-interview-prep/mock-interviews/` has full scenarios with
rubrics for self-assessment.

---

*Related: [Machine Coding Guide](machine-coding-guide.md) ·
[LLD Patterns Cheatsheet](lld-patterns-cheatsheet.md) ·
[HLD Quick Reference](../02-hl-designs/hld-quick-reference.md)*
