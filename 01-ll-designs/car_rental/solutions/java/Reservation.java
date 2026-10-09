import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A reservation owns its own state machine: guards against illegal transitions
 * live here (markInProgress/complete/cancel), NOT scattered in services.
 * Lifecycle: SCHEDULED -> IN_PROGRESS -> COMPLETED, or SCHEDULED -> CANCELLED.
 */
public class Reservation {
    private final String reservationId;
    private final Vehicle vehicle;
    private final Customer customer;
    private final Interval rentalWindow;
    private final int bookedKmAllowance;
    private ReservationStatus status;
    private Bill bill;

    public Reservation(String reservationId, Vehicle vehicle, Customer customer,
                       Interval rentalWindow) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reservation id cannot be null or empty");
        }
        this.reservationId = reservationId.trim();
        this.vehicle = Objects.requireNonNull(vehicle, "Reservation vehicle cannot be null");
        this.customer = Objects.requireNonNull(customer, "Reservation customer cannot be null");
        this.rentalWindow = Objects.requireNonNull(rentalWindow,
                "Reservation rental window cannot be null");
        this.bookedKmAllowance =
                (int) Math.min(Integer.MAX_VALUE,
                        vehicle.getDailyKmAllowance() * rentalWindow.days());
        this.status = ReservationStatus.SCHEDULED;
    }

    /** Only SCHEDULED reservations can be picked up. */
    public void markInProgress() {
        if (status != ReservationStatus.SCHEDULED) {
            throw new IllegalStateException(
                "Cannot pick up reservation " + reservationId + " in status " + status
                + "; pickup is only allowed from SCHEDULED");
        }
        this.status = ReservationStatus.IN_PROGRESS;
    }

    /** Only an IN_PROGRESS reservation can be completed (vehicle returned). */
    public void complete(Bill bill) {
        if (status != ReservationStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Cannot return vehicle for reservation " + reservationId + " in status "
                + status + "; return is only allowed from IN_PROGRESS");
        }
        this.bill = Objects.requireNonNull(bill, "Return bill cannot be null");
        this.status = ReservationStatus.COMPLETED;
    }

    /** Only SCHEDULED reservations can be cancelled; a trip in progress must be returned. */
    public void cancel() {
        if (status != ReservationStatus.SCHEDULED) {
            throw new IllegalStateException(
                "Cannot cancel reservation " + reservationId + " in status " + status
                + "; only SCHEDULED reservations can be cancelled");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    /** Active = the vehicle is (or will be) held by this reservation. */
    public boolean isActive() {
        return status == ReservationStatus.SCHEDULED || status == ReservationStatus.IN_PROGRESS;
    }

    public String getReservationId() {
        return reservationId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Interval getRentalWindow() {
        return rentalWindow;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public int getBookedKmAllowance() {
        return bookedKmAllowance;
    }

    /** Final bill; only present after return. */
    public Bill getBill() {
        return bill;
    }

    @Override
    public String toString() {
        return "Reservation " + reservationId + ": " + vehicle.getVehicleType() + " "
                + vehicle.getVehicleId() + " [" + rentalWindow + "] for "
                + customer.getName() + " — status " + status;
    }

    /** Immutable price breakdown, produced by a PricingStrategy. */
    public static final class Bill {
        private final long days;
        private final double baseAmount;
        private final double kmOverage;
        private final double latePenalty;
        private final double total;
        private final List<String> lines;

        public Bill(long days, double baseAmount, double kmOverage,
                    double latePenalty, double total, List<String> lines) {
            this.days = days;
            this.baseAmount = baseAmount;
            this.kmOverage = kmOverage;
            this.latePenalty = latePenalty;
            this.total = total;
            this.lines = lines == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(lines));
        }

        public long getDays() {
            return days;
        }

        public double getBaseAmount() {
            return baseAmount;
        }

        public double getKmOverage() {
            return kmOverage;
        }

        public double getLatePenalty() {
            return latePenalty;
        }

        public double getTotal() {
            return total;
        }

        public List<String> getLines() {
            return lines;
        }

        public String prettyPrint() {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append("    ").append(line).append('\n');
            }
            sb.append(String.format("    TOTAL PAYABLE: INR %.2f (%d day(s))%n", total, days));
            return sb.toString();
        }
    }
}
