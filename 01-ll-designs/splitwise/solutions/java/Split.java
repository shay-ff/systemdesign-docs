import java.math.BigDecimal;

/**
 * Base class for "how one participant's share of an expense is described".
 *
 * IMPORTANT - this is data, not behaviour: a Split says "this user owes X%
 * of this expense"; the *computation* of money amounts lives in
 * ExpenseService. The subclasses differ only in the constraint they place on
 * themselves and how they describe their share, which is why inheritance
 * (a small, closed, is-a hierarchy) is the right tool and a Strategy is NOT.
 *
 * Money is BigDecimal at the edges and long paise in the core. Never double
 * for money - interviewers at fintech companies (Razorpay included) actively
 * probe this.
 */
public abstract class Split {
    protected final String userId;

    protected Split(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Split user id cannot be null or empty");
        }
        this.userId = userId;
    }

    /** The participant this split belongs to. */
    public String getUserId() {
        return userId;
    }

    /**
     * This participant's share of an expense of the given total, in paise.
     *
     * Contract: only SELF-CONTAINED split types (Percent, Exact) can answer
     * from their own data. An EqualSplit cannot - its amount depends on how
     * many other splits share the expense - so ExpenseService computes
     * equal lists itself (see ExpenseService.computeShares). Forcing a fake
     * answer here would be worse polymorphism than an honest "cannot".
     */
    public abstract long computeShareInPaise(long totalInPaise);

    /** Human description used in demo output, e.g. "33.33%" or "2 shares". */
    public abstract String describe();

    /** Formats paise as a readable rupee string, e.g. "Rs.150.00". */
    public static String formatRupees(long paise) {
        if (paise < 0) {
            return "-" + formatRupees(-paise);
        }
        return "Rs." + BigDecimal.valueOf(paise, 2).toPlainString();
    }
}
