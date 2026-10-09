# Car Rental System - Design Explanation

A walkthrough of every entity, the relationships between them, why each pattern was chosen, what was rejected, and the edge cases interviewers use to trip you up.

## The Problem in One Sentence

Many vehicles across many stores must be rentable for arbitrary date windows, with a guarantee that **no two active reservations overlap on the same vehicle**, flexible pricing, and a lifecycle that mirrors real rentals (book -> pickup -> return / cancel).

Everything else (inventory, payment, pricing) is standard OOP modelling. **Overlap detection is the crux** — it is where this problem earns its place in an LLD round.

---

## Entity-by-Entity Rationale

### `Interval` — the conflict-detection core

An immutable pair of `LocalDate` with a **half-open `[start, end)`** convention — start = pickup day, end = drop-off day. The vehicle is held from start (00:00) up to but *not including* end, so it is free again on the drop-off day itself. Two load-bearing methods:

```java
public boolean overlaps(Interval other) {
    return this.start.isBefore(other.end) && other.start.isBefore(this.end);
}
```

**Why strict inequality?** Because touching windows must be allowed: pickup Oct 1 with drop-off Oct 3 frees the car on Oct 3, so a second reservation picking up Oct 3 is legal (dropped off in the morning, picked up again in the evening). With `<=` you'd wrongly reject back-to-back bookings. Interviews probe exactly this boundary.

Implementation notes:

- Validation: `start` must be *strictly before* `end` (throws with a clear message) — a rental must span at least one day.
- `days()` = `ChronoUnit.DAYS.between(start, end)` — pickup Oct 1, drop-off Oct 3 = 2 rental days, matching 24h-period billing. (State your convention out loud; either inclusive or exclusive-end works as long as `overlaps` matches it.)
- The half-open form makes `overlaps` the classic strict-inequality check — no off-by-one wrangling.

### `Vehicle` (abstract) + `Car` / `Suv` / `Bike` / `Truck`

`Vehicle` fixes identity (`vehicleId`, license plate), the `VehicleType`, per-day base price, and daily km allowance. Variants just supply parameters through constructors — `Bike` and `Truck` were added **without touching `BookingService`, `VehicleInventory`, or `Store`**. That is the OCP/LSP talking point: services depend on the `Vehicle` abstraction and `VehicleType` enum, never on concrete classes.

Why abstract instead of a concrete class with a type enum only? Because variants can later add behaviour (e.g., `Truck` requiring a commercial licence check, `Bike` needing a helmet deposit) without services knowing. For a 45-minute interview, an abstract class + enum combo reads as deliberate, not over-engineered.

### `VehicleType` — enum

CAR, SUV, BIKE, TRUCK. Search-by-type is the primary browse axis ("show me SUVs in Bengaluru"), so the enum is the key in `VehicleInventory`'s type index.

### `Location` — immutable value object

City, pincode, address. Immutable (no setters) because addresses are identity-like data; two threads can share them freely. Equality by city+pincode.

### `Store` — per-city inventory owner

A `Store` belongs to one `Location` and **composes** a `VehicleInventory` plus the reservation log for its vehicles. Composition over inheritance: a store is not *a kind of* inventory; it *has* one, and it also owns reservation policy, so the responsibilities stay clean.

### `VehicleInventory`

Type-keyed index (`Map<VehicleType, List<Vehicle>>`) for `addVehicle`, `removeVehicle`, and `findByType`. The availability question — "is vehicle V free during interval I?" — is answered by scanning V's active reservations; keeping that logic with the reservation store (`Store.isAvailable`) rather than duplicating it in inventory avoids two sources of truth.

### `Reservation` + `ReservationStatus`

Lifecycle: `SCHEDULED -> (pickup) -> IN_PROGRESS -> (return) -> COMPLETED`, and `SCHEDULED -> (cancel) -> CANCELLED`. The reservation owns:

- vehicle, customer, interval, status
- `bookedKmAllowance` (days * daily allowance)
- final `bill` (a `Reservation.Bill` breakdown printed by the demo)

