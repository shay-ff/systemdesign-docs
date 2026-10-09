/**
 * "Split the total equally among N participants" - expressed as a share count
 * (default 1). Two shares = weighted split, useful for "the couple in the
 * flat pays for 2 people" without needing a PercentSplit.
 *
 * computeShareInPaise intentionally throws: an equal share's amount depends
 * on the OTHER splits in the expense (how many participants there are), so
 * ExpenseService computes equal-split lists as a batch. This is a deliberate
 * "do not force polymorphism where the data is not self-contained" decision.
 */
public class EqualSplit extends Split {
    private final int shares;

    public EqualSplit(String userId) {
        this(userId, 1);
    }

    public EqualSplit(String userId, int shares) {
        super(userId);
        if (shares < 1) {
            throw new IllegalArgumentException("EqualSplit shares must be >= 1, got " + shares);
        }
        this.shares = shares;
    }

    public int getShares() {
        return shares;
    }

    @Override
    public long computeShareInPaise(long totalInPaise) {
        throw new UnsupportedOperationException(
            "An EqualSplit's amount depends on the other splits in the expense;"
            + " ExpenseService computes equal lists (total * shares / totalShares)");
    }

    @Override
    public String describe() {
        return shares == 1 ? "1 share" : (shares + " shares");
    }
}
