# BookMyShow - Design Explanation

A walkthrough of every entity, the locking state machine, the concurrency decision (pessimistic vs optimistic), pattern choices, rejected alternatives, trade-offs, and the edge cases interviewers use to trip you up.

## The Problem in One Sentence

Thousands of users race for a few hundred seats per show; the system must give each seat to exactly one user, hold seats briefly while payment completes, release them if it doesn't, and never deadlock or double-charge — everything else (catalog, search, pricing) is standard OOP modelling.

**The crux is seat inventory under concurrency** — that is where this problem earns its place in an LLD round. Everything else is scaffolding around it.

---

## Entity-by-Entity Rationale

### `Seat` / `SeatType` / `Screen` — the physical catalog

`Seat` is an immutable catalog object: row + number + `SeatType` (REGULAR / PREMIUM / VIP). It never changes after the admin lays out the screen. `Screen` owns the seat layout and mints **globally unique seat ids** (`{screenId}-{row}{number}`, e.g. `scr1-C2`).

Why globally unique ids rather than (show, seat) pairs everywhere? Because every lock map, hold, and booking then keys on a plain string — one `Map<String, ReentrantLock>` serves all shows, and logs read unambiguously. The show is carried separately by `ShowSeat`.

### `Theatre` / `City` — location hierarchy

`Theatre` owns screens; `City` owns theatres and is the search root. `City.addTheatre` stamps the theatre's city name (a small back-pointer set at registration) so `Show -> Theatre -> city` navigation needs no city registry lookup on every search. In production this would be a `cityId` FK; for LLD the denormalized name is the honest simplification — say so.

### `Movie` — catalog metadata

Identity + language/duration/genre. Immutable, boring, correct.

### `Show` — the pivot entity

A show = movie + screen + start time + price multiplier. Its constructor does the single most important piece of wiring in the whole design:

```java
for (Seat seat : screen.getSeats()) {
    showSeats.put(seat.getSeatId(), new ShowSeat(showId, seat));
}
```

**One `ShowSeat` row per physical seat, created fresh per show.** This is what makes "a seat is free in the 6pm show but booked in the 9pm show" trivially true: availability is show-scoped data, not a flag on the shared catalog object. Interviewers probe this: "where does availability live?" The answer is *in the ShowSeat row keyed by (show, seat)* — never on `Seat`.

The per-show `priceMultiplier` (1.0 evening, 1.2 late-night, 0.9 morning) lets pricing vary per show without per-show strategy classes.

### `ShowSeat` / `SeatStatus` — the state machine (the heart)

```
AVAILABLE --tryLock(user)--> LOCKED --confirmBooking(user)--> BOOKED
                                |
                                |--release(user)--------> AVAILABLE   (payment failed / cancel)
                                |--expireIfLapsed()-----> EXPIRED -> AVAILABLE (hold timed out)
```

All transitions live **inside** `ShowSeat`, implemented as `AtomicReference<SeatStatus>` CAS operations:

```java
public boolean tryLock(String userId, Instant expiresAt) {
    if (status.compareAndSet(SeatStatus.AVAILABLE, SeatStatus.LOCKED)) {
        this.lockedByUserId = userId;
        this.holdExpiresAt = expiresAt;
        return true;
    }
    return false;   // someone else won — this IS the double-booking rejection
}
```

Design decisions worth stating out loud:

- **A LOCKED intermediate state exists because payment takes seconds-to-minutes.** Without it you would either freeze inventory globally during payment or sell the same seat twice. LOCKED = "reserved for this user for N seconds, pending payment".
- **EXPIRED is a transient marker, not a parking state.** The sweeper sets it so observers can distinguish "went free because the hold timed out" from "never locked", then immediately moves the seat to AVAILABLE so it is re-lockable. A seat stuck in EXPIRED would be stranded inventory — the exact failure the state machine exists to prevent.
- **CAS, not `if (status == X) status = Y`**: the check-and-set is one atomic instruction, so no interleaving can slip a second lock through. Even under the outer pessimistic locks, the CAS is the belt-and-braces guarantee that a future code path that forgets to lock still cannot corrupt state.
- **Guards throw with specific messages** (`confirmBooking` from a non-LOCKED state, `release` by a non-holder) — an interviewer reading the code should never wonder what a transition failure means.

Why is the state machine in the entity rather than the service? Because the seat row is the *only* place with complete knowledge of its own state; a service-level `if` would have to trust its view of the world (stale maps, races). The object protecting its own invariants is the answer that scores.

### `SeatHold` — a value receipt

