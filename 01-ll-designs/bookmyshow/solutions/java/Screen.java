import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An auditorium inside a theatre - owns the physical seat layout.
 *
 * The layout is built once via {@code addSeat(...)} (admin action). Each seat
 * gets a globally unique id "{screenId}-{row}{number}" so per-seat lock maps
 * and ShowSeat rows can be keyed by seat id alone, without dragging the
 * show/theatre around.
 *
 * A screen can host MANY shows across the day - the show:seat relationship is
 * 1:1 per show via ShowSeat, not via anything stored here.
 */
public class Screen {
    private final String screenId;
    private final String name;
    private final Map<String, Seat> seatsById = new LinkedHashMap<>();

    public Screen(String screenId, String name) {
        if (screenId == null || screenId.trim().isEmpty()) {
            throw new IllegalArgumentException("Screen id cannot be null/empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Screen name cannot be null/empty (screen " + screenId + ")");
        }
        this.screenId = screenId;
        this.name = name;
    }

    /** Adds a seat; ids are auto-derived as {screenId}-{row}{number}. */
    public Seat addSeat(String row, int number, SeatType seatType) {
        Seat seat = new Seat(seatId(row, number), row, number, seatType);
        if (seatsById.containsKey(seat.getSeatId())) {
            throw new IllegalArgumentException("Duplicate seat " + seat.getSeatId()
                + " on screen " + screenId);
        }
        seatsById.put(seat.getSeatId(), seat);
        return seat;
    }

    /** Convenience: adds a whole row of seats of one type (A1..A10). */
    public void addRow(String row, int fromNumber, int toNumber, SeatType seatType) {
        if (row == null || row.trim().isEmpty()) {
            throw new IllegalArgumentException("Seat row cannot be null/empty (screen " + screenId + ")");
        }
        if (fromNumber < 1 || toNumber < fromNumber) {
            throw new IllegalArgumentException("Row range invalid: from=" + fromNumber
                + ", to=" + toNumber + " (screen " + screenId + ")");
        }
        for (int n = fromNumber; n <= toNumber; n++) {
            addSeat(row, n, seatType);
        }
    }

    public String seatId(String row, int number) {
        return screenId + "-" + row + number;
    }

    public Seat getSeat(String seatId) {
        Seat seat = seatsById.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException("Seat " + seatId + " not found on screen " + screenId);
        }
        return seat;
    }

    public List<Seat> getSeats() {
        return new ArrayList<>(seatsById.values());
    }

    public Map<String, Seat> getSeatsById() {
        return Collections.unmodifiableMap(seatsById);
    }

    public String getScreenId() {
        return screenId;
    }

    public String getName() {
        return name;
    }

    public int seatCount() {
        return seatsById.size();
    }

    @Override
    public String toString() {
        return name + " (" + seatsById.size() + " seats)";
    }
}
