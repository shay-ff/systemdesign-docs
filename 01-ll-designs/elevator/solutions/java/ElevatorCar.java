import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * One elevator car: a state machine plus its pending request set.
 *
 * DIVISION OF LABOUR (the pattern point of this whole problem):
 * - ElevatorCar OWNS state and data: floor, direction, state, capacity,
 *   passengers aboard, pending requests. It MOVES and OPENS DOORS.
 * - The injected ElevatorScheduler DECIDES which floor to serve next — the
 *   car stops ONLY at the floor the scheduler names. No stop logic leaks
 *   into the car (the classic bug: a car that stops at "any floor with a
 *   pending request" has silently hard-coded SCAN and will ignore its
 *   FCFS strategy).
 * - The ElevatorSystem (dispatcher) DECIDES WHICH CAR takes a new hall call.
 *
 * The car contains zero scheduling policy and zero dispatch policy — which
 * is why the demo can swap FCFS for SCAN by constructing the car with a
 * different scheduler and nothing else changes.
 *
 * Tick model: one call to tick() = ONE unit of work — advance one floor, OR
 * open doors, OR close doors, OR rest. No threads, no sleeps — the demo drives
 * a deterministic tick loop, which also makes the whole system unit-testable
 * as a golden log.
 */
public class ElevatorCar {

    private final String id;
    private final int capacity;
    private final int maxFloors;
    private final ElevatorScheduler scheduler;

    private int currentFloor;
    private ElevatorState state = ElevatorState.IDLE;
    private Direction direction;          // null while resting (sweep memory otherwise)
    private int passengersAboard;
    private int totalFloorsTravelled;
    private int totalStops;

    /** Pending requests, in ARRIVAL order (request ids preserve it). */
    private final List<ElevatorRequest> pendingRequests = new ArrayList<>();

