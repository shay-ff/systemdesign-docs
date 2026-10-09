/**
 * Elevator System demo.
 *
 * Narrative:
 *   1. One car, FCFS: observe the naive queue discipline and its wasted travel.
 *   2. Same scenario, SCAN/LOOK: the intermediate-floor pickup FCFS deferred.
 *   3. Multi-car building: nearest-suitable-car dispatch.
 *   4. Capacity overload: hall call rejected when every car is full.
 *   5. Direction reversal and state-machine guardrails.
 *   6. Validation guardrails.
 */
public class ElevatorDemo {

    // One tick = one floor of travel. Keep pacing snappy: the demo is a log,
    // not a simulation — a short sleep only makes the narrative readable.
    private static final long TICK_PAUSE_MS = 25L;
    private static final int RUN_TIMEOUT_TICKS = 120;

    public static void main(String[] args) {
        System.out.println("=== Elevator System — Low Level Design Demo ===");
        System.out.println("Model: one tick = one floor of travel. Building: 10 floors.\n");
        try {
            section1FcfsBaseline();
            section2ScanIntermediatePickup();
            section3MultiCarDispatch();
            section4CapacityOverload();
            section5DirectionReversal();
            section6StateGuards();
        } finally {
            System.out.println("\n=== Demo Complete ===");
        }
    }

    // ------------------------------------------------------------------
    private static void section1FcfsBaseline() {
        System.out.println("=== Section 1: Single car with FCFS (the naive baseline) ===");
        System.out.println("Scenario: cab starts at floor 1.");
        System.out.println("  t1: hall call floor 2 (UP)   — Priya on 2 wants to go up");
        System.out.println("  t2: hall call floor 5 (DOWN) — Kabir on 5 wants to go down");
        System.out.println("  t3: hall call floor 3 (UP)   — Meera on 3 wants to go up");
        System.out.println("FCFS services in ARRIVAL order: 2 -> 5 -> 3, even though the cab");
        System.out.println("walks straight past floor 3 on its way up to 5.\n");

        ElevatorSystem building = new ElevatorSystem(10);
        ElevatorCar cab = new ElevatorCar("E1", 1, 10, 8, new FcfsScheduler());
        building.addCar(cab);

        building.hallCall(2, Direction.UP);
        building.hallCall(5, Direction.DOWN);
        building.hallCall(3, Direction.UP);

        System.out.println("Tick-by-tick:");
        runToRest(building);

        System.out.println("\nResult: cab travelled " + cab.getTotalFloorsTravelled()
                + " floors, made " + cab.getTotalStops() + " stop(s), now at floor "
                + cab.getCurrentFloor() + " — " + cab);
        System.out.println("THE FCFS FLAW, visible in the log above: the cab swept PAST floor 3");
        System.out.println("(going up, floor 3 wanted UP) without stopping — Meera waits until");
        System.out.println("the cab has been all the way up to 5 and come back down. FCFS ignores");
        System.out.println("the intermediate floor entirely; only arrival order matters.");
        System.out.println();
        pause();
    }

    // ------------------------------------------------------------------
    private static void section2ScanIntermediatePickup() {
        System.out.println("=== Section 2: Same scenario, SCAN/LOOK — the payoff ===");
        System.out.println("Identical requests, identical start. Only the strategy changed:");
        System.out.println("SCAN sweeps UP servicing every outstanding stop on the way, so");
        System.out.println("the floor-3 UP hall call is picked up DURING the up-sweep — the");
        System.out.println("call FCFS deferred (its 'floor 3' turn never came until after 5).\n");

        ElevatorSystem building = new ElevatorSystem(10);
        ElevatorCar cab = new ElevatorCar("E1", 1, 10, 8, new ScanScheduler());
        building.addCar(cab);

        building.hallCall(2, Direction.UP);
        building.hallCall(5, Direction.DOWN);
        building.hallCall(3, Direction.UP);

        System.out.println("Tick-by-tick:");
        runToRest(building);

        System.out.println("\nResult: cab travelled " + cab.getTotalFloorsTravelled()
                + " floors, made " + cab.getTotalStops() + " stop(s), now at floor "
                + cab.getCurrentFloor() + " — " + cab);
        System.out.println("THE SCAN PAYOFF, same scenario as Section 1: the up-sweep collected");
        System.out.println("floor 3 ON THE WAY (FCFS made Meera wait for a full round trip).");
        System.out.println("Fewer floors travelled, no dead legs, same requests served.");
        System.out.println();
        pause();
    }

