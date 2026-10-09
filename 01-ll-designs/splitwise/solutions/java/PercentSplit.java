/**
 * "This user owes P percent of the expense." The percent is stored in
 * basis points (1% = 100 bp) to avoid floating-point drift: 33.33% is
 * exactly 3333 bp, not 33.33 +- epsilon.
 *
 * Validation that all percents of an expense sum to 100% happens in
 * Expense/ExpenseService, where the full list is known - a single split
 * cannot know whether it alone is valid.
 */
public class PercentSplit extends Split {
    public static final int PERCENT_SCALE = 10000; // 100.00% == 10000 basis points

    private final int percentBasisPoints;

    /**
     * @param percentBasisPoints share in basis points, e.g. 3333 for 33.33%
     */
    public PercentSplit(String userId, int percentBasisPoints) {
        super(userId);
        if (percentBasisPoints <= 0 || percentBasisPoints > PERCENT_SCALE) {
            throw new IllegalArgumentException(
                "PercentSplit must be in (0%, 100%], got " + asPercent(percentBasisPoints) + "%");
        }
        this.percentBasisPoints = percentBasisPoints;
    }

    public int getPercentBasisPoints() {
        return percentBasisPoints;
    }

    /** e.g. 3333 -> 33.33 (for display and for the 100% sum check). */
    public double asPercent(int basisPoints) {
        return basisPoints / 100.0;
    }

    public double getPercent() {
        return asPercent(percentBasisPoints);
    }

    @Override
    public long computeShareInPaise(long totalInPaise) {
        return Math.round((double) totalInPaise * percentBasisPoints / PERCENT_SCALE);
    }

    @Override
    public String describe() {
        return String.format("%.2f%%", getPercent());
    }
}
