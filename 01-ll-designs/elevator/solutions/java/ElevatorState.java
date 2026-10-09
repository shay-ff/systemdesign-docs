/**
 * The car's state machine. Interviewers ask you to draw this on the whiteboard:
 *
 *   IDLE --(request accepted, target above)--> MOVING_UP
 *   IDLE --(request accepted, target below)--> MOVING_DOWN
 *   MOVING_UP --(arrival at a stop floor)--> DOORS_OPEN
 *   MOVING_DOWN --(arrival at a stop floor)--> DOORS_OPEN
 *   DOORS_OPEN --(doors close, more stops remain)--> MOVING_UP / MOVING_DOWN
 *   DOORS_OPEN --(doors close, no stops remain)--> IDLE
 *
 * Two deliberate decisions:
 *
 * 1. MOVING_UP / MOVING_DOWN are SEPARATE states, not one MOVING state with a
 *    direction field. Why? Because the legal transitions differ (a LOOK
 *    sweep going up must not reverse until the topmost target is serviced),
 *    and because state-machine clarity beats saving one enum constant.
 *    This is the classic interview trap: one MOVING state forces the reversal
 *    rule to live in scattered if-chains instead of the transition table.
 *
 * 2. There is no DOORS_CLOSED state: "closed and about to move" is the same
 *    as MOVING_* once the first tick fires. Fewer states = fewer illegal
 *    transitions to guard. DOORS_OPEN is a state because the car must NOT
 *    move while doors are open (a safety invariant worth enforcing).
 */
public enum ElevatorState {
    IDLE,
    MOVING_UP,
    MOVING_DOWN,
    DOORS_OPEN;

    /** True when the car must not move right now. */
    public boolean isSafeToLoad() {
        return this == DOORS_OPEN;
    }

    /** True while the cab is travelling between floors. */
    public boolean isMoving() {
        return this == MOVING_UP || this == MOVING_DOWN;
    }
}
