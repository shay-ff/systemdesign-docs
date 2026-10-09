# BookMyShow (Movie Ticket Booking) - Low Level Design

A movie-ticket booking system with per-show seat inventory, a locking state machine (`AVAILABLE -> LOCKED -> BOOKED`, with expiry release), **pessimistic per-seat locks acquired in sorted order (deadlock-free)**, hold expiry via a background sweeper, payment behind an interface, idempotent confirmation, and city/movie/time show search.

## Problem Statement

Design a movie-ticket booking system (think BookMyShow / PVR / INOX) where:

- The company operates in **multiple cities**; each city has **theatres**; each theatre has **screens** with a fixed **seat layout** (regular / premium / VIP recliner).
- Admin **schedules shows**: a movie on a screen at a start time, with a per-show price multiplier.
- A user **searches shows by city / movie / time of day**.
- A user **locks seats** for a show: the seats become unavailable to everyone else for a short window (e.g. 3s in the demo, 5-10 min in production) while the user pays.
- **Two users must never book the same seat** for the same show — the crux.
- **Payment happens while the seats are held**; on success seats become **BOOKED**, on failure seats are **released** immediately.
- **Unpaid holds expire** (user closed the tab) — seats return to AVAILABLE automatically.
- **Duplicate Pay clicks must not double-charge** or double-book (idempotency).
- The system must survive **concurrent bookings on the same and overlapping seat sets** without deadlock.

## Key Features

- **Show-scoped seat inventory**: availability is a property of (show, seat), never of the physical seat — a seat free in the 6pm show can be booked in the 9pm show.
- **ShowSeat state machine** (the heart): `AVAILABLE -> LOCKED -> BOOKED`, plus `LOCKED -> AVAILABLE` (payment failure / cancel) and `LOCKED -> EXPIRED -> AVAILABLE` (hold timed out).
- **Pessimistic per-seat locking** in `BookingService`: per-seat `ReentrantLock`s acquired in **sorted seat-id order** — the deadlock-prevention move interviewers want unprompted.
- **All-or-nothing seat holds**: if any requested seat is unavailable, nothing is locked.
- **Hold expiry sweeper**: `ScheduledExecutorService` releases lapsed holds — no stranded inventory.
- **Idempotent confirmation**: bookingId-keyed guard; double-click / webhook redelivery returns the original booking, no second charge.
- **Payment behind an interface**: `PaymentGateway` with a scriptable mock (success/failure per call) — swap in Razorpay without touching booking logic.
- **Pricing**: seat-type base price map × per-show multiplier.
- **Search**: in-memory filtering by city / movie title / hour window.

## Clarifying Questions an Interviewer Expects You to Ask

1. **For how long are seats locked while the user pays?**
   A short window — 5-10 minutes in production (3 seconds in the demo). The hold is the LOCKED state with an expiry instant, not a mutex.
2. **What if two users select the same seat concurrently?**
   Pessimistic per-seat locks serialize them: the loser waits milliseconds, then reads LOCKED and is rejected (all-or-nothing). The design document in explanation.md compares this with optimistic CAS and explains why pessimistic wins here.
3. **What happens to a hold if the user never pays?**
   It expires. The sweeper (or a Redis TTL / DB job in production) moves it LOCKED -> EXPIRED -> AVAILABLE so the seat is sellable again.
4. **Can a seat be booked for a different show at the same time?**
   Yes — seat state is per (show, seat): `ShowSeat` rows are independent across shows.
5. **What about payment latency?**
   The gateway call runs OUTSIDE the per-seat mutexes; the seat's LOCKED status (plus expiry) protects it. Holding mutexes across a seconds-long gateway call would block every other booking on those seats.
6. **What if payment succeeds but the hold expired meanwhile?**
   The confirm path re-validates under lock; a lost hold after a successful charge is the "paid but no seats" edge — refund and notify (documented in explanation.md).
7. **Double-click on Pay? Retry of the same webhook?**
   Idempotency keyed on bookingId: first call wins, later calls return the original booking.
8. **Can a booking be cancelled after confirmation?**
   Out of demo scope but the state machine extends: `BOOKED -> (cancel) -> AVAILABLE` plus a refund. Say this; do not build it unless asked.
9. **Max seats per booking?**
   10 (a real BMS constraint). Enforced with a clear message.
10. **Currency and rounding?**
   INR (`Rs.` in output), amounts rounded to 2 decimals.

## Core Entities

