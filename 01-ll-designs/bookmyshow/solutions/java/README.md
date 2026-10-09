# BookMyShow - Java Implementation

Java 11, no external libraries, no `package` declarations (one top-level class per file, repo convention).

## Class-by-Class Design

| File | Role |
|---|---|
| `BookMyShowDemo.java` | End-to-end narrative demo with `main` (11 `===` sections) |
| `SeatType.java` | REGULAR / PREMIUM / VIP — the pricing axis |
| `Seat.java` | One physical seat (row + number + type) — immutable catalog object |
| `SeatStatus.java` | AVAILABLE / LOCKED / BOOKED / EXPIRED — the lifecycle enum |
| `ShowSeat.java` | **The heart**: per-show availability of one seat; every transition (`tryLock`, `confirmBooking`, `release`, `expireIfLapsed`) is an `AtomicReference` CAS guarded inside the entity |
| `Screen.java` | Auditorium + seat layout; mints globally unique seat ids (`scr1-C2`) |
| `Theatre.java` | Multiplex owning screens; carries its city name (set at registration) |
| `City.java` | Search root: city + its theatres |
| `Movie.java` | Catalog title metadata |
| `Show.java` | Movie + screen + time + multiplier; **instantiates one `ShowSeat` per physical seat at construction** |
| `User.java` | Customer identity |
| `SeatHold.java` | Priced hold receipt (seats, user, total, expiry) — powerless by design; the engine re-validates at confirm |
| `Booking.java` | Confirmed paid booking — immutable, created only on payment success |
| `BookingService.java` | **The engine**: sorted-order pessimistic per-seat locking, all-or-nothing holds, payment-outside-locks confirm, idempotency cache, expiry sweeper |
| `SearchService.java` | Read-only city / movie-title / hour-window show search |
| `AdminService.java` | Catalog writes; `scheduleShow` fans out to search + booking in one call |
| `PriceCalculator.java` | `EnumMap<SeatType, Double>` base × show multiplier |
| `PaymentGateway.java` | Payment seam — business failure is a value, not an exception |
| `MockPaymentGateway.java` | Scriptable mock (queue of outcomes; success when empty) |
| `PaymentResult.java` | SUCCESS (payment id) / FAILURE (reason) value object |
| `BookingRepository.java` / `InMemoryBookingRepository.java` | Persistence seam + thread-safe in-memory impl |

Key invariants:

- **Availability lives in `ShowSeat`, never on `Seat`** — a seat free in the 6pm show can be booked in the 9pm show.
- **All seat transitions are CAS** inside `ShowSeat`; the pessimistic outer locks serialize contenders, the CAS guarantees no lost update can slip through any code path.
- **Per-seat mutexes are acquired in sorted seat-id order** everywhere (`lockSeats`, `confirmHold` validate + finalize, `releaseSeats`) — deadlock is structurally impossible.
- **No mutex is ever held across the gateway charge** — the LOCKED status + hold expiry protect seats during payment.
- **`confirmHold` is idempotent on bookingId** — double-click / webhook redelivery returns the original booking with no second charge.
- **A failed payment releases seats immediately** — no waiting for the sweeper when the outcome is already known.

## Run

The demo is multi-file, so Java 11 single-file launch (`java BookMyShowDemo.java`) does not work directly. Two options:

```bash
cd solutions/java

# Option 1 (standard): compile then run
javac *.java
java BookMyShowDemo

# Option 2 (no javac available): merge into one file and run.
# Uses the merge_java.py helper (hoists imports, concatenates classes
# in dependency order):
python3 /path/to/merge_java.py . BookMyShowDemo.java   # writes /tmp/merged_bookmyshow.java
java /tmp/merged_bookmyshow.java
```

Runtime is ~5 seconds (a deliberate 4-second wait so the 3-second hold expiry fires visibly; production holds are 5-10 minutes). Output is grouped under `=== Section ===` headers: setup, search, successful lock, contended-lock rejection, hold expiry via the sweeper, re-lock, payment + BOOKED confirmation, idempotent double-confirm, payment failure releasing seats, and admin scheduling a new show.

The sweeper runs on a daemon thread and `shutdown()` is called at the end, so the JVM exits promptly.

## Complexity

| Operation | Cost |
|---|---|
| `lockSeats` (k seats) | O(k log k) sort + O(k) lock/unlock + O(k) CAS |
| `confirmHold` | O(k) locks (validate + finalize) + 1 gateway call |
| Sweeper pass | O(seats across registered shows) |
| Search | O(shows) linear scan (indexes are the documented scale-up) |
| `ShowSeat` transition | O(1) CAS |

## Production Notes (interview talking points)

- Money as `double` is demo-only — use integer paise or `BigDecimal`.
- In-process `ReentrantLock` works for one JVM; multi-node maps to Redis `SETNX` per seat with TTL (the TTL replaces the sweeper) or DB `SELECT ... FOR UPDATE`. The sorted-order discipline carries over unchanged.
- Swap `MockPaymentGateway` for a real gateway (Razorpay order + capture, webhooks) behind the same interface; keep idempotency keys on order creation and capture.
- Refunds on cancel/`BOOKED -> AVAILABLE` is the natural extension — same compensating-transaction pattern, keyed on the same bookingId.