The priced result of `lockSeats`: hold id, show, user, sorted seat ids, total, expiry instant. Deliberately powerless — anyone can construct one, so `confirmHold` re-validates every seat against the engine's own state. A forged or stale hold can book nothing. This "receipt, not capability" distinction is a nice interview point.

### `Booking` — the paid, confirmed record

Created **only** on payment success. Immutable: id, user, show, seats, total, payment id, instant. A failed payment never produces a Booking row — it just releases seats. That separation ("payment is not part of the booking aggregate until it succeeds") is exactly how real systems avoid half-paid bookings.

### `BookingService` — the engine

The class under the strongest interview scrutiny. Four responsibilities, each covered below in the concurrency section: `lockSeats`, `confirmHold`, `releaseSeats`/`cancelHold`, `sweepExpiredHolds`. Plus `registerShow`, which pre-creates the per-seat lock objects when a show becomes bookable (see "lock map population" below).

### `SearchService` — read-only browsing

Linear scans over an in-memory show list, filtered by city / movie title / hour window, sorted by start time. O(shows) per query — the right answer at interview scale; the scale-up story is in the trade-offs section. Kept separate from `BookingService` because search reads the catalog while booking mutates seat rows: different data, different evolution.

### `AdminService` — catalog writes + fan-out

Registers cities/theatres/screens (with a compact row-layout spec), and `scheduleShow` creates the Show then makes it **searchable and bookable in one call**. One entry point means no "visible in search but not bookable" window — a small consistency argument that reads well.

### `PriceCalculator`

`EnumMap<SeatType, Double>` base prices × the show's multiplier. Adding GOLD seats = one enum constant + one map entry, zero branching anywhere (OCP). `round2` is demo-grade; production is integer paise or `BigDecimal` — say it, it's a free point.

### `PaymentGateway` / `MockPaymentGateway` / `PaymentResult`

`charge(bookingId, userId, amount)` returns a `PaymentResult` (SUCCESS + payment id, or FAILURE + reason). Business failure is a *value*, not an exception — a declined card is not exceptional. The mock holds a scripted-outcome queue so the demo can force a failure at a chosen step, and it prints its own `[gateway]` narration lines.

### `BookingRepository` / `InMemoryBookingRepository`

The persistence seam, `ConcurrentHashMap`-backed. The engine never knows where bookings live.

---

## Class Relationships

```
City                1 ── many  Theatre
Theatre             1 ── many  Screen
Screen              1 ── many  Seat          (physical layout, immutable)
Movie               1 ── many  Show          (scheduled across screens)
Show                * ── 1     Screen + Movie + Theatre
Show                1 ── many  ShowSeat      (one per physical seat, created per show)
ShowSeat            * ── 1     Seat           (delegates type/price class)
ShowSeat            ..>        SeatStatus     (state machine)
Booking             * ── 1     Show
Booking             1 ── many  seat ids      (immutable copy)
SeatHold            1 ── many  seat ids      (receipt)
BookingService      uses→ ShowSeat registry, PaymentGateway, PriceCalculator, BookingRepository
SearchService       1 ── many  Show + City   (read-only catalog)
AdminService        uses→ SearchService + BookingService (fan-out)
```

No service depends on `Seat`'s physical identity beyond passing ids through; everything availability-shaped flows through `ShowSeat`.

---

## THE CRUX: Seat Concurrency — Pessimistic vs Optimistic

This is the section the interview is really about. State the options, compare, commit.

### The race

Two threads, same seat, same instant:

```
T1: read C2 status = AVAILABLE     T2: read C2 status = AVAILABLE
T1: decide to lock C2              T2: decide to lock C2
T1: write C2 = LOCKED(priya)       T2: write C2 = LOCKED(arjun)   <- last write wins, one user silently loses
```

Check-then-act on shared state. Both naive options below fix it; they differ in *who waits* and *what the user sees*.

### Option A: Optimistic (versioned CAS)

Every `ShowSeat` row carries a version. Reads are free; writes are `UPDATE ... WHERE version = v` — one winner, everyone else retries.

- **No blocking**: readers never wait; contention costs nothing until write time.
- **Scales beautifully for read-heavy, low-contention data** (profiles, settings, cart items).
- **The problem for ticketing**: a new release is a *flash sale*. Hundreds of users hammer the same ~300 seats; MOST attempts lose the CAS. Each loser already rendered "C1 available" in their UI, then hits "retry" — the UI flickers through seats disappearing, and retry storms amplify load at the worst moment. Optimistic concurrency degrades precisely when ticketing needs it most: extreme write contention on few rows.

### Option B: Pessimistic (lock the seat row before deciding) — CHOSEN

