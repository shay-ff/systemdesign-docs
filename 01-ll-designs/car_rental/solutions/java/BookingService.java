import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The facade customers and the demo talk to. Coordinates store inventory,
 * the overlap rule (double-booking prevention), pricing, and payments.
 *
 * Concurrency note: book() is check-then-act. Single-threaded here by design;
 * production options (per-vehicle lock, optimistic versioning, DB range
 * exclusion constraint) are documented in explanation.md.
 */
public class BookingService {
    private final AtomicLong reservationSeq = new AtomicLong(0);
    private final ConcurrentHashMap<String, Reservation> reservationsById =
            new ConcurrentHashMap<>();
    private final List<Store> stores = new ArrayList<>();
    private final PricingStrategy pricing;
    private final PaymentService paymentService;

    public BookingService(PricingStrategy pricing, PaymentService paymentService) {
        this.pricing = Objects.requireNonNull(pricing, "Pricing strategy cannot be null");
        this.paymentService = Objects.requireNonNull(paymentService,
                "Payment service cannot be null");
    }

    public void addStore(Store store) {
        if (store == null) {
            throw new IllegalArgumentException("Cannot add a null store");
        }
        for (Store existing : stores) {
            if (existing.getStoreId().equals(store.getStoreId())) {
                throw new IllegalArgumentException("Store id already exists: "
                        + store.getStoreId());
            }
        }
        stores.add(store);
    }

    public List<Store> findStores(String city) {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City cannot be null or empty");
        }
        List<Store> result = new ArrayList<>();
        for (Store s : stores) {
            if (s.getLocation().getCity().equalsIgnoreCase(city.trim())) {
                result.add(s);
            }
        }
        return result;
    }

    public List<Vehicle> searchAvailable(Store store, VehicleType type, Interval window) {
        if (store == null) {
            throw new IllegalArgumentException("Store cannot be null");
        }
        return store.searchAvailable(type, window);
    }

    /**
     * Book a vehicle for a window. THE CRUX: rejects any window that overlaps
     * an active reservation on the same vehicle at that store.
     *
     * @return the created SCHEDULED reservation (payment already charged)
     * @throws IllegalStateException on a double-booking conflict
     */
    public Reservation book(Store store, Vehicle vehicle, Customer customer, Interval window) {
        if (store == null) {
            throw new IllegalArgumentException("Store cannot be null");
        }
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null");
        }
        if (customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }
        if (window == null) {
            throw new IllegalArgumentException("Rental window cannot be null");
        }
        if (store.getInventory().findById(vehicle.getVehicleId()) != vehicle) {
            throw new IllegalArgumentException("Vehicle " + vehicle.getVehicleId()
                    + " does not belong to store " + store.getStoreId());
        }

        Reservation conflict = store.findConflictingReservation(vehicle, window);
        if (conflict != null) {
            throw new IllegalStateException(
                "Double-booking prevented: vehicle " + vehicle.getVehicleId()
                + " is already reserved " + conflict.getRentalWindow()
                + " (reservation " + conflict.getReservationId()
                + ", status " + conflict.getStatus() + ") which overlaps requested "
                + window);
        }

        Reservation reservation = new Reservation(
                "RES-" + reservationSeq.incrementAndGet(), vehicle, customer, window);
        store.addReservation(reservation);
        reservationsById.put(reservation.getReservationId(), reservation);

        // Estimate upfront charge from the base pricing (no actuals yet).
        double estimate = pricing.calculate(vehicle, window, 0, window.getEnd()).getTotal();
        Payment payment = paymentService.charge(reservation, estimate);
        System.out.printf("  Payment: INR %.2f charged upfront (%s)%n",
                payment.getAmount(), payment.getPaymentId());
        return reservation;
    }

    /** Customer picks the vehicle up: SCHEDULED -> IN_PROGRESS. */
    public void pickup(String reservationId) {
        Reservation reservation = getReservation(reservationId);
        reservation.markInProgress();
        System.out.println("  Picked up: " + reservation.getVehicle() + " handed to "
                + reservation.getCustomer().getName());
    }

    /**
     * Customer returns the vehicle: IN_PROGRESS -> COMPLETED, final bill via
     * the pricing strategy (late penalty and km overage handled there).
     */
    public Reservation returnVehicle(String reservationId, int actualKm, LocalDate actualReturn) {
        Reservation reservation = getReservation(reservationId);
        if (actualKm < 0) {
            throw new IllegalArgumentException(
                "Actual km driven cannot be negative, got " + actualKm);
        }
        if (actualReturn == null) {
            throw new IllegalArgumentException("Actual return date cannot be null");
        }
        if (actualReturn.isBefore(reservation.getRentalWindow().getStart())) {
            throw new IllegalArgumentException(
                "Return date " + actualReturn + " is before the rental start "
                + reservation.getRentalWindow().getStart());
        }
        Reservation.Bill bill = pricing.calculate(reservation.getVehicle(),
                reservation.getRentalWindow(), actualKm, actualReturn);
        reservation.complete(bill);
        System.out.println("  Final bill for " + reservationId + ":");
        System.out.print(bill.prettyPrint());
        return reservation;
    }

    /** Cancel a SCHEDULED reservation and refund the upfront charge. */
    public void cancel(String reservationId) {
        Reservation reservation = getReservation(reservationId);
        reservation.cancel();
        double refunded = pricing.calculate(reservation.getVehicle(),
                reservation.getRentalWindow(), 0,
                reservation.getRentalWindow().getEnd()).getTotal();
        Payment payment = paymentService.refund(reservation, refunded);
        System.out.printf("  Cancelled %s — refunded INR %.2f (%s)%n",
                reservationId, payment.getAmount(), payment.getPaymentId());
    }

    public Reservation getReservation(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reservation id cannot be null or empty");
        }
        Reservation reservation = reservationsById.get(reservationId.trim());
        if (reservation == null) {
            throw new IllegalArgumentException("No reservation found with id " + reservationId);
        }
        return reservation;
    }

    public PaymentService getPaymentService() {
        return paymentService;
    }
}
