# Elevator System — Java Solution

Runnable, dependency-free Java (no external libraries; `javac` + `java` only).

## Files

| File | Role |
|---|---|
| `Direction.java` | UP/DOWN enum; intent + travel direction |
| `Floor.java` | Immutable floor value object with range validation |
| `RequestSource.java` | FLOOR_BUTTON (hall call) vs CAB_BUTTON (car call) |
| `ElevatorRequest.java` | Immutable request; named factories for hall/cab calls |
| `ElevatorState.java` | Car state machine: IDLE / MOVING_UP / MOVING_DOWN / DOORS_OPEN |
| `ElevatorScheduler.java` | Strategy seam: which floor does this car serve next? |
| `FcfsScheduler.java` | Arrival-order baseline (the naive queue discipline) |
| `ScanScheduler.java` | SCAN in its LOOK variant: sweep, service matching stops, reverse at the farthest outstanding request |
| `ElevatorCar.java` | State machine + data owner; moves one floor per tick; stops only at the scheduler's chosen floor |
| `ElevatorSystem.java` | Multi-car dispatcher + tick pump |
| `ElevatorDemo.java` | Six-section narrative demo (below) |

## Run

```bash
javac *.java
java ElevatorDemo
```

Runtime: under 2 seconds (25ms tick pacing).

## Demo Narrative

| Section | Shows |
|---|---|
| 1 — FCFS baseline | Arrival-order discipline: the cab sweeps PAST floor 3 (which wanted UP) en route to floor 5 — 6 floors travelled; the deferred passenger waits a round trip |
| 2 — SCAN/LOOK payoff | Identical scenario, only the strategy swapped: the up-sweep collects floor 3 on the way — fewer floors, no dead legs |
| 3 — Multi-car dispatch | Nearest suitable car by LOOK-aware cost (hall call at 5 -> car at 4; hall call at 9 -> car at 8) |
| 4 — Capacity as commitments | A tiny cab with 2 promised pickups refuses a third hall call — counting only bodies aboard would wrongly accept it |
| 5 — Direction reversal & sweep boundary | Wrong-intent hall calls (DOWN, above an up-sweeping cab) are served at the sweep boundary — the starvation bug naive intent-only SCAN ships |
| 6 — Validation guardrails | Out-of-range floors, zero capacity, null scheduler, duplicate ids, null direction — all rejected with messages |
