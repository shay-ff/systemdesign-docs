import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Scripted mock gateway for the demo and tests.
 *
 * Holds a queue of scripted outcomes; each charge() pops one. When the queue
 * runs dry it returns SUCCESS (default). This keeps demo narrative under the
 * caller's control: script a FAILURE before the call you want to fail.
 */
public class MockPaymentGateway implements PaymentGateway {
    private final Queue<PaymentResult> scriptedOutcomes = new ArrayDeque<>();
    private int callCounter = 0;

    /** Scripts the next outcomes, in order (no varargs enum - Java 11 fine). */
    public MockPaymentGateway script(PaymentResult... outcomes) {
        if (outcomes == null) {
            throw new IllegalArgumentException("Scripted outcomes cannot be null");
        }
        for (PaymentResult outcome : outcomes) {
            if (outcome == null) {
                throw new IllegalArgumentException("Scripted outcome cannot be null");
            }
            scriptedOutcomes.add(outcome);
        }
        return this;
    }

    @Override
    public PaymentResult charge(String bookingId, String userId, double amountInRupees) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookingId cannot be null/empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null/empty");
        }
        if (amountInRupees <= 0) {
            throw new IllegalArgumentException("Charge amount must be > 0 (got " + amountInRupees + ")");
        }
        callCounter++;
        PaymentResult outcome = scriptedOutcomes.poll();
        PaymentResult result = outcome != null ? outcome
            : PaymentResult.success("pay-" + callCounter);
        System.out.println("    [gateway] charge for booking " + bookingId + " (" + userId
            + ", Rs." + String.format("%.2f", amountInRupees) + ") -> " + result);
        return result;
    }

    public int getCallsMade() {
        return callCounter;
    }
}
