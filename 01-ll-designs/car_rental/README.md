# Car Rental System - Low Level Design

A multi-city, multi-store car rental management system with per-store vehicle inventory, reservation lifecycle management, double-booking prevention via interval overlap detection, and pluggable pricing strategies.

## Problem Statement

Design a car rental system (think Zoomcar / Hertz) where:

- The company operates **multiple stores across multiple cities**.
- Each store maintains its **own vehicle inventory** (cars, SUVs, bikes, trucks...).
- A user **browses vehicles by type at a store** for a date range.
- A user **books a vehicle** for an interval; the system must **prevent double-booking**
  (two reservations cannot hold the same vehicle at overlapping times).
- The user **picks up** the vehicle (reservation becomes IN_PROGRESS) and **returns** it.
  Late returns incur a **penalty** on top of base pricing.
- The user can **cancel** a scheduled reservation.
- Pricing is **configurable**: per-day base price with a km allowance, plus per-km charges
  beyond the allowance. Different pricing strategies can be swapped in without touching
  booking code (Open/Closed Principle).

## Key Features

- **Multi-store, multi-city operations**: stores are discovered by city; inventory is per store.
- **Vehicle hierarchy**: abstract `Vehicle` with concrete variants (`Car`, `Suv`, `Bike`, `Truck`) — adding new vehicle types requires **zero changes** to booking/inventory services.
- **Reservation lifecycle**: `SCHEDULED -> IN_PROGRESS -> COMPLETED`, plus `CANCELLED` from SCHEDULED.
- **Double-booking prevention**: the crux of this problem. An `Interval` class with a clean `overlaps()` check; every booking validates against the vehicle's active reservations.
- **Pricing strategies**: `PerDayPricingStrategy` (base per-day + per-km after allowance) and `LatePenaltyDecorator`-style surcharges via a second strategy slot; late-return fee shown in the demo.
- **Payment mock**: in-memory `PaymentService` simulating card charge + refund on cancellation.
- **Location & store management**: `VehicleInventory` per store with `findByType`.

## Clarifying Questions an Interviewer Expects You to Ask

1. **Can one vehicle have multiple future reservations?**
   Yes — non-overlapping back-to-back reservations on the same vehicle are allowed (a car dropped off Monday morning can be picked up again Monday evening). Only windows that share a held day conflict.
2. **Do reservations on adjacent windows (drop-off day == next pickup day) conflict?**
   No. Windows are `[start, end)` — pickup Oct 1 with drop-off Oct 3 frees the vehicle on Oct 3, so pickup Oct 3 is fine. The overlap check is strict: `thisStart < otherEnd && otherStart < thisEnd`.
3. **When is a vehicle actually handed over?**
   At pickup: `SCHEDULED -> IN_PROGRESS`. Return completes it and triggers final pricing (possibly with a late penalty).
4. **What pricing inputs exist?**
   Booked days, allowance km, actual km driven, actual return date vs booked return date.
5. **Is inventory per store or global?**
   Per store. A Bengaluru vehicle cannot be booked from the Delhi store.
6. **What happens on cancel?**
   `SCHEDULED -> CANCELLED`; full refund via the payment mock. An IN_PROGRESS trip cannot be cancelled (must be returned).
7. **Currency and rounding?**
   INR (`INR` suffix in output), amounts rounded to 2 decimals.
8. **How do you detect conflicts at scale?**
   O(n) scan per vehicle is fine for an interview; discuss interval trees / sorted list + binary search as scale-ups (see explanation.md).

## Core Entities

