import java.math.BigDecimal;

/**
 * "This user owes exactly this amount." The amount is given in rupees as a
 * BigDecimal and stored in paise (long) so every share - computed or exact -
 * ends up in the same integer unit, ready to be summed without mixed types.
 */
public class ExactSplit extends Split {
    private final long amountInPaise;

    public ExactSplit(String userId, BigDecimal exactAmountRupees) {
        super(userId);
        if (exactAmountRupees == null) {
            throw new IllegalArgumentException("ExactSplit amount cannot be null");
        }
        if (exactAmountRupees.signum() <= 0) {
            throw new IllegalArgumentException("ExactSplit amount must be positive: "
                + exactAmountRupees);
        }
        this.amountInPaise = exactAmountRupees.movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }

    @Override
    public long computeShareInPaise(long totalInPaise) {
        return amountInPaise; // exact by definition; total is ignored
    }

    @Override
    public String describe() {
        return "exact " + formatRupees(amountInPaise);
    }
}
