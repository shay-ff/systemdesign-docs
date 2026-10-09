/** A single payment event: a charge or a refund against a reservation. */
public class Payment {
    private final String paymentId;
    private final String reservationId;
    private final double amount;
    private final String kind; // "CHARGE" or "REFUND"

    public Payment(String paymentId, String reservationId, double amount, String kind) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.amount = amount;
        this.kind = kind;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public double getAmount() {
        return amount;
    }

    public String getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return String.format("Payment %s [%s, reservation %s]: INR %.2f",
                paymentId, kind, reservationId, amount);
    }
}
