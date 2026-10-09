# Elevator System — Design Explanation

A walkthrough of every entity, why each pattern was chosen, what was rejected, and the edge cases that separate a passing LLD from a great one. The two cruxes of this problem: **request duality** (hall calls know intent, not destination) and **FCFS vs SCAN/LOOK behaviour** (what the scheduler does when it sweeps past an intermediate floor).

## The Problem in One Sentence

Passengers demand rides through two different channels (floor buttons with direction intent, cab buttons with destinations); cars must run a safe state machine while choosing stops via a swappable policy, and a building must coordinate multiple cars — all simulatable deterministically.

---

## Entity-by-Entity Rationale

### `ElevatorRequest` + `RequestSource` — the duality crux

The single most important modelling decision in this problem. There are TWO kinds of demand, and they know different things:

- **Hall call** (`FLOOR_BUTTON`): someone at floor 4 pressed Up. We know the pickup floor (4) and the travel intent (UP). We do NOT know the destination — it is revealed only when they board and press a cab button.
- **Cab call** (`CAB_BUTTON`): someone aboard pressed 7. We know the destination (7). The passenger is already committed; there is no pickup to plan.

Why this matters so much: a scheduler that treats both as "a request for floor N" will happily stop for a down-going hall passenger while sweeping up — boarding them and carrying them the wrong way. The intent field exists to prevent exactly that.

`ElevatorRequest` is immutable with two named factories (`floorButton(floor, direction)`, `cabButton(floor)`). The factories enforce the invariants at construction — a hall call without a direction or with a "none" direction throws immediately. A leaky constructor (direction nullable for cab calls) would push that validation to every consumer instead; factories keep it in one place. Monotonic ids from an `AtomicLong` preserve arrival order — which is exactly what FCFS needs and what the car's append-only list maintains for free.

### `ElevatorState` — enum state machine

`IDLE, MOVING_UP, MOVING_DOWN, DOORS_OPEN`. Two deliberate decisions:

1. **MOVING_UP and MOVING_DOWN are separate states**, not one MOVING state plus a direction field. The legal behaviour differs by direction (a LOOK sweep must not reverse until its sweep is exhausted), and separate states make the transition table explicit — the thing interviewers ask you to draw. One MOVING state forces reversal logic into scattered if-chains; two states keep it inspectable.
2. **No DOORS_CLOSED state.** "Closed and about to move" is behaviourally the same as MOVING once the next tick fires. Fewer states = fewer illegal transitions to guard. `DOORS_OPEN` IS a state because it carries a safety invariant: the car must not move while doors are open. The tick loop structurally enforces this — a `DOORS_OPEN` tick can only close doors, never move.

The full transition table (print this in the interview):

| Current | Event | Next |
|---|---|---|
| IDLE | scheduler targets current floor | DOORS_OPEN |
| IDLE | scheduler targets other floor | MOVING_UP / MOVING_DOWN (by direction) |
| MOVING_UP | arrives at target | DOORS_OPEN |
| MOVING_DOWN | arrives at target | DOORS_OPEN |
| MOVING_* | not at target yet | MOVING_* (one floor per tick) |
| DOORS_OPEN | doors close, work remains | IDLE → re-planned next tick (MOVING_* on the following tick) |
| DOORS_OPEN | doors close, no work | IDLE |

### `Direction` — intent and travel in one enum

Used for both travel direction of the car and intent of a hall call. `Direction.between(from, to)` derives travel direction; validation rejects identical floors (no direction exists). Keeping IDLE/none OUT of this enum avoids the classic bug where comparison logic treats an idle car as "moving up". `isMoving()` is the only null-safe check.

### `Floor` — value object

A floor number plus the building's floor count, validated at construction. Why a class and not a bare int: (1) range validation lives in exactly one place, so no service re-checks legality; (2) a floor is meaningless without its building context — `Floor(15, 10)` is unconstructable rather than a runtime surprise; (3) equals/hashCode make it a proper value object. The car and the demo mostly pass ints (the scheduler contract is int-based for brevity), but `Floor` demonstrates the pattern and guards the demo's guardrail section.

### `ElevatorScheduler` — the strategy seam

```java
Integer nextStop(int currentFloor, Direction direction, List<ElevatorRequest> requests);
```

One method, three inputs, one output (target floor or null = rest this tick). The car owns state and data; the scheduler owns ONLY the ordering decision. This division is what makes the FCFS→SCAN demo honest: the car stops **only** at the floor its scheduler names. The anti-pattern (seen constantly in interview code) is a car that stops at "any floor with a pending request" — that has silently hard-coded SCAN and will ignore its injected FCFS strategy.

### `FcfsScheduler` — the honest baseline

The head of the arrival-ordered list, always. It deliberately ignores intermediate floors and direction intent — that naivety IS the teaching point. Implemented in three lines, it exists so the demo can show the same scenario under two policies and print the difference.

