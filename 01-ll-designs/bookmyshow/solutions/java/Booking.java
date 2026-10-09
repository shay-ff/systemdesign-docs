import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A confirmed booking (paid). Owns a booking lifecycle and an immutable copy
 * of what was paid for - show, seats, amount, payment id.
 *
 * Notably a Booking is created ONLY on payment success - a failed-payment
 * attempt never produces a Booking row; it just releases the seats. This is
 * the "payment is not part of the booking aggregate until it succeeds"
 * distinction interviewers like to hear stated.
 */
public class Booking {
    private final String bookingId;
    private final String userId;
    private final Show show;
    private final List<String> seatIds;
    private final double totalAmount;
    private final String paymentId;
    private final Instant bookedAt;

    public Booking(String bookingId, String userId, Show show, List<String> seatIds,
                   double totalAmount, String paymentId, Instant bookedAt) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Booking id cannot be null/empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User id cannot be null/empty (booking " + bookingId + ")");
        }
        if (show == null) {
            throw new IllegalArgumentException("Show cannot be null (booking " + bookingId + ")");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat list cannot be empty (booking " + bookingId + ")");
        }
        if (totalAmount < 0) {
            throw new IllegalArgumentException("Total amount cannot be negative (got " + totalAmount + ")");
        }
        this.bookingId = bookingId;
        this.userId = userId;
        this.show = show;
        this.seatIds = new ArrayList<>(seatIds);
        this.totalAmount = totalAmount;
        this.paymentId = paymentId;
        this.bookedAt = bookedAt;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getUserId() {
        return userId;
    }

    public Show getShow() {
        return show;
    }

    public List<String> getSeatIds() {
        return Collections.unmodifiableList(seatIds);
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    @Override
    public String toString() {
        return "Booking[" + bookingId + "] " + show.getMovie().getTitle() + " @ "
            + show.getStartTime() + " seats " + seatIds + " total Rs."
            + String.format("%.2f", totalAmount);
    }
}
