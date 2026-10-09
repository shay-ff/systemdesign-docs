import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A screening: one movie on one screen at one start time.
 *
 * On creation the show instantiates a {@link ShowSeat} for EVERY physical
 * seat on the screen - the per-show availability rows. This is the pivot of
 * the design: seat availability is show-scoped data, created fresh per show,
 * never mutated on the shared {@link Seat} catalog objects.
 *
 * Also owns convenience pricing knobs (per-show multipliers like a weekend
 * surge) that PriceCalculator reads.
 */
public class Show {
    private final String showId;
    private final Movie movie;
    private final Screen screen;
    private final Theatre theatre;
    private final LocalDateTime startTime;
    private final double priceMultiplier;
    private final Map<String, ShowSeat> showSeats = new LinkedHashMap<>();

    public Show(String showId, Movie movie, Screen screen, Theatre theatre,
                LocalDateTime startTime, double priceMultiplier) {
        if (showId == null || showId.trim().isEmpty()) {
            throw new IllegalArgumentException("Show id cannot be null/empty");
        }
        if (movie == null) {
            throw new IllegalArgumentException("Show " + showId + " needs a movie");
        }
        if (screen == null) {
            throw new IllegalArgumentException("Show " + showId + " needs a screen");
        }
        if (theatre == null) {
            throw new IllegalArgumentException("Show " + showId + " needs a theatre");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Show " + showId + " needs a start time");
        }
        if (priceMultiplier <= 0) {
            throw new IllegalArgumentException("Price multiplier must be > 0 (got " + priceMultiplier + ")");
        }
        this.showId = showId;
        this.movie = movie;
        this.screen = screen;
        this.theatre = theatre;
        this.startTime = startTime;
        this.priceMultiplier = priceMultiplier;

        // Instantiate one availability row per physical seat on the screen.
        // All initially AVAILABLE; the booking engine's whole world.
        for (Seat seat : screen.getSeats()) {
            showSeats.put(seat.getSeatId(), new ShowSeat(showId, seat));
        }
    }

    public ShowSeat getShowSeat(String seatId) {
        ShowSeat showSeat = showSeats.get(seatId);
        if (showSeat == null) {
            throw new IllegalArgumentException("Seat " + seatId + " is not on screen "
                + screen.getScreenId() + " for show " + showId);
        }
        return showSeat;
    }

    /** All ShowSeat rows of this show, in stable layout order. */
    public List<ShowSeat> getShowSeats() {
        return new ArrayList<>(showSeats.values());
    }

    /** Seats in a given state (e.g. all AVAILABLE for the seat map UI). */
    public List<ShowSeat> getShowSeatsByStatus(SeatStatus status) {
        List<ShowSeat> result = new ArrayList<>();
        for (ShowSeat ss : showSeats.values()) {
            if (ss.getStatus() == status) {
                result.add(ss);
            }
        }
        return result;
    }

    public Map<String, ShowSeat> getShowSeatMap() {
        return Collections.unmodifiableMap(showSeats);
    }

    public String getShowId() {
        return showId;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public Theatre getTheatre() {
        return theatre;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    @Override
    public String toString() {
        return showId + ": " + movie.getTitle() + " @ " + theatre.getName() + "/"
            + screen.getName() + ", " + startTime;
    }
}
