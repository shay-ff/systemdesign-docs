import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock payment processor. In production this hides a real gateway behind the
 * same two methods — that is exactly what you tell the interviewer.
 */
public class PaymentService {
    private final AtomicLong paymentSeq = new AtomicLong(0);
    private final List<Payment> history = new CopyOnWriteArrayList<>();

    public Payment charge(Reservation reservation, double amount) {
        return record(reservation, amount, "CHARGE");
    }

    public Payment refund(Reservation reservation, double amount) {
        return record(reservation, amount, "REFUND");
    }

    private Payment record(Reservation reservation, double amount, String kind) {
        if (reservation == null) {
            throw new IllegalArgumentException("Payment needs a non-null reservation");
        }
        if (amount < 0) {
            throw new IllegalArgumentException(
                kind + " amount cannot be negative, got " + amount);
        }
        Payment payment = new Payment(
                "PAY-" + paymentSeq.incrementAndGet(),
                reservation.getReservationId(), amount, kind);
        history.add(payment);
        return payment;
    }

    public List<Payment> paymentsFor(String reservationId) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : history) {
            if (p.getReservationId().equals(reservationId)) {
                result.add(p);
            }
        }
        return result;
    }

    public List<Payment> getHistory() {
        return new ArrayList<>(history);
    }
}
