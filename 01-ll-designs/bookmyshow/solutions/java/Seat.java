/**
 * A physical seat inside a {@link Screen} - a one-time catalog object.
 *
 * A seat is created when the admin lays out a screen and never changes.
 * Whether the seat is free for a GIVEN show is not a property of the seat
 * (a seat is free in the 6pm show while being booked in the 9pm show) - it is
 * a property of the {@link ShowSeat} row keyed by (show, seat). Keeping the
 * physical seat and the per-show availability separate is THE modelling move
 * of this problem.
 *
 * {@code seatId} is globally unique ("SCR1-A1" style, see Screen.seatId(...))
 * so per-seat locks and maps can be keyed by the seat alone.
 */
public class Seat {
    private final String seatId;
    private final String row;
    private final int number;
    private final SeatType seatType;

    public Seat(String seatId, String row, int number, SeatType seatType) {
        if (seatId == null || seatId.trim().isEmpty()) {
            throw new IllegalArgumentException("Seat id cannot be null/empty");
        }
        if (row == null || row.trim().isEmpty()) {
            throw new IllegalArgumentException("Seat row cannot be null/empty (seat " + seatId + ")");
        }
        if (number < 1) {
            throw new IllegalArgumentException("Seat number must be >= 1 (got " + number + ")");
        }
        if (seatType == null) {
            throw new IllegalArgumentException("Seat type cannot be null (seat " + seatId + ")");
        }
        this.seatId = seatId;
        this.row = row;
        this.number = number;
        this.seatType = seatType;
    }

    public String getSeatId() {
        return seatId;
    }

    public String getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    @Override
    public String toString() {
        return row + number + " [" + seatType.getLabel() + "]";
    }
}
