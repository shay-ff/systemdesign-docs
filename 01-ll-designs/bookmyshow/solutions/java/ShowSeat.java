import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The availability of ONE physical seat for ONE show - the row that the
 * whole booking engine revolves around. This is the class interviewers are
 * probing when they ask "how do you prevent two users from booking the same
 * seat?"
 *
 * State machine (transitions guarded HERE, not in the service - the object
 * protects its own invariants):
 *
 *   AVAILABLE -> LOCKED -> BOOKED          happy path
 *              LOCKED -> AVAILABLE         payment failed / explicit release
 *              LOCKED -> EXPIRED -> AVAILABLE  hold timed out (sweeper)
 *
 * Thread-safety notes:
 *  - {@code status} is an AtomicReference so a failed compareAndSet means
 *    another thread won the seat - that IS the optimistic backstop under the
 *    pessimistic outer lock (belt and braces: the lock serializes contenders,
 *    the CAS guarantees no lost update can slip through even if the outer
 *    locking discipline is ever violated - e.g. by a future code path).
 *  - {@code holdExpiresAt} is the expiry instant written at lock time and read
 *    by the expiry sweeper.
 *
 * EXPIRED is a transient marker state: the sweeper sets it (so observers can
 * see why a seat went free) and immediately moves the seat to AVAILABLE, so
 * the seat is re-lockable. The demo prints both transitions.
 */
public class ShowSeat {
    private final String showId;
    private final Seat seat;
    private final AtomicReference<SeatStatus> status = new AtomicReference<>(SeatStatus.AVAILABLE);
    private volatile String lockedByUserId;
    private volatile Instant holdExpiresAt;

    public ShowSeat(String showId, Seat seat) {
        if (showId == null || showId.trim().isEmpty()) {
            throw new IllegalArgumentException("Show id cannot be null/empty");
        }
        if (seat == null) {
            throw new IllegalArgumentException("Seat cannot be null (show " + showId + ")");
        }
        this.showId = showId;
        this.seat = seat;
    }

    /**
     * Places a hold: AVAILABLE -> LOCKED for the given user, expiring at
     * {@code expiresAt}. Fails (returns false) if the seat is in ANY other
     * state - this is the double-booking rejection point.
     *
     * Implemented as a CAS loop-free single attempt: only AVAILABLE can
     * transition to LOCKED, so one compareAndSet is enough and a false return
     * means the seat is genuinely taken.
     */
    public boolean tryLock(String userId, Instant expiresAt) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null/empty");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Hold expiry instant cannot be null");
        }
        if (status.compareAndSet(SeatStatus.AVAILABLE, SeatStatus.LOCKED)) {
            this.lockedByUserId = userId;
            this.holdExpiresAt = expiresAt;
            return true;
        }
        return false;
    }

    /**
     * LOCKED -> BOOKED. Only the locking user may confirm. Called after
     * payment success; idempotent-safe in that a second confirm attempt on an
     * already-BOOKED seat throws instead of double-confirming.
     */
    public void confirmBooking(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null/empty");
        }
        if (!status.compareAndSet(SeatStatus.LOCKED, SeatStatus.BOOKED)) {
            throw new IllegalStateException("Seat " + seat.getSeatId() + " for show " + showId
                + " cannot be BOOKED from state " + status.get()
                + " (not LOCKED, or not locked by this user)");
        }
        if (!userId.equals(lockedByUserId)) {
            // Extremely defensive: CAS already failed/ok'd; a mismatched user
            // confirming must never slip through. Roll back nothing (the CAS
            // above only succeeds from LOCKED), just reject loudly.
            throw new IllegalStateException("Seat " + seat.getSeatId()
                + " is locked by user '" + lockedByUserId + "', not '" + userId + "'");
        }
        this.holdExpiresAt = null;
    }

    /**
     * LOCKED -> AVAILABLE: payment failed, user backed out, or admin cancel.
     * Throwing (rather than returning false) is deliberate - every caller
     * site in the happy path should already know the seat is LOCKED.
     */
    public void release(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null/empty");
        }
        if (!userId.equals(lockedByUserId)) {
            throw new IllegalStateException("Seat " + seat.getSeatId() + " is locked by user '"
                + lockedByUserId + "', so user '" + userId + "' cannot release it");
        }
        if (!status.compareAndSet(SeatStatus.LOCKED, SeatStatus.AVAILABLE)) {
            throw new IllegalStateException("Seat " + seat.getSeatId() + " for show " + showId
                + " cannot be RELEASED from state " + status.get() + " (only LOCKED seats release)");
        }
        this.lockedByUserId = null;
        this.holdExpiresAt = null;
    }

    /**
     * Expiry sweeper entry point: if the hold has lapsed, move the seat
     * EXPIRED then immediately AVAILABLE (re-lockable). Returns true if this
     * call performed the release - used by the sweeper for logging.
     */
    public boolean expireIfLapsed(Instant now) {
        if (status.get() != SeatStatus.LOCKED) {
            return false;
        }
        Instant expiry = this.holdExpiresAt;
        if (expiry == null || now.isBefore(expiry)) {
            return false;
        }
        // Mark EXPIRED so observers can tell "went free because the hold
        // timed out" apart from "never locked"; then free it immediately.
        if (status.compareAndSet(SeatStatus.LOCKED, SeatStatus.EXPIRED)) {
            status.set(SeatStatus.AVAILABLE);
            String user = lockedByUserId;
            lockedByUserId = null;
            holdExpiresAt = null;
            return user != null; // true => this sweeper call released it
        }
        return false;
    }

    public boolean isAvailable() {
        return status.get() == SeatStatus.AVAILABLE;
    }

    public boolean isLockedBy(String userId) {
        return status.get() == SeatStatus.LOCKED && userId.equals(lockedByUserId);
    }

    public String getLockedByUserId() {
        return lockedByUserId;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public String getShowId() {
        return showId;
    }

    public Seat getSeat() {
        return seat;
    }

    public SeatType getSeatType() {
        return seat.getSeatType();
    }

    public SeatStatus getStatus() {
        return status.get();
    }

    @Override
    public String toString() {
        return seat.getSeatId() + "=" + status.get();
    }
}