    // ------------------------------------------------------------------
    private static void section3MultiCarDispatch() {
        System.out.println("=== Section 3: Multi-car building — nearest suitable car ===");
        System.out.println("Cars: A (idle at 4, LOOK), B (idle at 8, LOOK), C (idle at 1, LOOK).");
        System.out.println("A hall call at floor 5 going UP should go to A (cost 1), not B (3),");
        System.out.println("not C (4). Then a call at floor 9 (UP): B wins — cost 1 from 8.\n");

        ElevatorSystem building = new ElevatorSystem(10);
        ElevatorCar carA = new ElevatorCar("A", 4, 10, 8, new ScanScheduler());
        ElevatorCar carB = new ElevatorCar("B", 8, 10, 8, new ScanScheduler());
        ElevatorCar carC = new ElevatorCar("C", 1, 10, 8, new ScanScheduler());
        building.addCar(carA);
        building.addCar(carB);
        building.addCar(carC);

        building.hallCall(5, Direction.UP);
        building.hallCall(9, Direction.UP);
        System.out.println();

        System.out.println("Tick-by-tick (all cars tick in lockstep):");
        runToRest(building);

        System.out.println("\nFinal positions:");
        for (ElevatorCar car : building.getCars()) {
            System.out.println("  " + car + " — travelled " + car.getTotalFloorsTravelled()
                    + ", stops " + car.getTotalStops());
        }
        System.out.println();
        pause();
    }

    // ------------------------------------------------------------------
    private static void section4CapacityOverload() {
        System.out.println("=== Section 4: Capacity overload — commitment accounting ===");
        System.out.println("A tiny cab (capacity 2) at floor 1. Two hall calls arrive at floor 1");
        System.out.println("(two people waiting) — both accepted; 0 aboard + 2 promised = OK.");
        System.out.println("A THIRD hall call at floor 1 must be refused: 0 aboard + 2 promised");
        System.out.println("already equals capacity — counting only bodies aboard would wrongly");
        System.out.println("accept a passenger the cab can never load.\n");

        ElevatorSystem building = new ElevatorSystem(10);
        ElevatorCar tiny = new ElevatorCar("TINY", 1, 10, 2, new ScanScheduler());
        building.addCar(tiny);

        building.hallCall(1, Direction.UP);   // passenger 1
        building.cabCall("TINY", 7);           // passenger 1's destination
        building.hallCall(1, Direction.UP);   // passenger 2 — a REAL second passenger
        System.out.println("  " + tiny + " — 2 promised pickups + 1 cab call, capacity 2: FULL BY COMMITMENT\n");

        System.out.println("Third hall call at floor 1 (UP) while TINY is commitment-full:");
        try {
            building.hallCall(1, Direction.UP);
            System.out.println("  [BUG] accepted a hall call into a commitment-full cab");
        } catch (IllegalStateException expected) {
            System.out.println("  [OK] Rejected: " + expected.getMessage());
        }
        System.out.println("  Real buildings: the button light stays on; dispatch retries as");
        System.out.println("  soon as any car frees up. Our dispatcher throws — fail loud in a");
        System.out.println("  demo, retry-loop in production.\n");

        System.out.println("Tick-by-tick (boarding both, running to floor 7):");
        runToRest(building);
        System.out.println("  " + tiny);
        System.out.println();
        pause();
    }

