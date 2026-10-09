/**
 * Lifecycle of one (show, seat) pair - the state machine at the heart of
 * BookMyShow.
 *
 * Transitions (guarded in {@link ShowSeat}):
 *
 *   AVAILABLE --lock(user)--> LOCKED
 *   LOCKED    --confirm()-->  BOOKED     (payment succeeded)
 *   LOCKED    --release()-->  AVAILABLE (payment failed / user gave up /
 *                                     hold expired via the sweeper)
 *
 * Why a LOCKED intermediate state at all? Because payment takes seconds-to-
 * minutes over a real gateway, and we cannot hold a DB-wide freeze while a
 * card is authorizing. LOCKED = "reserved for this user for N seconds,
 * pending payment". The seats fall back to AVAILABLE automatically if payment
 * never completes - no manual unblock, no permanently stranded inventory.
 *
 * A BOOKED seat is terminal for that show: once confirmed it can only move via
 * an explicit cancel flow (out of scope here, but the guard would be
 * BOOKED --cancel--> AVAILABLE).
 */
public enum SeatStatus {
    AVAILABLE("Available"),
    LOCKED("Locked (hold placed, payment pending)"),
    BOOKED("Booked (paid & confirmed)"),
    EXPIRED("Expired (hold timed out, seat released)");

    private final String label;

    SeatStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
