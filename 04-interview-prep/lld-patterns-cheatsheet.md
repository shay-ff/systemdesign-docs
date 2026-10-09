# LLD Patterns Cheatsheet — Pattern → Problem Map

> 🧭 **Navigation**: [← Interview Prep](README.md) | [📍 Full Navigation](../NAVIGATION.md)

A single-page reference that maps each design pattern to **when it applies**,
**the classic LLD problems that need it**, and **where it lives in this repo**.
The point is pattern *recognition* — when an interviewer describes a problem,
you should hear the pattern.

For the theory of each pattern (structure, UML, trade-offs), see
[`../00-foundations/design-patterns.md`](../00-foundations/design-patterns.md).
This file is the applied companion.

---

## The core patterns (learn these cold)

### Strategy — "the algorithm varies"

**Signal:** an algorithm/policy/rule that could reasonably have several
implementations, and the caller shouldn't care which.

**Shape:** an interface + concrete implementations, injected into the context.

| Problem | The varying thing | Repo example |
|---|---|---|
| Parking Lot | Pricing (hourly, weekend, flat) | `parking_lot/solutions/java/PricingPolicy.java` |
| Splitwise | Split type (equal, percent, exact) | `splitwise/solutions/java/Split.java` |
| Elevator | Scheduling (FCFS, SCAN/LOOK) | `elevator/solutions/java/ElevatorScheduler.java` |
| Snake & Ladder | Win rule (exact, bounce-back) | `snake_ladder/solutions/java/WinRule.java` |
| Car Rental | Pricing (per-day, per-km, late penalty) | `car_rental/solutions/java/PricingStrategy.java` |
| Rate Limiter | Algorithm (token bucket, sliding window) | `rate_limiter/solutions/java/` |
| Notification | Retry policy, channel senders | `notification_system/solutions/java/RetryPolicy.java` |

**Interview line:** "Pricing is a policy that varies, so I'll put it behind a
`PricingStrategy` interface — adding weekend pricing is a new class, not an edit
to ParkingLot."

---

### State — "behavior changes with state"

**Signal:** an object with distinct modes where the legal operations *differ per
mode*, and transitions between modes follow rules.

**Shape:** a state interface + one class/enum per state; the context delegates.

| Problem | The states | Repo example |
|---|---|---|
| Vending Machine | IDLE → HAS_MONEY → DISPENSING | `vending_machine/solutions/java/VendingState.java` |
| ATM | IDLE → CARD_INSERTED → PIN_VERIFIED → ... | `atm/solutions/java/AtmState.java` |
| BookMyShow seat | AVAILABLE → LOCKED → BOOKED | `bookmyshow/solutions/java/SeatStatus.java` |
| Elevator car | IDLE → MOVING_UP/DOWN → DOORS_OPEN | `elevator/solutions/java/ElevatorState.java` |

**Interview line:** "The machine's behavior changes completely by state, so I'll
model explicit states with a state pattern — illegal transitions are rejected by
construction."

**Strategy vs. State:** Strategy is chosen *by the client* and the object
doesn't change it; State changes *internally* as the object transitions.

---

### Observer — "notify many when one changes"

**Signal:** one event must fan out to many interested parties, especially if
they register dynamically.

**Shape:** a subject holding a list of observers; `notify()` on change.

| Problem | The event | Repo example |
|---|---|---|
| Logger | A log message reaching sinks | `logger_framework/solutions/java/LogObserver.java` |
| Notification | A notification to channels | `notification_system/solutions/java/NotificationSender.java` |
| Calendar | Invite/RSVP/cancel events | `calendar_scheduler/solutions/java/NotificationService.java` |
| Parking Lot | Availability changes | `parking_lot/` (observer mention) |

**Interview line:** "Sinks can be added at runtime, so the manager broadcasts to
registered observers — adding a sink doesn't touch the core."

---

### Chain of Responsibility — "N handlers, each independently interested"

**Signal:** a request may be handled by one of several handlers, and you want to
add handlers without a giant `switch`.

| Problem | The chain | Repo example |
|---|---|---|
| Logger | DEBUG → INFO → WARN → ERROR | `logger_framework/solutions/java/Logger.java` |

**Interview line:** "Each log level is a link that decides whether the message
is its concern and passes it on — adding a level is one link, no `switch`."

---

### Decorator — "add behavior without editing the class"

**Signal:** you want to compose optional behaviors around an existing object.

| Problem | The decoration | Repo example |
|---|---|---|
| Snake & Ladder | Crooked dice (never rolls 6) | `snake_ladder/solutions/java/CrookedDiceDecorator.java` |
| Car Rental | Late-return penalty pricing | `car_rental/solutions/java/LatePenaltyPricingStrategy.java` |

**Interview line:** "A crooked die is a normal die wrapped so it re-rolls 6s —
the game calls `roll()` and never knows the difference."

---

### Factory — "creation decoupled from use"

**Signal:** the concrete type to instantiate depends on input/config, and the
caller shouldn't `new` concrete classes.

| Problem | What's created | Repo example |
|---|---|---|
| Logger | Sinks from config specs | `logger_framework/solutions/java/SinkFactory.java` |
| Parking Lot | Vehicles/spots by type | `parking_lot/` |
| ATM | Transaction commands | `atm/` |

**Interview line:** "The config says 'FILE:app.log'; the factory turns that into
a `FileSink` — the core never news up a concrete sink."