    // ------------------------------------------------------------------
    private static void section5DirectionReversal() {
        System.out.println("=== Section 5: Direction reversal & sweep boundary (LOOK) ===");
        System.out.println("Cab parked at floor 3. Requests (arrival order):");
        System.out.println("  r1: hall call floor 5 (DOWN) — Kabir on 5 wants down");
        System.out.println("  r2: hall call floor 8 (DOWN) — Neha on 8 wants down");
        System.out.println("  r3: hall call floor 4 (UP)   — Priya on 4 wants up");
        System.out.println();
        System.out.println("Expected LOOK behaviour: nearest request is floor 4 (UP) -> sweep UP.");
        System.out.println("The UP sweep first collects floor 4 (matching intent). Nothing");
        System.out.println("serviceable remains above (5-DOWN and 8-DOWN are wrong-intent),");
        System.out.println("so the cab travels to the SWEEP BOUNDARY — floor 8, the farthest");
        System.out.println("outstanding request — reverses there, boards Neha going DOWN,");
        System.out.println("then collects floor 5 on the way down. No passenger starves.\n");

        ElevatorSystem building = new ElevatorSystem(10);
        ElevatorCar cab = new ElevatorCar("R1", 3, 10, 8, new ScanScheduler());
        building.addCar(cab);

        building.hallCall(5, Direction.DOWN);
        building.hallCall(8, Direction.DOWN);
        building.hallCall(4, Direction.UP);

        System.out.println("Tick-by-tick:");
        runToRest(building);

        System.out.println("\nResult: " + cab + " — travelled "
                + cab.getTotalFloorsTravelled() + " floors, " + cab.getTotalStops()
                + " stops. All three passengers served, zero starvation.");
        System.out.println("A naive intent-only SCAN (no boundary step) would idle at floor 4");
        System.out.println("with the two DOWN calls pending forever — the classic starvation bug.");
        System.out.println();
        pause();
    }

    // ------------------------------------------------------------------
    private static void section6StateGuards() {
        System.out.println("=== Section 6: Validation and state-machine guardrails ===");
        expectRejection("car starting outside the building",
                () -> new ElevatorCar("X", 11, 10, 8, new ScanScheduler()));
        expectRejection("car with zero capacity",
                () -> new ElevatorCar("X", 1, 10, 0, new ScanScheduler()));
        expectRejection("car with a null scheduler",
                () -> new ElevatorCar("X", 1, 10, 8, null));
        expectRejection("duplicate elevator id in one building", () -> {
            ElevatorSystem b = new ElevatorSystem(10);
            b.addCar(new ElevatorCar("DUP", 1, 10, 8, new ScanScheduler()));
            b.addCar(new ElevatorCar("DUP", 5, 10, 8, new ScanScheduler()));
        });
        expectRejection("hall call below floor 1", () -> {
            ElevatorSystem b = new ElevatorSystem(10);
            b.addCar(new ElevatorCar("Y", 1, 10, 8, new ScanScheduler()));
            b.hallCall(0, Direction.UP);
        });
        expectRejection("hall call with a non-moving direction", () -> {
            ElevatorSystem b = new ElevatorSystem(10);
            b.addCar(new ElevatorCar("Y", 1, 10, 8, new ScanScheduler()));
            b.hallCall(3, null);
        });
        expectRejection("floor number out of range for a Floor value object",
                () -> new Floor(13, 10));

        System.out.println("\nState machine recap (printed for the interviewer):");
        System.out.println("  IDLE        --(request elsewhere)-->  MOVING_UP / MOVING_DOWN");
        System.out.println("  MOVING_*    --(arrival at stop)---->  DOORS_OPEN");
        System.out.println("  DOORS_OPEN  --(doors closed)------>  MOVING_* (more stops) or IDLE");
        System.out.println("  Direction reversal happens ONLY at LOOK turnaround points:");
        System.out.println("  a sweep finishes its last outstanding stop in-direction, then");
        System.out.println("  flips — never mid-sweep (that's what prevents thrash).");
        System.out.println();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Tick the building until at rest, printing one numbered tick line each. */
    private static void runToRest(ElevatorSystem building) {
        int tick = 0;
        while (tick < RUN_TIMEOUT_TICKS) {
            tick++;
            boolean more = building.tickAll();
            if (!more) {
                break;
            }
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(TICK_PAUSE_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private interface Construction {
        void construct();
    }

    private static void expectRejection(String description, Construction construction) {
        try {
            construction.construct();
            System.out.println("  [BUG] Accepted: " + description);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            System.out.println("  [OK] Rejected: " + description);
            System.out.println("        Reason: " + expected.getMessage());
        }
    }
}