    public ElevatorCar(String id, int startingFloor, int maxFloors, int capacity,
                       ElevatorScheduler scheduler) {
        this.id = validateId(id);
        if (maxFloors < 1) {
            throw new IllegalArgumentException(
                    "A building needs at least 1 floor, got " + maxFloors);
        }
        if (startingFloor < 1 || startingFloor > maxFloors) {
            throw new IllegalArgumentException("Starting floor " + startingFloor
                    + " is outside 1.." + maxFloors);
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1, got " + capacity);
        }
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "Elevator scheduler cannot be null (inject FcfsScheduler or ScanScheduler)");
        }
        this.scheduler = scheduler;
        this.maxFloors = maxFloors;
        this.capacity = capacity;
        this.currentFloor = startingFloor;
    }

    private static String validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Elevator id cannot be null or empty");
        }
        return id.trim();
    }

    // ------------------------------------------------------------------
    // Request intake
    // ------------------------------------------------------------------

    /**
     * True when this car can still promise a ride to a waiting passenger:
     * aboard + already-promised hall pickups must stay under capacity.
     *
     * WHY count promises and not just bodies aboard: a car that accepted a
     * hall call at floor 2 is COMMITTED to that passenger. Capacity checks
     * made only against bodies aboard let an empty car accept five hall
     * calls for a 2-person cab — the fifth passenger watches doors close on
     * a full cab. Counting commitments is the production-correct check.
     */
    public boolean canAcceptHallCall() {
        int promisedPickups = 0;
        for (ElevatorRequest request : pendingRequests) {
            if (request.isHallCall()) {
                promisedPickups++;
            }
        }
        return passengersAboard + promisedPickups < capacity;
    }

    /**
     * Assign a request to this car. Hall calls are rejected at capacity
     * (see canAcceptHallCall); cab calls are always accepted — the passenger
     * is already aboard and pressing the button is the only legal way to
     * leave. Duplicate presses are silently ignored (two people pressing
     * the same hall button must not create two pickups).
     */
    public void accept(ElevatorRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getFloorNumber() < 1 || request.getFloorNumber() > maxFloors) {
            throw new IllegalArgumentException("Request floor " + request.getFloorNumber()
                    + " is outside 1.." + maxFloors + " for elevator " + id);
        }
        if (request.isHallCall()) {
            // Deliberately NO floor+direction dedupe: every button press is a
            // REAL waiting passenger (two people pressing the same Up button
            // are two passengers, not one). Deduping here is the classic bug
            // that under-counts commitments and strands the second passenger;
            // the commitment check below is what protects capacity.
            if (!canAcceptHallCall()) {
                throw new IllegalStateException("Elevator " + id + " cannot accept hall call "
                        + request + ": at capacity (" + passengersAboard + " aboard + "
                        + countPendingHallCalls() + " promised pickups vs capacity " + capacity
                        + ") — dispatcher must pick another car");
            }
        } else {
            for (ElevatorRequest existing : pendingRequests) {
                if (!existing.isHallCall()
                        && existing.getFloorNumber() == request.getFloorNumber()) {
                    return; // duplicate cab button press
                }
            }
        }
        pendingRequests.add(request);
    }

    private int countPendingHallCalls() {
        int count = 0;
        for (ElevatorRequest request : pendingRequests) {
            if (request.isHallCall()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // The tick engine — one unit of work per tick
    // ------------------------------------------------------------------

    /**
     * Advance one unit of time. State machine, in words:
     *   DOORS_OPEN  -> close doors (boarding/alighting already happened at
     *                  open time; closing re-plans or rests)
     *   otherwise   -> ask the SCHEDULER for the next stop:
     *       no target          -> rest (IDLE) — or stay parked if mid-sweep
     *       target == here     -> open doors, board/alight
     *       target elsewhere   -> set direction/state, move ONE floor
     *                              (arriving exactly at target -> doors open)
     */
    public void tick() {
        if (state == ElevatorState.DOORS_OPEN) {
            closeDoors();
            return;
        }
        if (pendingRequests.isEmpty()) {
            settleIdle();
            return;
        }
        Integer target = scheduler.nextStop(currentFloor, direction, pendingRequests);
        if (target == null) {
            // Nothing serviceable this tick (a scheduler may choose to keep
            // sweeping past floors with wrong-way hall calls). Stay parked;
            // the next tick re-plans. This is also the stall-free resting
            // posture for a car whose only work is unreachable — impossible
            // by validation, but defensive.
            settleIdle();
            return;
        }
        if (target.intValue() == currentFloor) {
            arrive();
            return;
        }
        direction = target > currentFloor ? Direction.UP : Direction.DOWN;
        state = direction == Direction.UP ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
        moveOneFloor();
        if (currentFloor == target.intValue()) {
            arrive();
        } else {
            System.out.printf("    %s -> floor %d [%s]%n", id, currentFloor, state);
        }
    }

    /** Move exactly one floor along `direction` (already set by tick()). */
    private void moveOneFloor() {
        currentFloor = direction == Direction.UP ? currentFloor + 1 : currentFloor - 1;
        if (currentFloor < 1 || currentFloor > maxFloors) {
            throw new IllegalStateException("Elevator " + id + " left the building at floor "
                    + currentFloor + " — scheduler returned an out-of-range target");
        }
        totalFloorsTravelled++;
    }

    /**
     * Open doors at the current floor and service EVERY request here:
     * hall calls = passengers board (one aboard each), cab calls =
     * passengers alight. No direction filtering — the SCHEDULER already
     * decided this floor deserves a stop, so the car commits to it. (FCFS
     * boards a down-going passenger on an up-arriving cab because arrival
     * ORDER said so; SCAN would never have chosen this stop mid-sweep —
     * policy lives in the scheduler, not here.)
     */
    private void arrive() {
        state = ElevatorState.DOORS_OPEN;
        totalStops++;
        System.out.printf("    %s -> floor %d [ARRIVED — doors opening]%n", id, currentFloor);
        Iterator<ElevatorRequest> it = pendingRequests.iterator();
        while (it.hasNext()) {
            ElevatorRequest request = it.next();
            if (request.getFloorNumber() != currentFloor) {
                continue;
            }
            it.remove();
            if (request.isHallCall()) {
                // Belt-and-braces: commitment tracking should have kept the
                // promise count under capacity; clamp anyway so a bug can
                // never exceed the physical load.
                passengersAboard = Math.min(capacity, passengersAboard + 1);
                System.out.printf("      %s: passenger BOARDS at floor %d (wanted %s)%n",
                        id, currentFloor, request.getDirection());
            } else {
                passengersAboard = Math.max(0, passengersAboard - 1);
                System.out.printf("      %s: passenger ALIGHTS at floor %d%n", id, currentFloor);
            }
        }
    }

    /**
     * Close doors. If no work remains -> IDLE (direction memory cleared).
     * If work remains -> the sweep direction is KEPT: SCAN's next decision
     * must know which way we were sweeping (reversal only at sweep end).
     * The next tick re-plans the move; closing doors is this tick's work.
     */
    private void closeDoors() {
        System.out.printf("    %s at floor %d [doors closed]%n", id, currentFloor);
        if (pendingRequests.isEmpty()) {
            settleIdle();
        } else {
            // Silent transition: neither moving nor resting, doors shut,
            // planning next stop on the next tick.
            state = ElevatorState.IDLE;
        }
    }

    private void settleIdle() {
        if (state != ElevatorState.IDLE) {
            System.out.printf("    %s at floor %d [IDLE — no pending requests]%n",
                    id, currentFloor);
        }
        state = ElevatorState.IDLE;
        direction = null;
    }

    // ------------------------------------------------------------------
    // Dispatcher-facing queries (used by ElevatorSystem to rank cars)
    // ------------------------------------------------------------------

    /**
     * Estimated cost for this car to answer a hall call at `floor` with `dir`
     * intent. Lower = better candidate. LOOK-aware: a car sweeping the same
     * direction that has not yet passed the floor is cheap (marginal detour);
     * a car that must finish its sweep first pays for the whole round trip.
     */
    public int estimateCost(int floor, Direction dir) {
        if (floor < 1 || floor > maxFloors) {
            throw new IllegalArgumentException("Floor " + floor
                    + " is outside 1.." + maxFloors);
        }
        if (isIdle()) {
            return Math.abs(floor - currentFloor);
        }
        if (direction != null && direction == dir) {
            boolean ahead = dir == Direction.UP
                    ? floor >= currentFloor : floor <= currentFloor;
            if (ahead) {
                return Math.abs(floor - currentFloor); // on the way: marginal cost
            }
            return Math.abs(floor - currentFloor) + 2 * sweepExtent(dir);
        }
        // Opposite sweep or busy-neutral: worst case is finishing the current
        // sweep, crossing to the floor, then reaching the call.
        return Math.abs(floor - currentFloor) + sweepExtent(direction) + 2;
    }

    private int sweepExtent(Direction dir) {
        if (dir == null) {
            return 0;
        }
        int farthest = currentFloor;
        for (ElevatorRequest request : pendingRequests) {
            if (dir == Direction.UP && request.getFloorNumber() > farthest) {
                farthest = request.getFloorNumber();
            }
            if (dir == Direction.DOWN && request.getFloorNumber() < farthest) {
                farthest = request.getFloorNumber();
            }
        }
        return Math.abs(farthest - currentFloor);
    }

    public boolean isIdle() {
        return state == ElevatorState.IDLE && pendingRequests.isEmpty();
    }

    public boolean hasPendingRequests() {
        return !pendingRequests.isEmpty();
    }

    // ------------------------------------------------------------------
    // Getters / toString
    // ------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public ElevatorState getState() {
        return state;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getPassengersAboard() {
        return passengersAboard;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTotalFloorsTravelled() {
        return totalFloorsTravelled;
    }

    public int getTotalStops() {
        return totalStops;
    }

    public List<ElevatorRequest> getPendingRequests() {
        return new ArrayList<>(pendingRequests);
    }

    @Override
    public String toString() {
        return String.format("%s[floor %d, %s%s, %d/%d aboard, %d pending]",
                id, currentFloor, state,
                direction != null && state.isMoving() ? " " + direction : "",
                passengersAboard, capacity, pendingRequests.size());
    }
}