---

### Command — "encapsulate a request as an object"

**Signal:** you need to queue, log, undo, or audit operations.

| Problem | The commands | Repo example |
|---|---|---|
| ATM | Withdraw/Deposit/BalanceInquiry | `atm/solutions/java/WithdrawCommand.java` |
| Message Queue | Producer/consumer messages | `message_queue/` |

**Interview line:** "Each ATM transaction is a command object, which gives me an
audit trail and makes undo possible."

---

### Adapter — "make incompatible interfaces work together"

**Signal:** two interfaces that should collaborate but have different shapes.

| Problem | The adaptation | Repo example |
|---|---|---|
| Logger | `LogSink` as a `LogObserver` | `logger_framework/solutions/java/SinkObserverAdapter.java` |

**Interview line:** "I keep the sink SPI minimal and adapt it to the observer
interface — the two roles stay orthogonal."

---

### Facade — "one entry point over a subsystem"

**Signal:** many collaborating classes that callers shouldn't orchestrate
themselves.

| Problem | The facade | Repo example |
|---|---|---|
| Splitwise | `SplitwiseService` | `splitwise/solutions/java/SplitwiseService.java` |
| BookMyShow | `BookingService` | `bookmyshow/solutions/java/BookingService.java` |
| Calendar | `CalendarService` | `calendar_scheduler/solutions/java/CalendarService.java` |

**Interview line:** "Callers talk to one facade; the balance, ledger, and
simplification services are coordinated behind it."

---

### Template Method — "fix the skeleton, vary the steps"

**Signal:** an algorithm's structure is fixed but some steps differ.

| Problem | The template | Repo example |
|---|---|---|
| Notification | Message formatting per channel | `notification_system/` |
| ATM | Transaction flow | `atm/` |

---

### Singleton — "exactly one shared instance" (use sparingly)

**Signal:** genuinely one shared resource (config, a registry). **Overused** —
prefer dependency injection in most cases.

| Problem | The singleton | Repo example |
|---|---|---|
| ATM | Machine instance | `atm/` (singleton mention) |

**Interview line:** "I *could* make the config a singleton, but I'd rather inject
it — singletons make testing hard." (Saying this earns points.)

---

### Builder — "construct complex objects step by step"

**Signal:** an object with many optional fields, or immutable construction.

| Problem | The builder | Repo example |
|---|---|---|
| Logger | `LoggerConfig.Builder` | `logger_framework/solutions/java/LoggerConfig.java` |

---

### Repository — "a persistence seam"

**Signal:** you want to decouple domain logic from storage (and swap in-memory
for a DB later).

| Problem | The repository | Repo example |
|---|---|---|
| Splitwise | User/Group repos | `splitwise/solutions/java/UserRepository.java` |
| BookMyShow | Booking repo | `bookmyshow/solutions/java/InMemoryBookingRepository.java` |

**Interview line:** "Repos are interfaces with in-memory impls — in an LLD round
that's the storage seam, swappable for a DB."

---

## Pattern → problem quick lookup (by problem)

| Problem | Patterns that fit |
|---|---|
| LRU Cache | (data-structure problem — no pattern needed; say so) |
| Rate Limiter | Strategy (algorithms), Singleton (limiter registry) |
| Consistent Hashing | (data-structure problem) |
| Message Queue | Command, Producer-Consumer |
| Bloom Filter | (data-structure problem) |
| Parking Lot | Strategy (pricing), Factory (vehicles), Observer (availability), Builder |
| URL Shortener | Strategy (encoding), Repository |
| Splitwise | Strategy (split types), Facade, Repository |
| BookMyShow | State (seat), Strategy (pricing), Repository, Observer |
| Elevator | State (car), Strategy (scheduling), Facade |
| Vending Machine | **State** (headline), Factory |
| Logger | **Chain of Responsibility** (headline), Observer, Factory, Adapter, Builder |
| Notification | Observer, Strategy (retry), Template Method, Command |
| Snake & Ladder | **Decorator** (dice), Strategy (win rule) |
| ATM | **State** + **Command** (headline), Factory, Singleton |
| Car Rental | Strategy (pricing), Repository |
| Calendar | Observer (notifications), Strategy (recurrence), Facade |

---

## SOLID, applied (the one-liners that earn points)

- **S**RP — "This class has one reason to change: pricing."
- **O**CP — "Adding a payment method is a new class, not an edit to the core."
- **L**SP — "Any `Split` subtype is usable wherever `Split` is expected."
- **I**SP — "`LogSink` has three methods; I didn't force sinks to implement
  observer methods they don't need."
- **D**IP — "`ParkingLot` depends on the `PricingPolicy` interface, not a
  concrete class."

Weave these into your design narration — they're the vocabulary interviewers are
listening for.

---

## The meta-lesson

**Patterns are names for solutions to recurring problems — not goals.**
A design with zero patterns is fine if nothing varies. A design with five
patterns for a problem that needs one reads as inexperienced. The skill is
recognising *when a seam is needed* (because something varies) and reaching for
the pattern that fits that seam.

The single most common LLD insight: **find the part most likely to change and
put an interface there.** Everything else follows.

---

*Related: [LLD Round Guide](lld-round-guide.md) ·
[Machine Coding Guide](machine-coding-guide.md) ·
[Design Patterns (theory)](../00-foundations/design-patterns.md)*
