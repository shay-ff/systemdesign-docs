import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory BookingRepository (HashMap would do for a demo, but
 * booking saves happen on payment threads while reads happen on API threads,
 * so ConcurrentHashMap is the honest choice and costs nothing).
 */
public class InMemoryBookingRepository implements BookingRepository {
    private final Map<String, Booking> bookingsById = new ConcurrentHashMap<>();

    @Override
    public Booking save(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be null");
        }
        bookingsById.put(booking.getBookingId(), booking);
        return booking;
    }

    @Override
    public Optional<Booking> findById(String bookingId) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Booking id cannot be null/empty");
        }
        return Optional.ofNullable(bookingsById.get(bookingId));
    }

    @Override
    public List<Booking> findByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User id cannot be null/empty");
        }
        List<Booking> result = new ArrayList<>();
        for (Booking booking : bookingsById.values()) {
            if (userId.equals(booking.getUserId())) {
                result.add(booking);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<Booking> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(bookingsById.values()));
    }
}
