/**
 * Value object for a payment attempt outcome. A boolean return hides the
 * failure reason; a result object carries it, so the demo can narrate WHY a
 * payment failed (the interviewer will ask).
 */
public class PaymentResult {
    public enum Status { SUCCESS, FAILURE }

    private final Status status;
    private final String paymentId;
    private final String failureReason;

    private PaymentResult(Status status, String paymentId, String failureReason) {
        this.status = status;
        this.paymentId = paymentId;
        this.failureReason = failureReason;
    }

    public static PaymentResult success(String paymentId) {
        if (paymentId == null || paymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment id cannot be null/empty on success");
        }
        return new PaymentResult(Status.SUCCESS, paymentId, null);
    }

    public static PaymentResult failure(String failureReason) {
        if (failureReason == null || failureReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason cannot be null/empty");
        }
        return new PaymentResult(Status.FAILURE, null, failureReason);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        return status == Status.SUCCESS
            ? "SUCCESS (payment " + paymentId + ")"
            : "FAILURE (" + failureReason + ")";
    }
}