| Entity | Responsibility |
|---|---|
| `Vehicle` (abstract) | Identity, type, price anchor, km allowance; concrete variants add behaviour |
| `Car` / `Suv` / `Bike` / `Truck` | Vehicle variants |
| `VehicleType` | Enum: CAR, SUV, BIKE, TRUCK |
| `Location` | Immutable city/pincode/address |
| `Store` | One city location; owns `VehicleInventory` + reservation log |
| `VehicleInventory` | Add/remove/search vehicles by type and availability window |
| `Reservation` | Vehicle + customer + interval + status + price breakdown |
| `ReservationStatus` | Enum lifecycle |
| `Interval` | Rental window `[start, end)` (start = pickup day, end = drop-off day) with `overlaps()` — the conflict-detection core |
| `BookingService` | Creates/updates/cancels reservations; enforces overlap rule |
| `PricingStrategy` | Interface; `PerDayPricingStrategy`, `LatePenaltyPricingStrategy` |
| `Payment` / `PaymentService` | Mock payment record + processor (charge / refund) |
| `Customer` | Renter identity |
| `CarRentalDemo` | End-to-end narrative demo |

## Design Patterns Used (and why)

- **Strategy Pattern — pricing.** Per-day-plus-allowance pricing, weekend surge, and late-return penalties are *algorithms that vary independently* of booking flow. `PricingStrategy` is injected into `BookingService`, so a new pricing model is a new class, not a modified service (OCP demonstrated by adding `Bike`/`Truck` and a penalty strategy without touching services).
- **Decorator-flavoured surcharge — late penalty.** `LatePenaltyPricingStrategy` *wraps* a base strategy and adds a surcharge line, instead of duplicating the base calculation.
- **Composition over inheritance for Store -> Inventory.** A store *has an* inventory, not *is an* inventory; keeps store focused on reservation policy.
- **Template-method-lite on Vehicle.** Abstract `Vehicle` fixes the contract (type + allowance + base rate); variants only supply parameters. Services depend on the abstraction, so `Bike`/`Truck` were added with zero service changes.
- **Facade — BookingService.** Customers and the demo talk to one service; reservation, conflict check, pricing and payment coordination hide behind it.

Rejected alternatives and trade-off discussion live in [explanation.md](explanation.md).

## How to Run

```bash
cd solutions/java

# Option 1: single-file source launch (Java 11+)
java CarRentalDemo.java

# Option 2: compile then run
javac *.java
java CarRentalDemo
```

Total runtime: well under a second (no sleeps).

## Time Complexity

- `Interval.overlaps()`: O(1)
- Conflict check for one booking: O(R) where R = active reservations on that vehicle (see interval-tree discussion for O(log R + k))
- Inventory search by type: O(V) per store
- Cancellation / return: O(1) map lookups

## Interview Extension Questions

1. How would you make booking **thread-safe**? (Lock per vehicle, or optimistic versioning; beware check-then-act races across threads.)
2. How would you detect conflicts faster than O(n) per vehicle? (Interval tree: O(log n + k); or keep per-vehicle sorted intervals + `Collections.binarySearch`.)
3. How would you persist this? (Vehicle + reservation tables; the overlap rule becomes a DB exclusion constraint / range type.)
4. How do you handle a vehicle that comes back to a **different store**? (One-way rentals: reservation gains a `returnStoreId`; availability queries span both stores.)
5. Add surge / weekend pricing. (New `PricingStrategy` — no service changes; that's the OCP win.)
6. Add trucks — did any service code change? (No — that's the LSP/OCP talking point.)
7. What if a customer no-shows? (State machine extension: SCHEDULED -> EXPIRED via a scheduled sweep.)
8. How would search scale to thousands of stores? (Store index by city; cache per-store inventory; shard by city.)

## Files Structure

```
car_rental/
├── README.md              # This file
├── design.puml            # PlantUML class diagram
├── explanation.md         # Design walkthrough, trade-offs, edge cases
└── solutions/
    └── java/
        ├── README.md      # Class-by-class notes + run instructions
        ├── CarRentalDemo.java
        ├── Vehicle.java / Car.java / Suv.java / Bike.java / Truck.java
        ├── VehicleType.java / ReservationStatus.java
        ├── Location.java / Store.java / VehicleInventory.java
        ├── Interval.java / Reservation.java
        ├── BookingService.java / PricingStrategy.java
        ├── PerDayPricingStrategy.java / LatePenaltyPricingStrategy.java
        ├── Payment.java / PaymentService.java
        └── Customer.java
```

See [explanation.md](explanation.md) for the full design walkthrough.
