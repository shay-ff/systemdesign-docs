# Car Rental System - Java Implementation

Java 11, no external libraries, no `package` declarations (one top-level class per file, repo convention).

## Class-by-Class Design

| File | Role |
|---|---|
| `CarRentalDemo.java` | End-to-end narrative demo with `main` |
| `Interval.java` | Immutable rental window `[start, end)` — start = pickup day, end = drop-off day; `overlaps()` is the double-booking crux (strict `<` so touching windows are allowed) |
| `Vehicle.java` | Abstract base: id, plate, type, per-day rate, daily km allowance; validates all inputs |
| `Car.java` / `Suv.java` / `Bike.java` / `Truck.java` | Variants — only supply parameters (OCP: no service changes needed for these) |
| `VehicleType.java` | CAR / SUV / BIKE / TRUCK — browse axis and inventory index key |
| `Location.java` | Immutable city/pincode/address value object |
| `Store.java` | Per-city store; owns inventory + reservation log; `isAvailable` / `findConflictingReservation` (O(R) overlap scan) |
| `VehicleInventory.java` | Type-keyed fleet index; `findByType` for browsing |
| `Reservation.java` | Owns its state machine (SCHEDULED -> IN_PROGRESS -> COMPLETED / CANCELLED) + nested immutable `Bill` breakdown |
| `ReservationStatus.java` | Lifecycle enum |
| `BookingService.java` | Facade: search, book (overlap check + payment), pickup, return (final bill), cancel (refund) |
| `PricingStrategy.java` | Strategy interface for bill computation |
| `PerDayPricingStrategy.java` | Base per-day rate + per-km over allowance |
| `LatePenaltyPricingStrategy.java` | Wraps a base strategy; adds extra-day + flat late fee (decorator-flavoured) |
| `PaymentService.java` / `Payment.java` | Mock gateway: charge / refund + ledger |
| `Customer.java` | Renter identity |

Key invariants:

- No two **active** (SCHEDULED/IN_PROGRESS) reservations on one vehicle may hold the vehicle on the same day — enforced in `Store.findConflictingReservation` and re-checked in `BookingService.book`.
- Windows are `[start, end)`: drop-off day is end, and the vehicle is free again that day, so a drop-off on Oct 4 followed by a pickup on Oct 4 is a valid back-to-back pair.
- All state transitions are guarded inside `Reservation` (pickup only from SCHEDULED, return only from IN_PROGRESS, cancel only from SCHEDULED).

## Run

```bash
cd solutions/java

# Option 1: single-file source launch (Java 11+)
java CarRentalDemo.java

# Option 2: compile then run
javac *.java
java CarRentalDemo
```

Runtime is well under a second (no sleeps). Output is grouped under `=== Section N ===` headers.

## Complexity

| Operation | Cost |
|---|---|
| `Interval.overlaps` | O(1) |
| Booking conflict check | O(R), R = active reservations on the vehicle |
| Search available by type in a store | O(V_t x R) |
| Pickup / return / cancel | O(1) map lookups |

Upgrade path for the conflict check (sorted list + binary search, interval tree) is discussed in the problem's `explanation.md`.

## Production Notes (interview talking points)

- Money as `double` is demo-only — use integer paise or `BigDecimal` in production.
- `book()` is check-then-act: make it atomic with a per-vehicle lock or a DB range-exclusion constraint.
- Swap the mock `PaymentService` for a real gateway behind the same interface.