Acquire a per-seat mutex, *then* read the state, *then* act, *then* release. The loser waits milliseconds and reads the truth (`LOCKED`) from the start — their UI never shows the seat as available at all.

- **The wait is bounded and tiny**: the mutex covers only microsecond-scale state mutation, *never* the payment call (see below). With hundreds of seats per show, the probability two users contend on the *same* seat is small, and when they do, one `ReentrantLock` wait of microseconds is invisible.
- **Fairness for free**: the JVM lock queue orders contenders; no starvation churn.
- **Matches the domain semantics**: a movie seat is *intrinsically* exclusive inventory — modelling it as a mutually-exclusive resource is honest; modelling it as an optimistic counter is fighting the domain.

**This design chooses pessimistic per-seat locks**, and layers the CAS inside `ShowSeat` as the backstop. The `SeatStatus` transitions are CAS-guarded *anyway* (they must be, for the sweeper-vs-confirm race), so the optimistic machinery exists — it's just not the primary admission-control mechanism.

### The refinement that makes pessimistic correct: locks never span payment

Holding a mutex across a 2-second gateway call means every other booking touching those seats blocks for 2 seconds. Instead:

```
lockSeats:   acquire per-seat mutexes -> CAS AVAILABLE->LOCKED (+expiry) -> RELEASE mutexes
             ... seconds pass, user pays, nobody holds any mutex ...
confirmHold: acquire mutexes -> validate still ours -> RELEASE -> charge gateway
             -> acquire mutexes -> CAS LOCKED->BOOKED -> RELEASE
```

The **LOCKED status plus its expiry instant** is what protects the seat during payment — the mutex only ever guards state reads/writes. This is the classic BookMyShow/IRCTC hold design, and articulating the distinction ("the lock guards the state transition; the hold guards the business window") is the single strongest thing you can say in this interview.

### Deadlock prevention: sorted lock order

Two bookings want overlapping seats: T1 wants {A1, C2}, T2 wants {C2, A1}. If T1 locks A1→C2 while T2 locks C2→A1, each holds what the other needs — classic deadlock, forever.

Fix: **every thread acquires seat locks in ascending seat-id order** (`orderedSeatIds.sort(Comparator.naturalOrder())`). With a single global ordering, the lock graph is a straight line — no cycles are possible, so no deadlock is possible. This is the same discipline as `SELECT ... FOR UPDATE ... ORDER BY id` in Postgres, and it costs O(k log k) for k seats. State it unprompted and you've signalled real systems experience.

All-or-nothing on top: if any requested seat fails to lock, every seat already locked is rolled back (`SeatUnavailableException`, "Nothing was locked (all-or-nothing)") — a user never ends up with 1 of their 2 chosen seats.

### Lock map population (a subtle race)

`registerShow` pre-creates every seat's `ReentrantLock` when the show becomes bookable, rather than lazily doing `computeIfAbsent` at first lock. Why? Lazy creation is *almost* safe with `ConcurrentHashMap.computeIfAbsent` (it's atomic per key), but pre-creating at registration makes the invariant obvious — "a bookable show's every seat has a lock object" — and removes the creation cost from the contended path. In a DB this is just "the row exists."

### Hold expiry: the sweeper

`ScheduledExecutorService` (single daemon thread, every N seconds) walks every registered show's `ShowSeat` rows; lapsed LOCKED holds go EXPIRED → AVAILABLE. The sweeper takes **no mutexes** — `expireIfLapsed` is internally CAS-guarded, so a sweeper racing a confirm is always safe: exactly one transition wins. If the sweeper's CAS wins, the confirm's re-validation finds the hold gone and fails loudly; if the confirm wins, the sweeper's CAS finds BOOKED and does nothing.

Production equivalents: Redis `SET key value EX ttl` per seat (the TTL *is* the sweeper), or a DB job sweeping `locked_until < now()` rows. Same semantics, different substrate — and the demo shows the thread doing it live.

### Idempotency (double-click / webhook redelivery)

`confirmHold(hold, bookingId)` is keyed on a client-supplied booking id (in production: a UUID minted when the user hits Pay). The guard is `idempotencyCache.putIfAbsent(bookingId, booking)`:

- First call charges, flips seats, persists, caches. 
- Retry with the same id (double-click, flaky network, gateway webhook redelivered) hits the cache and returns the ORIGINAL booking — no second charge, no seat re-mutation.
- Concurrent same-id calls: one wins the `putIfAbsent`; the loser returns the winner's booking and a production system refunds the duplicate charge. (The demo prints the sequential path; the code documents the racing path.)