Status transitions are guarded *inside* `Reservation` (`markInProgress()`, `complete(...)`, `cancel()`) — calling `cancel()` on a COMPLETED reservation throws. Centralizing the state machine in the entity (not the service) is the move interviewers like: the object protects its own invariants.

### `BookingService` — the facade

The only class the demo/customer talks to. Responsibilities:

1. `findStores(city)` — browse stores.
2. `searchAvailable(store, type, interval)` — inventory by type, filtered by non-overlap.
3. `book(store, vehicle, customer, interval)` — **the overlap check**, pricing, payment charge, reservation creation.
4. `pickup(reservationId)` / `returnVehicle(reservationId, actualKm, actualDate)` — lifecycle + final bill (late penalty applied here).
5. `cancel(reservationId)` — state machine + refund via `PaymentService`.

Booking = check-then-act. In a single-threaded demo that's safe; the concurrency section below covers the multi-threaded version.

### `PricingStrategy` — strategy pattern

```java
public interface PricingStrategy {
    Bill calculate(Vehicle vehicle, Interval interval, int daysKmAllowance, int actualKm);
}
```

Two implementations:

- **`PerDayPricingStrategy`** — base daily rate x days; km used above allowance charged per extra km.
- **`LatePenaltyPricingStrategy`** — *wraps* a base strategy, compares actual return date to booked end, and adds a penalty line (extra days at the daily rate + flat late fee). Decorator-flavoured: it delegates the base computation and layers a surcharge, instead of copy-pasting the base formula.

Rejected alternative: one `PricingStrategy` with `if (late)` branches inside. Rejected because the strategy pattern's whole point is eliminating those branches; each pricing rule stays a single small class, and new rules (weekend surge, long-rental discount) are additive.

### `Payment` / `PaymentService` — mock

`PaymentService.charge(...)` / `.refund(...)` create `Payment` records (id, reservation, amount, SUCCESS/REFUNDED) in a map, so the demo can show money movement. A real gateway is an integration detail behind this interface — exactly what you'd tell the interviewer.

### `Customer`

Simple renter identity (id, name, driving licence).

---

## Class Relationships

```
RentalCompany (optional root)  1 ── many  Store
Store                1 ── 1     VehicleInventory (composition)
Store                1 ── many   Vehicle        (via inventory index)
VehicleInventory     1 ── many   Vehicle        (type-keyed map)
Store                1 ── many   Reservation    (reservation log)
Reservation          * ── 1     Vehicle
Reservation          * ── 1     Customer
Reservation          1 ── 1     Interval       (value object)
Reservation          1 ── *     Payment        (charge, maybe refund)
BookingService      uses→ Store, PricingStrategy, PaymentService (composition)
```

No service depends on a concrete `Vehicle` subclass anywhere — only on `Vehicle` and `VehicleType`.

---

## The Crux: Overlap Detection — Three Options Compared

### Option A: O(n) scan (implemented)

For a booking on vehicle V, scan V's active (SCHEDULED / IN_PROGRESS) reservations and call `overlaps()`.

- Complexity: O(R) per booking, R = reservations on that vehicle.
- Verdict: **correct and completely fine for an interview** (R is tens, not millions). Zero auxiliary state to keep consistent.

### Option B: per-vehicle sorted list + binary search

Keep each vehicle's reservations sorted by start date; binary-search for the first reservation ending after the new start, check it and its predecessor.

- Complexity: O(log R) search, O(R) insert (list shift).
- When it wins: read-heavy, moderately large R per vehicle.
- Cost: every mutation must re-sort; deleted/cancelled reservations must be pruned or skipped. More invariants to defend in a 45-minute round.

### Option C: interval tree

Balanced BST keyed by start, augmented with max-end in subtree. Query "any reservation overlapping I" in O(log R + k), k = overlaps found.