| Entity | Responsibility |
|---|---|
| `City` | Search root: a city and its theatres |
| `Theatre` | A multiplex in a city; owns screens |
| `Screen` | An auditorium; owns the physical seat layout (globally unique seat ids like `scr1-C2`) |
| `Seat` | One physical seat: row + number + `SeatType` — immutable catalog object |
| `SeatType` | Enum: REGULAR / PREMIUM / VIP |
| `Movie` | Catalog title metadata (language, duration, genre) |
| `Show` | Movie + screen + start time + price multiplier; **instantiates one `ShowSeat` per physical seat** |
| `ShowSeat` | **Per-show availability of one seat**; owns the state machine (AVAILABLE / LOCKED / BOOKED / EXPIRED) with CAS-guarded transitions |
| `SeatStatus` | Lifecycle enum for the above |
| `SeatHold` | Priced hold receipt: seats + user + total + expiry instant |
| `Booking` | Confirmed (paid) booking: seats, total, payment id — immutable |
| `User` | Customer identity |
| `BookingService` | **The engine**: lock seats (sorted-order pessimistic locks), confirm (payment + idempotency), release, sweep expiry |
| `SearchService` | Find shows by city / movie / hour window |
| `AdminService` | Register cities/theatres/screens; schedule shows (fans out to search + booking) |
| `PriceCalculator` | Seat-type base price × show multiplier |
| `PaymentGateway` / `MockPaymentGateway` | Payment seam + scriptable mock |
| `PaymentResult` | Charge outcome (SUCCESS/FAILURE + reason) |
| `BookingRepository` / `InMemoryBookingRepository` | Persistence seam for confirmed bookings |
| `BookMyShowDemo` | End-to-end narrative demo |

## Design Patterns Used (and why)

- **State machine in the entity (`ShowSeat`)** — every transition (`tryLock`, `confirmBooking`, `release`, `expireIfLapsed`) is guarded *inside* the seat row via `AtomicReference.compareAndSet`. The object protects its own invariants; services cannot corrupt state. This is the single most important choice in this problem.
- **Facade (`BookingService`)** — controllers talk to one entry point: lock, confirm, cancel, sweep. Payment, pricing, persistence and locking hide behind it.
- **Strategy / dependency inversion (`PaymentGateway`)** — the charge is an interface; the mock is scriptable and swappable for a real gateway. Booking logic never sees gateway details.
- **Repository pattern (`BookingRepository`)** — in-memory today, DB tomorrow, booking engine unchanged.
- **Observer-flavoured fan-out (`AdminService.scheduleShow`)** — scheduling a show simultaneously makes it searchable (`SearchService`) and bookable (`BookingService.registerShow`) from one call — no "searchable but not bookable" window.
- **Deterministic lock ordering** — sorted seat ids for lock acquisition: a concurrency discipline (not a GoF pattern) that eliminates deadlock; the idea mirrors DB row-lock ordering.

Rejected alternatives and trade-off discussion live in [explanation.md](explanation.md).

## How to Run

```bash
cd solutions/java

# Option 1: compile then run (standard multi-file path)
javac *.java
java BookMyShowDemo

# Option 2: merge into one file and run (no javac needed - see solutions/java/README.md)
#   uses the merge_java.py trick to hoist imports and concatenate in dependency order
```

Total runtime ~5 seconds (a deliberate 4s wait so the 3-second hold expiry is visible).

## Time Complexity

- Locking k seats: O(k log k) (sort) + O(k) lock/unlock
- Availability check inside the lock: O(k) CAS attempts
- Sweeper pass: O(total show seats)
- Search by city/movie/hour: O(shows) — indexes are the scale-up (see explanation.md)
- Confirm: O(k) locks + 1 gateway call + O(k) CAS flips

## Interview Extension Questions

1. How would you persist this? (shows + show_seats tables; the lock becomes `SELECT ... FOR UPDATE` or an optimistic `UPDATE ... WHERE status='AVAILABLE'`.)
2. What if the gateway is slow — how do you stop seat blocking? (This design already answers: holds, not mutexes, protect seats during payment.)
3. Distributed booking service — do in-process `ReentrantLock`s still work? (No: Redis `SETNX` per seat with TTL, or DB row locks; the sorted acquisition order carries over unchanged.)
4. How would you stop a user/bot from holding seats forever without paying? (Expiry is one half; add per-user concurrent-hold limits and rate limiting.)
5. Seat-map rendering for 10k concurrent users? (Cache per-show seat maps; publish deltas via WebSocket; invalidate on every transition.)
6. How do you handle refunds + cancellations after BOOKED? (New transition + compensating payment; idempotency keys again.)
7. Show recommendations / "fastest booking" flows? (Search indexes, city/movie partitions.)
8. How do you test the race conditions? (Deterministic stress loops in the demo style; `CountDownLatch` to align threads at the lock.)

## Files Structure

```
bookmyshow/
├── README.md              # This file
├── design.puml            # PlantUML class diagram
├── explanation.md         # Design walkthrough, concurrency deep-dive, edge cases
└── solutions/
    └── java/
        ├── README.md      # Class-by-class notes + run instructions
        ├── BookMyShowDemo.java
        ├── SeatType.java / Seat.java / SeatStatus.java / ShowSeat.java
        ├── Screen.java / Theatre.java / City.java
        ├── Movie.java / Show.java
        ├── User.java
        ├── SeatHold.java / Booking.java
        ├── BookingService.java            # the engine
        ├── SearchService.java / AdminService.java
        ├── PriceCalculator.java
        ├── PaymentGateway.java / MockPaymentGateway.java / PaymentResult.java
        └── BookingRepository.java / InMemoryBookingRepository.java
```

See [explanation.md](explanation.md) for the full design walkthrough — including the pessimistic-vs-optimistic concurrency decision.
