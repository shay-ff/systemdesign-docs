# Elevator System — Low Level Design

A multi-car elevator system: an enum-based car state machine, pluggable scheduling strategies (FCFS vs SCAN/LOOK — the classic comparison), hall-call vs cab-call request modelling, and a nearest-suitable-car dispatcher for multi-elevator buildings.

## Problem Statement

Design an elevator system for a building where:

- A building has **N floors** and one or more **elevator cars**, each with a capacity.
- Passengers press **floor buttons** (Up/Down hall calls) from a floor — we know the pickup floor and travel direction, but **not the destination**.
- Boarded passengers press **cab buttons** — now we know the destination.
- Each car runs a **state machine**: idle, moving up, moving down, doors open.
- Each car schedules its pending stops via a **pluggable algorithm** — FCFS and SCAN/LOOK both ship here, swappable without touching the car.
- A **dispatcher** assigns each new hall call to the nearest suitable car.
- Movement is simulated with a **tick loop**: one tick = one floor of travel (plus door open/close events).

## Key Features

- **Request duality**: `FLOOR_BUTTON` (hall call: floor + direction intent) vs `CAB_BUTTON` (destination) — the real-world subtlety interviewers probe.
- **Pluggable scheduling (Strategy)**: `FcfsScheduler` and `ScanScheduler` (LOOK variant) behind one interface; the demo runs the identical scenario under both and prints the behavioural difference.
- **Car state machine**: `IDLE / MOVING_UP / MOVING_DOWN / DOORS_OPEN` with guarded transitions; direction reversal only at sweep turnarounds.
- **Multi-car dispatch**: `ElevatorSystem` ranks cars by estimated LOOK-aware cost and picks the nearest suitable one.
- **Capacity as commitments**: a car counts promised hall pickups plus aboard passengers, so a full cab never accepts a call it cannot load.
- **Deterministic simulation**: a tick loop instead of threads/sleeps — the whole demo is a golden log.

## Clarifying Questions an Interviewer Expects You to Ask

1. **Do you know the passenger's destination when the floor button is pressed?**
   No — only at cab-button press time. This forces the two-request model (hall call + cab call) and is the #1 design fork in this problem.
2. **Single elevator or bank of elevators?**
   Ask. Single car = scheduling algorithm focus. Multiple cars = add a dispatcher (assignment policy is a separate concern from per-car scheduling).
3. **Which scheduling algorithm?**
   FCFS is the baseline; SCAN/LOOK is the production answer. Implement the strategy seam even if you only write one algorithm.
4. **Can the cab stop for an opposite-direction hall call mid-sweep?**
   No — picking up a down-going passenger during an up-sweep strands them riding the wrong way. Wrong-intent calls must still be served via the sweep boundary (reversal at the farthest outstanding request), or they starve.
5. **What happens on capacity overload?**
   A full cab must not accept a hall call; the dispatcher routes to another car (or retries). Count commitments (promised pickups), not just bodies aboard.
6. **Door timing?**
   Doors open on arrival, close before moving — movement while doors are open is a safety invariant. Model `DOORS_OPEN` as a state, not a flag.
7. **How is time modelled?**
   A tick loop (one tick = one floor). Threads and sleeps are anti-features in an interview: a deterministic loop is testable as a golden log.
8. **Weight sensor / alarms / emergency stop?**
   Out of scope, but name the extension points (capacity guard is where a weight sensor hooks in).

## Core Entities

| Entity | Responsibility |
|---|---|
| `ElevatorCar` | The state machine: floor, direction, state, capacity, pending requests; moves one floor per tick, opens/closes doors. Contains NO scheduling policy |
| `ElevatorState` | Enum: IDLE, MOVING_UP, MOVING_DOWN, DOORS_OPEN |
| `Direction` | Enum: UP, DOWN (intent and travel direction) |
| `ElevatorRequest` | Immutable request; factories for hall calls (floor + direction) and cab calls (destination) |
| `RequestSource` | Enum: FLOOR_BUTTON vs CAB_BUTTON |
| `ElevatorScheduler` | Strategy interface: which floor does this car serve next? |
| `FcfsScheduler` | Arrival-order queue discipline — the naive baseline |
| `ScanScheduler` | SCAN in its LOOK variant: sweep, service matching stops, reverse at the farthest outstanding request |
| `ElevatorSystem` | Building dispatcher: hall-call -> nearest suitable car; cab-call routing; the tick pump |
| `Floor` | Immutable floor value object with range validation |
| `ElevatorDemo` | Six-section narrative demo |

## Design Patterns Used (and why)

- **Strategy — scheduling algorithm.** FCFS vs SCAN/LOOK are interchangeable algorithms over the same car state. Injected at car construction; swapping the policy is a constructor argument. This is the cleanest OCP demonstration in the problem — the car never edits to change its brain.
- **State (enum-based) — car lifecycle.** The legal transitions differ by state (doors must close before moving; direction reverses only at turnaround). Enum states + guarded transitions keep the machine inspectable and make illegal states unrepresentable at the type level (`DOORS_OPEN` cannot move).
- **Factory method — `ElevatorRequest.floorButton(...)` / `.cabButton(...)`.** Two request shapes with different required fields (hall calls need direction intent; cab calls forbid it). Named factories enforce the invariants at construction instead of a leaky constructor.
- **Facade — `ElevatorSystem`.** Callers press buttons; the dispatcher's cost ranking, capacity checks and assignment logic hide behind `hallCall` / `cabCall`.
- **Value object — `Floor`.** Validation (range) lives in one place; every consumer is guaranteed a legal floor.

Rejected alternatives and trade-offs live in [explanation.md](explanation.md).

## How to Run

```bash
cd solutions/java

# Option 1: compile then run (Java 11)
javac *.java
java ElevatorDemo

# Option 2 (if your launcher supports sibling classes): single-file launch
java ElevatorDemo.java
```

Total runtime under 2 seconds (25ms tick pacing; no long sleeps).

## Time Complexity

| Operation | Cost |
|---|---|
| `nextStop` (FCFS or SCAN) | O(R), R = pending requests on the car |
| Hall-call dispatch (nearest suitable car) | O(C x R), C = cars in the building |
| One car tick | O(R) (scheduler call + service) |
| Accept a request | O(R) duplicate/capacity scan |

Interview scale-up: per-car sorted stop sets make `nextStop` O(log R); a zone-based dispatcher (cars own floor ranges) makes dispatch O(1) amortized.

## Interview Extension Questions

1. **Add a "go to default floor when idle" policy** — a third `ElevatorScheduler` or a post-idle hook in the tick loop; the car is untouched (OCP).
2. **Priority for penthouse floors / access control** — a request priority field + a scheduler that weighs it; again strategy-shaped.
3. **Weight sensor overload** — hook into `ElevatorCar.arrive()`: refuse to board and sound the alarm (the capacity guard is already the seam).
4. **Emergency stop / fire recall** — a new state with transitions from every state (the enum grows; the machine guards it).
5. **Real-time threads** — replace the demo's tick loop with a `ScheduledExecutorService` pumping the same `tick()`; the domain code is already single-threaded per car, so the swap is mechanical.
6. **Zone dispatching for skyscrapers** — an `ElevatorSystem` variant: cars own floor ranges; dispatch is a range lookup instead of a cost scan.
7. **What if two passengers press the same hall button?** — Two real passengers: count both commitments (see explanation.md for why deduping here is a bug).
