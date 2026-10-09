/**
 * A directed money flow: "fromUser owes toUser the given amount".
 *
 * Used for BOTH raw per-expense flows (output of ExpenseService) and
 * post-simplification settlement suggestions (output of
 * SimplifyDebtService) - one small value class serves both, which keeps
 * the demo output uniform.
 */
public class Transfer {
    private final String expenseId; // null for settlement transfers
    private final String fromUserId;
    private final String toUserId;
    private final long amountInPaise;

    public Transfer(String expenseId, String fromUserId, String toUserId, long amountInPaise) {
        if (fromUserId == null || toUserId == null) {
            throw new IllegalArgumentException("Transfer endpoints cannot be null");
        }
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("Transfer endpoints must differ: " + fromUserId);
        }
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException(
                "Transfer amount must be positive, got " + Split.formatRupees(amountInPaise));
        }
        this.expenseId = expenseId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amountInPaise = amountInPaise;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }

    @Override
    public String toString() {
        return fromUserId + " -> " + toUserId + " : " + Split.formatRupees(amountInPaise);
    }
}