The FCFS flaw in numbers (the demo's Section 1): hall calls 2F(UP), 5F(DOWN), 3F(UP) arriving in that order. FCFS shuttles 2 → 5 → 3: it sweeps **past floor 3** (which wanted UP!) on its way to 5, then doubles back — 6 floors travelled. Meera at floor 3 waits a full round trip for a pickup the cab performed while driving past her.

### `ScanScheduler` — SCAN in its LOOK variant

Pure SCAN sweeps to the building's end (floor 1 or N) before reversing, always. LOOK reverses at the **last outstanding request** instead. We implement LOOK and say "SCAN" out loud — interviewers accept it instantly when you can name the difference.

**Why LOOK beats SCAN at the extremes** (the standard probe): a 15-floor building, one pending request at floor 2, cab sweeping up from floor 5. Pure SCAN travels to 15 first (10 wasted floors), then descends 13 more to 2 — 23 floors for one errand. LOOK reverses at the topmost outstanding target and goes straight down — 3 floors. SCAN's travel is O(building height) per sweep regardless of demand; LOOK's travel is bounded by the request set. In a 40-floor tower running 2-floor errands, SCAN wastes 40+ floors per round trip; LOOK wastes zero. Real elevator controllers run LOOK.

The scheduler's decision order (each step load-bearing — remove one and a bug appears):

1. **Parked car with requests at its own floor** → open doors here. Never drive away from people standing at your doors.
2. **Sweeping** → nearest strictly-ahead stop that is a cab call (any) or a hall call whose intent **matches** the sweep. This is the payoff: the up-sweep collects every up-going intermediate floor that FCFS would defer.
3. **No matching stops ahead, but wrong-intent requests ahead** → travel to the **farthest** such floor (the sweep boundary). Those passengers board there for the return sweep. **Skipping this step is the classic starvation bug**: a "floor 8, going down" call above an up-sweeping cab is invisible to intent-filtered lookups and waits forever. The demo's Section 5 prints this exact scenario.
4. **Sweep exhausted** → reverse: nearest matching-intent stop in the opposite half-space, else farthest request that way, else (if the only remaining work is at the current floor) stop and board — the return sweep starts here.

Why the intent filter at all (step 2)? Picking up a down-going passenger during an up-sweep strands them riding the wrong way — they board, ride up, then need ANOTHER trip down. Naive SCAN implementations ship this bug; naming it out loud is an interview point by itself.

### `ElevatorCar` — the state machine owner

Owns: id, current floor, state, direction, capacity, passengers aboard, travel/stop counters, and the append-only pending list (arrival order preserved — what FCFS reads as a queue). Its tick loop is one unit of work per call: close doors OR open doors + board/alight OR move one floor OR rest.

Key decisions:

- **The car never decides stops.** `tick()` asks the scheduler and commits to whatever floor comes back. Policy is 100% in the strategy.
- **`arrive()` services every request at the floor — no direction filtering.** The scheduler already decided this floor deserves a stop, so the car commits. This is what makes FCFS honest: FCFS boards a down-going passenger at the head-of-queue floor because arrival order said so; SCAN would never have chosen that stop mid-sweep. The policy difference shows up in WHICH floors get chosen, not in how a chosen stop behaves.
- **Capacity is counted as commitments** (`canAcceptHallCall`): aboard passengers + promised hall pickups must stay under capacity. Counting only bodies aboard lets an empty cab accept five hall calls for a 2-person car — the fifth passenger watches the doors close on a full cab. Promises are commitments; count them.
- **No hall-call dedupe by floor+direction.** Two people pressing the same Up button are two passengers. Deduping undercounts commitments and strands the second passenger — the demo's Section 4 shows the correct rejection instead.
- **Duplicate cab presses ARE deduped** — the passenger is already aboard; a second press of "7" is one person pressing twice, not two people.

### `ElevatorSystem` — the dispatcher

For a hall call, which car answers? The demo policy: **nearest suitable car**, suitable = `canAcceptHallCall()`, nearest by `estimateCost`. The cost model is LOOK-aware: a car sweeping the same direction that hasn't passed the floor is cheap (marginal detour); a car that must finish its sweep first pays for the round trip; an idle car pays pure distance.

Why the dispatcher is a separate class from the scheduler: even with perfect LOOK inside each car, a building needs a global assignment policy. Keeping intra-car scheduling (which floor next) and inter-car dispatch (which car takes the call) apart is what makes the design survive the follow-up "what if 8 elevators?" — the dispatcher is a loop over cars; the strategy is per car.

Also owns the tick pump: `tickAll()` advances every car one unit. A production controller would run this on a scheduler thread; the demo calls it in a for-loop so the whole run is a deterministic golden log.

---

## Class Relationships

```
ElevatorSystem 1 ── many ElevatorCar        (dispatch + tick pump)
ElevatorCar    1 ── 1  ElevatorScheduler     (strategy, injected)
ElevatorCar    1 ── many ElevatorRequest     (pending list, arrival order)
ElevatorCar    ··· ElevatorState / Direction (state machine)
ElevatorScheduler <|── FcfsScheduler, ScanScheduler
ElevatorRequest ──> RequestSource (FLOOR_BUTTON | CAB_BUTTON)
Demo ──> ElevatorSystem (hallCall / cabCall / tickAll)
```

No class depends on a concrete scheduler. The car depends on the seam; the demo injects the policy.

---

## Pattern Choices and Rejected Alternatives

| Choice | Rejected alternative | Why |
|---|---|---|
| Strategy interface for scheduling | `if (scanMode)` branch in the car | The policy is the varying axis of this problem; a branch fails OCP the moment the interviewer says "now show FCFS" |
| Enum state machine | boolean flags (`isMoving`, `doorsOpen`) | Flags can express illegal combinations (moving with doors open); the enum cannot — illegal states are unrepresentable |
| Hall/cab request duality | one request type with nullable fields | Nullable intent for cab calls pushes validation to every consumer; the factories enforce shape at construction |
| Commitment-based capacity | count bodies aboard | Undercounts promised pickups; strands the N+1th passenger at a full cab |
| No hall-call dedupe | dedupe floor+direction | Two presses = two passengers; dedupe undercounts commitments |
| Tick loop simulation | threads + sleeps | Determinism: the whole demo is a golden log; threads make interview output unreadable and untestable |
| Car stops only at scheduler's target | car stops at "any floor with a pending request" | Silently hard-codes SCAN; the injected FCFS strategy becomes dead code |

---

## Classic Edge Cases

1. **SCAN picking up an intermediate floor that FCFS defers** — the headline comparison. Hall calls 2F(UP), 5F(DOWN), 3F(UP): FCFS walks past floor 3 en route to 5 (6 floors, Meera waits a round trip); SCAN collects floor 3 on the up-sweep (2 floors to that point). Demo Sections 1–2 print both logs.
2. **Direction reversal** — only at LOOK turnaround points: the sweep exhausts its matching stops, travels to the sweep boundary (farthest outstanding request), then flips. Never mid-sweep. Reversing mid-sweep thrashes the cab and re-strands everyone.
3. **Wrong-intent starvation** — the bug this design explicitly fixes: "floor 8, DOWN" above an up-sweeping cab is invisible to intent-filtered lookups. The sweep-boundary step (3) guarantees service. Demo Section 5.
4. **Capacity overload** — a commitment-full cab rejects a hall call at the dispatcher; the caller retries (real buildings: the button light stays on). Demo Section 4.
5. **Two presses of the same hall button** — two passengers, two commitments; no dedupe. The second cab-button press of the same floor IS deduped (same person, already aboard).
6. **Doors and movement** — doors open only on arrival at a scheduler-chosen floor; movement happens only outside DOORS_OPEN. The tick structure enforces it: a DOORS_OPEN tick can only close.
7. **Request at the car's own floor while parked** — open doors immediately; never drive away from passengers at your doors.

---

## Trade-offs Accepted

1. **Simultaneous boarding is instantaneous** — passengers board/alight the moment doors open, no dwell timer. Real controllers model door-dwell (re-open on sensor). Extension point: a `DOORS_OPEN` tick that re-checks before closing.
2. **Single-threaded tick pump** — a production system pumps ticks from a scheduler thread or per-car actors; the domain here is single-threaded per car, so the swap is mechanical. Say this out loud.
3. **Cost model is a heuristic** — `estimateCost` approximates LOOK awareness without simulating. A full simulation (try each car, take the best outcome) is more accurate and O(C × ticks) more expensive; heuristics are the interview answer.
4. **No persistence / no display / no alarms** — the state machine's guards (capacity, range, illegal transitions) are the seams where those features attach.

---

## Complexity Summary

| Operation | Time | Space |
|---|---|---|
| `nextStop` (either strategy) | O(R), R = pending requests | O(1) |
| Hall-call dispatch | O(C × R) over C cars | O(1) |
| Car tick | O(R) | O(1) |
| Request accept (capacity scan) | O(R) | O(R) list growth |

Scale-ups worth naming: per-car **sorted stop set** → `nextStop` in O(log R) (ceiling/floor lookups replace the scan); **zone dispatching** → O(1) assignment in skyscrapers (cars own floor ranges).

---

## How to Extend (Interview Talking Points)

- **Idle repositioning** (return to lobby): a post-idle hook in the tick loop or a third scheduler; the car is untouched.
- **Access control / priority floors**: priority on the request + a scheduler that weighs it — still strategy-shaped, no car changes.
- **Weight sensor**: hook in `arrive()` — refuse boarding past capacity and alarm; the capacity guard is already the seam.
- **Emergency stop / fire recall**: a new state with transitions from every state; the enum grows, the machine guards it.
- **Real threading**: replace the demo's loop with a `ScheduledExecutorService` pumping `tickAll()`; the domain is already single-threaded per car.
- **Destination dispatch** (the modern twist): passengers enter destination at the hall panel; hall calls become full trips, the scheduler gets richer data, the strategy seam absorbs it.
