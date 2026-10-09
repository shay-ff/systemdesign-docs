import java.util.List;

/**
 * Strategy interface: given a car and its pending requests, which floor (if
 * any) should it service on the next tick?
 *
 * WHY a strategy (and not a method on ElevatorCar):
 * - FCFS and SCAN/LOOK are interchangeable ALGORITHMS over the same data.
 *  The car should hold the data (position, direction, pending requests) and
 *  the scheduler should decide the ORDER. That split is the single cleanest
 *  OCP demonstration in this problem: swapping dispatch policies is a
 *  constructor argument, never a car edit.
 * - The building dispatcher (ElevatorSystem) also depends on the scheduler
 *  when ranking cars, so the policy lives in exactly one place.
 *
 * Contract:
 * - Returns the next target floor, or null when nothing is serviceable now
 *  (e.g. SCAN in mid-sweep with no stops left in the sweep direction — the
 *  car should finish the sweep before reversing; returning null lets the
 *  tick loop flip direction naturally).
 * - Must be deterministic and side-effect free on the request set; the car
 *  calls it once per tick.
 */
public interface ElevatorScheduler {

    /** Human-readable policy name for demo output. */
    String name();

    /** Short one-liner for demo output: how the policy orders stops. */
    String describe();

    /**
     * Next floor this car should stop at, or null if none is serviceable
     * this tick (car should keep sweeping, or idle if no sweep is active).
     *
     * @param currentFloor the car's floor right now
     * @param direction    the car's current direction (may be null if idle)
     * @param requests      pending requests assigned to this car (never null)
     * @return target floor number, or null
     */
    Integer nextStop(int currentFloor, Direction direction, List<ElevatorRequest> requests);
}
