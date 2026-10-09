import java.util.List;

/**
 * SCAN, implemented in its production-used LOOK variant (a.k.a. the
 * "elevator algorithm" — literally named after this problem).
 *
 * Pure SCAN: the cab sweeps to the END of the building (floor 1 or N) before
 * reversing, ALWAYS — even with no requests left in the sweep direction.
 * LOOK: the cab reverses at the LAST outstanding request in the sweep
 * direction instead of sweeping to the physical end.
 *
 * WHY LOOK beats SCAN at the extremes (the standard interview question):
 * In a 15-floor building with one pending request at floor 2 while the cab
 * sweeps up from 5, pure SCAN travels to 15 first (10 wasted floors),
 * then descends 13 to 2: 23 floors total. LOOK reverses at the topmost
 * OUTSTANDING TARGET and goes straight down: 3 floors. SCAN's fixed sweep
 * endpoints make its travel O(building height) per sweep regardless of
 * demand; LOOK's travel is bounded by the REQUEST SET, not the building.
 * In a 40-floor tower serving a 2-floor errand, SCAN wastes 40+ floors per
 * round trip; LOOK wastes zero. Real elevator controllers run LOOK.
 *
 * The algorithm, in decision order (this exact order matters):
 *
 * 1. PARKED CAR: if any request sits at the current floor (waiting
 *    passengers / a cab call), open doors HERE first — never drive away
 *    from people standing at your own doors. Otherwise pick the sweep
 *    direction by the nearest request floor of ANY intent.
 * 2. SWEEPING: next stop = nearest strictly-ahead target that is either a
 *    cab call (passenger already aboard) or a hall call whose intent
 *    MATCHES the sweep. This is the payoff the demo prints: the up-sweep
 *    collects every up-going intermediate floor FCFS would defer.
 * 3. NO MATCHING STOPS AHEAD, but requests still lie ahead (wrong-intent
 *    hall calls): travel to the FARTHEST such floor — the sweep boundary.
 *    Those down-going passengers board at the sweep's end for the return
 *    leg (this is what real LOOK elevators do at the top of a sweep).
 *    Skipping this step is the classic starvation bug: a "floor 5, going
 *    down" call above an up-sweeping car is invisible to intent-filtered
 *    lookups and the passenger waits forever.
 * 4. SWEEP EXHAUSTED: reverse — nearest matching-intent stop in the
 *    opposite half-space; if none, farthest request the other way; if the
 *    only remaining work is at the CURRENT floor (the reversal point),
 *    stop and board: the sweep has ended, the return sweep begins here.
 */
public class ScanScheduler implements ElevatorScheduler {

    @Override
    public String name() {
        return "SCAN/LOOK";
    }

    @Override
    public String describe() {
        return "sweeps one direction servicing every stop on the way; reverses at "
                + "the LAST outstanding request (LOOK), not the building end";
    }

    @Override
    public Integer nextStop(int currentFloor, Direction direction, List<ElevatorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }

        // (1) Parked car: never leave passengers standing at your own doors.
        // A sweeping car does NOT take this branch for wrong-intent calls at
        // its floor — those passengers are picked up on the return sweep;
        // stopping for them mid-sweep would carry them the wrong way.
        if (direction == null || !direction.isMoving()) {
            if (anyRequestAt(requests, currentFloor)) {
                return currentFloor;
            }
        }

        Direction sweep = (direction != null && direction.isMoving())
                ? direction : chooseSweep(requests, currentFloor);
        if (sweep == null) {
            return anyRequestAt(requests, currentFloor) ? currentFloor : null;
        }

        // (2) Nearest serviceable stop in the sweep direction.
        Integer stop = nearestServiceable(requests, currentFloor, sweep);
        if (stop != null) {
            return stop;
        }

        // (3) Sweep boundary: wrong-intent hall calls still ahead — travel to
        // the farthest one; its passengers board there for the return sweep.
        Integer boundary = farthestAny(requests, currentFloor, sweep);
        if (boundary != null) {
            return boundary;
        }