This is precisely how Razorpay/PhonePe-style integrations behave (idempotency keys on order creation and capture), and interviewers at payment companies probe it specifically. Money operations are idempotent *by key*, not by hope.

---

## Edge Cases and How the Code Handles Them

| Edge case | Behaviour |
|---|---|
| Two users lock the same seat concurrently | Pessimistic lock serializes; loser rejected, nothing of theirs locked |
| Overlapping seat sets from two bookings | Sorted acquisition order — no deadlock possible |
| Requested set partially available | All-or-nothing rollback; `SeatUnavailableException` names the failed seats |
| User never pays | Hold expires; sweeper frees seats (demo Step 4) |
| Payment fails | Seats released IMMEDIATELY (not left for the sweeper) + `PaymentFailedException` |
| Hold expired but payment succeeded (tiny race window) | Confirm re-validates under lock, finds the hold gone, throws; refund flow documented |
| Double-click Pay / webhook redelivery | Same bookingId → original booking returned, no second charge |
| Duplicate seat ids in one request | Rejected up front (`LinkedHashSet` size check) |
| >10 seats per booking | Rejected (real BMS constraint) |
| Seat not on this show's screen | `getShowSeat` throws with seat id + screen id in message |
| Lock a seat for show 2 while booked in show 1 | Fine — `ShowSeat` rows are per show |
| Booked seat re-lock attempt | `tryLock` CAS fails from non-AVAILABLE; named in failure list with state label |
| Cancel someone else's hold | `release` validates the holder and throws |
| Null/empty ids, bad row ranges, missing prices | `IllegalArgumentException` with the field named |

---

## Trade-offs Accepted

1. **In-process locks — stated explicitly.** `ReentrantLock` works for one JVM. At multi-node scale the *same design* maps to Redis `SETNX` per seat with TTL or `SELECT ... FOR UPDATE` rows; the sorted-order discipline and hold-expiry semantics carry over unchanged. The interviewer wants to hear that you know the boundary.
2. **Locks in a map, never released.** Lock objects accumulate per seat — bounded (seats per screen × screens) and permanent by design: the seat outlives every booking. No leak.
3. **Linear-scan search.** O(shows) per query. Production: `Map<city, Map<movieId, List<Show>>>` indexes plus cached seat maps with delta invalidation. For LLD, the scan plus a stated upgrade path reads better than premature indexing.
4. **Money as double with round2.** Demo-grade; production uses integer paise / `BigDecimal`. Say it before the interviewer does.
5. **Expiry as a background sweep, not per-hold timers.** One sweeper thread over N shows is simpler and easier to reason about than thousands of `ScheduledFuture`s, at the cost of freeing seats up to one sweep-interval late. A Redis TTL would get exact expiry for free — stated as the scale-up.
6. **Single-seat-map printing from the demo thread.** The seat map render reads live state without locks — fine for narration; a production UI subscribes to transition events.
7. **3-second holds in the demo.** Artificially short so expiry is visible in a ~5-second run; production uses 5-10 minutes.

---

## Complexity Summary

| Operation | Complexity |
|---|---|
| `lockSeats` (k seats) | O(k log k) sort + O(k) lock/unlock + O(k) CAS |
| `confirmHold` | O(k log k) + O(k) locks (twice: validate, finalize) + 1 gateway call |
| Sweeper pass | O(seats across registered shows) |
| Search (city + optional movie/hour) | O(shows) per query |
| `ShowSeat` transition | O(1) CAS |
| Booking persistence / lookup | O(1) map ops |

---

## How to Extend (Interview Talking Points)

- **Multi-node**: Redis lock per seat (`SET seat:show1:scr1-C2 <userId> NX EX 300`), confirm re-validates via `GET` + Lua compare-and-set; sorted order still applies across the Redis keys.
- **Cancel + refund**: `BOOKED -> AVAILABLE` transition + compensating gateway refund keyed by the same bookingId (idempotent refund — same pattern, reversed).
- **Seat-map at scale**: publish `ShowSeat` transitions on a message bus; per-show cache + WebSocket deltas; search indexes by city/movie.
- **Bot defence**: per-user concurrent-hold limit, rate limiting on `lockSeats`, CAPTCHA after N rejections.
- **Partial-show search ranking**: weight by available-seat ratio (a nearly-full show ranks lower).
- **Couple seats / aisle constraints**: seat adjacency metadata on `Seat`; `lockSeats` validates group contiguity before locking.
- **Dynamic pricing**: replace the flat multiplier with a demand-based strategy injected into `PriceCalculator` — additive, no engine change (OCP again).
