import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A priced hold over seats of one show - the result of
 * {@code BookingService.lockSeats(...)}.
 *
 * The hold is a VALUE receipt: it says which seats are locked for which user
 * until when, and for how much. The user then pays against it; on success
 * {@code BookingService.confirmHold(hold, bookingId)} flips the seats to
 * BOOKED and mints a {@link Booking}.
 *
 * It carries no behavioural power of its own (anyone can construct one) - the
 * booking engine re-validates every seat against its own state at confirm
 * time, so a forged/stale hold cannot book anything.
 */
public class SeatHold {
    private final String holdId;
    private final String showId;
    private final String userId;
    private final List<String> seatIds;
    private final double totalAmount;
    private final Instant expiresAt;

    public SeatHold(String holdId, String showId, String userId, List<String> seatIds,
                    double totalAmount, Instant expiresAt) {
        if (holdId == null || holdId.trim().isEmpty()) {
            throw new IllegalArgumentException("Hold id cannot be null/empty");
        }
        if (showId == null || showId.trim().isEmpty()) {
            throw new IllegalArgumentException("Show id cannot be null/empty (hold " + holdId + ")");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User id cannot be null/empty (hold " + holdId + ")");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat list cannot be empty (hold " + holdId + ")");
        }
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("Hold total must be > 0 (got " + totalAmount + ")");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Hold expiry instant cannot be null (hold " + holdId + ")");
        }
        this.holdId = holdId;
        this.showId = showId;
        this.userId = userId;
        this.seatIds = new ArrayList<>(seatIds);
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
    }

    public String getHoldId() {
        return holdId;
    }

    public String getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getSeatIds() {
        return Collections.unmodifiableList(seatIds);
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "Hold[" + holdId + "] show " + showId + " seats " + seatIds
            + " for " + userId + ", Rs." + String.format("%.2f", totalAmount)
            + ", expires " + expiresAt;
    }
}