- Complexity: O(log R + k) search and insert.
- When it wins: genuinely large R per vehicle with heavy concurrent queries — production scale (a fleet tool with thousands of bookings per vehicle-year).
- Cost: the most code by far; augmentations must be maintained on rotation; overkill for an interview unless the interviewer explicitly pushes scale.

**Decision:** implement A, *be able to describe B and C*. Saying "O(n) scan today; sorted-list or interval tree when R grows; note both only pay off when R per vehicle is large, and the real bottleneck at scale is storage + locking, not the scan" is a strong answer.

### The concurrency angle

Check-then-act (`isAvailable` then `book`) races if two threads book the same vehicle concurrently. Fixes, in increasing order of interview credit:

1. Synchronize booking per vehicle (`synchronized` on vehicle id monitor).
2. Optimistic concurrency: reservation table row version / DB range-exclusion constraint (`EXCLUDE USING gist (vehicle_id WITH =, tsrange(...) WITH &&)` in Postgres).
3. Distributed lock (Redis) keyed by vehicleId for a multi-node booking service.

---

## Edge Cases and How the Code Handles Them

| Edge case | Behaviour |
|---|---|
| Booking that exactly repeats an existing window | `overlaps` catches identical ranges — rejected |
| Back-to-back booking (drop-off day == next pickup day) | **Allowed** — strict inequality in `overlaps` |
| Same-day pickup and drop-off (start == end) | `IllegalArgumentException` — must span >= 1 day |
| Inverted interval (start after end) | `IllegalArgumentException` from `Interval` |
| Book past date / null args | `IllegalArgumentException` with the field named in the message |
| Return before booked start / negative km | Rejected with message |
| Late return (actual return after booked drop-off) | Extra days charged + flat late fee via `LatePenaltyPricingStrategy` |
| Early return | Charged for booked days (no refund of unused days) — stated, simple, defensible |
| Cancel an IN_PROGRESS reservation | Thrown — must return instead |
| Cancel after completion | Thrown — state machine guard in `Reservation` |
| Double pickup / double return | Thrown by state guards |
| Return with km over allowance | Per-km overage line in the bill |
| Search returns vehicles already booked in window | Filtered out by `searchAvailable` |

---

## Trade-offs Accepted

1. **Single-threaded demo, documented concurrency story.** Locks everywhere would drown the modelling; the concurrency answer is written down instead, and the seams (`BookingService.book` check-then-act) are called out.
2. **Dates, not datetimes.** Rental days are the natural pricing unit and keep the interval math legible. A production system would use `ZonedDateTime` in UTC.
3. **Money as `double` with printf rounding.** Fine for a demo; production would use integer paise / `BigDecimal` — say this out loud, it's an easy interviewer point.
4. **In-memory IDs** (`AtomicLong`/UUID-ish strings) instead of a persistence layer — the repo convention for LLD practice.
5. **Store-scoped availability.** A vehicle belongs to exactly one store; one-way rentals are an extension question, not built-in scope creep.

---

## Complexity Summary

| Operation | Complexity |
|---|---|
| `Interval.overlaps` | O(1) |
| Booking conflict check | O(R) active reservations on the vehicle |
| Search available by type in a store | O(V_t x R) — V_t vehicles of that type |
| Pickup / return / cancel | O(1) map lookups (bill recomputation O(1)) |
| Inventory add/remove | O(1) amortized (list append / swap-remove) |

---

## How to Extend (Interview Talking Points)

- **One-way rentals**: add `returnStoreId` to `Reservation`; availability queries at the origin store account for vehicles in transit.
- **No-show expiry**: a sweep job (or `ScheduledExecutorService` in-memory) flips stale SCHEDULED reservations to EXPIRED.
- **Search by features**: add `Map<String, String> features` to `Vehicle` and a `Predicate<Vehicle>` filter parameter to search — stays out of the services' core logic.
- **Dynamic pricing**: a third `PricingStrategy` that reads demand curves — additive, no service change (OCP again).
- **Persistence**: reservation rows with a DB-level overlap-exclusion constraint make the check-then-act race impossible.
