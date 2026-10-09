/**
 * Payment gateway seam. Everything downstream of "charge this much" is an
 * integration detail (Razorpay order + capture, retries, webhooks) - hidden
 * behind this interface, exactly as you would tell the interviewer.
 *
 * The mock impl ({@code MockPaymentGateway}) lets the demo script outcomes
 * (success on one call, failure on the next) so both the happy path and the
 * failure-releases-seats path can be shown.
 */
public interface PaymentGateway {
    /**
     * Attempts to charge {@code amountInRupees} for {@code bookingId}.
     * NEVER throws on business failure - a declined card is a FAILURE
     * result, not an exception. Exceptions are reserved for programmer
     * errors (nulls) and infrastructure faults beyond the demo's scope.
     */
    PaymentResult charge(String bookingId, String userId, double amountInRupees);
}
