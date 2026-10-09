import java.util.List;
import java.util.Optional;

/**
 * Persistence seam for bookings. Interface + in-memory impl keeps the booking
 * engine testable and lets the interview answer "how would you persist this?"
 * be "swap the impl; the engine never knows" (DI seam, DIP).
 */
public interface BookingRepository {
    Booking save(Booking booking);

    Optional<Booking> findById(String bookingId);

    List<Booking> findByUserId(String userId);

    List<Booking> findAll();
}
