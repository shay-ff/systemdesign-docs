import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The building dispatcher: coordinates multiple cars.
 *
 * For a hall call ("floor 4, going up"), WHICH car answers? Classic policy:
 * the NEAREST SUITABLE car — nearest by estimated cost, suitable = can still
 * take a passenger (not at capacity) and (for LOOK-aware realism) heading the
 * right way or idle.
 *
 * This is a SEPARATE concern from intra-car scheduling (FCFS vs SCAN): even
 * with perfect LOOK inside each car, a building needs a global assignment
 * policy. Keeping the two policies apart is what makes this design survive
 * the follow-up "what if 8 elevators?" — the dispatcher is a loop over cars,
 * the scheduler is a strategy per car.
 *
 * Also owns the tick pump: every tick, every car advances one unit. A real
 * controller would run this on a scheduler thread; the demo calls tick() in
 * a for-loop so the whole run is a deterministic golden log.
 */
public class ElevatorSystem {

    private final int maxFloors;
    private final List<ElevatorCar> cars = new ArrayList<>();

    public ElevatorSystem(int maxFloors) {
        if (maxFloors < 1) {
            throw new IllegalArgumentException(
                    "A building needs at least 1 floor, got " + maxFloors);
        }
        this.maxFloors = maxFloors;
    }

    public void addCar(ElevatorCar car) {
        if (car == null) {
            throw new IllegalArgumentException("Cannot add a null elevator car");
        }
        for (ElevatorCar existing : cars) {
            if (existing.getId().equals(car.getId())) {
                throw new IllegalArgumentException(
                        "Elevator id already exists in this building: " + car.getId());
            }
        }
        cars.add(car);
    }

    /**
     * A passenger pressed an Up/Down button on `floor`. Dispatch to the
     * cheapest suitable car.
     *
     * @throws IllegalStateException if every car is full (capacity overload
     *                               is a real production alert — the button
     *                               lights stay on and the caller retries)
     */
    public void hallCall(int floor, Direction dir) {
        if (floor < 1 || floor > maxFloors) {
            throw new IllegalArgumentException("Floor " + floor
                    + " is outside this building (1.." + maxFloors + ")");
        }
        if (dir == null || !dir.isMoving()) {
            throw new IllegalArgumentException(
                    "Hall call direction must be UP or DOWN, got " + dir);
        }
        if (cars.isEmpty()) {
            throw new IllegalStateException(
                    "No elevators in the building — add cars before dispatching calls");
        }

        ElevatorCar best = null;
        int bestCost = Integer.MAX_VALUE;
        for (ElevatorCar car : cars) {
            if (!car.canAcceptHallCall()) {
                continue; // at capacity (aboard + promised pickups): unsuitable
            }
            int cost = car.estimateCost(floor, dir);
            if (cost < bestCost) {
                bestCost = cost;
                best = car;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Hall call at floor " + floor + " (" + dir
                    + ") rejected: every car is at capacity (aboard + promised pickups)");
        }
        ElevatorRequest request = ElevatorRequest.floorButton(floor, dir);
        best.accept(request);
        System.out.printf("  Hall call: floor %d (%s) -> dispatched to %s (est. cost %d)%n",
                floor, dir, best.getId(), bestCost);
    }

    /**
     * A passenger inside car `carId` pressed a cab button for `floor`.
     * Cab calls bypass the dispatcher — the car is already committed.
     */
    public void cabCall(String carId, int floor) {
        if (carId == null || carId.trim().isEmpty()) {
            throw new IllegalArgumentException("Car id cannot be null or empty");
        }
        if (floor < 1 || floor > maxFloors) {
            throw new IllegalArgumentException("Floor " + floor
                    + " is outside this building (1.." + maxFloors + ")");
        }
        findCar(carId).accept(ElevatorRequest.cabButton(floor));
    }

    /**
     * Advance every car one tick. Returns true if ANY car still has pending
     * work (the demo loops until quiet), false when the building is at rest.
     */
    public boolean tickAll() {
        for (ElevatorCar car : cars) {
            car.tick();
        }
        for (ElevatorCar car : cars) {
            if (car.hasPendingRequests() || car.getState().isMoving()) {
                return true;
            }
        }
        return false;
    }

    /** Run ticks until every car is idle (capped so a bug cannot loop forever). */
    public void runUntilIdle(int maxTicks) {
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be >= 1, got " + maxTicks);
        }
        int ticks = 0;
        while (tickAll() && ticks < maxTicks) {
            ticks++;
        }
    }

    private ElevatorCar findCar(String carId) {
        Objects.requireNonNull(carId, "Car id cannot be null");
        for (ElevatorCar car : cars) {
            if (car.getId().equals(carId.trim())) {
                return car;
            }
        }
        throw new IllegalArgumentException("No elevator with id " + carId + " in this building");
    }

    public List<ElevatorCar> getCars() {
        return new ArrayList<>(cars);
    }

    public int getMaxFloors() {
        return maxFloors;
    }
}
