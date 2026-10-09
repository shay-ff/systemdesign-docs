import java.util.List;

/**
 * FCFS (First-Come-First-Served): service requests strictly in arrival order,
 * like a queue at a bank counter. The car maintains its pending list in
 * ARRIVAL order, so "first in" is simply the head of the list.
 *
 * Behaviour interviewers expect you to articulate:
 * - Simple, starvation-free by arrival order, but TERRIBLE throughput in a
 *   multi-floor building: with hall calls 2F(UP), 5F(DOWN), 3F(UP) arriving
 *   in that order, the cab shuttles 2 -> 5 -> 3, walking straight past
 *   floor 3 on the way up (6 floors travelled) where SCAN collects all
 *   three in one up-sweep (2 floors). The demo prints both logs side by side.
 * - Direction intent of hall calls is IGNORED: whoever pressed FIRST is
 *   served first, even if the cab sweeps past a same-intent intermediate
 *   floor on the way. That indifference is exactly the flaw SCAN fixes.
 *
 * This class is intentionally naive — it exists to be the baseline the demo
 * compares SCAN against. In an interview, implement the dumb version first,
 * name its flaw out loud, then upgrade.
 */
public class FcfsScheduler implements ElevatorScheduler {

    @Override
    public String name() {
        return "FCFS";
    }

    @Override
    public String describe() {
        return "services requests strictly in arrival order (queue discipline); "
                + "passes intermediate floors without stopping";
    }

    @Override
    public Integer nextStop(int currentFloor, Direction direction, List<ElevatorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        // First-in-first-out: the head of the arrival-ordered list. Always a
        // legal target — the car will open doors there and board/alight
        // whoever is at that floor (FCFS does not do intent filtering; that
        // naivety is the teaching point).
        return requests.get(0).getFloorNumber();
    }
}