        // (4) Sweep exhausted: LOOK reversal (not the building end — that's
        // the SCAN/LOOK difference). Prefer a real stop in the opposite
        // half-space; else travel toward the farthest outstanding request;
        // else the remaining work is right here (board: return sweep starts).
        Direction opp = opposite(sweep);
        Integer stopOpposite = nearestServiceable(requests, currentFloor, opp);
        if (stopOpposite != null) {
            return stopOpposite;
        }
        Integer boundaryOpposite = farthestAny(requests, currentFloor, opp);
        if (boundaryOpposite != null) {
            return boundaryOpposite;
        }
        return anyRequestAt(requests, currentFloor) ? currentFloor : null;
    }

    /**
     * Sweep direction for a parked car: toward the nearest request floor of
     * ANY intent (a down-going passenger above still needs the car to come
     * up to them). Ties prefer UP — the conventional tie-break.
     */
    private Direction chooseSweep(List<ElevatorRequest> requests, int currentFloor) {
        Integer nearestUp = nearestAny(requests, currentFloor, Direction.UP);
        Integer nearestDown = nearestAny(requests, currentFloor, Direction.DOWN);
        if (nearestUp == null && nearestDown == null) {
            return null; // only same-floor work (handled by the caller)
        }
        if (nearestDown == null
                || (nearestUp != null && (nearestUp - currentFloor) <= (currentFloor - nearestDown))) {
            return Direction.UP;
        }
        return Direction.DOWN;
    }

    /**
     * Nearest strictly-ahead target that is serviceable mid-sweep: any cab
     * call (the passenger is already aboard — direction is irrelevant) or a
     * hall call whose intent MATCHES the sweep (a wrong-way pickup would
     * strand the passenger riding the wrong direction).
     */
    private Integer nearestServiceable(List<ElevatorRequest> requests,
                                       int currentFloor, Direction dir) {
        Integer best = null;
        for (ElevatorRequest request : requests) {
            int target = request.getFloorNumber();
            boolean ahead = dir == Direction.UP ? target > currentFloor : target < currentFloor;
            if (!ahead) {
                continue;
            }
            if (request.isHallCall() && request.getDirection() != dir) {
                continue;
            }
            if (best == null
                    || (dir == Direction.UP && target < best)
                    || (dir == Direction.DOWN && target > best)) {
                best = target;
            }
        }
        return best;
    }

    /** Nearest request floor of ANY intent, strictly ahead along `dir`. */
    private Integer nearestAny(List<ElevatorRequest> requests,
                               int currentFloor, Direction dir) {
        Integer best = null;
        for (ElevatorRequest request : requests) {
            int target = request.getFloorNumber();
            boolean ahead = dir == Direction.UP ? target > currentFloor : target < currentFloor;
            if (ahead && (best == null
                    || (dir == Direction.UP && target < best)
                    || (dir == Direction.DOWN && target > best))) {
                best = target;
            }
        }
        return best;
    }

    /** Farthest request floor of ANY intent, strictly ahead along `dir`. */
    private Integer farthestAny(List<ElevatorRequest> requests,
                                int currentFloor, Direction dir) {
        Integer best = null;
        for (ElevatorRequest request : requests) {
            int target = request.getFloorNumber();
            boolean ahead = dir == Direction.UP ? target > currentFloor : target < currentFloor;
            if (ahead && (best == null
                    || (dir == Direction.UP && target > best)
                    || (dir == Direction.DOWN && target < best))) {
                best = target;
            }
        }
        return best;
    }

    private boolean anyRequestAt(List<ElevatorRequest> requests, int floor) {
        for (ElevatorRequest request : requests) {
            if (request.getFloorNumber() == floor) {
                return true;
            }
        }
        return false;
    }

    private Direction opposite(Direction dir) {
        return dir == Direction.UP ? Direction.DOWN : Direction.UP;
    }
}
