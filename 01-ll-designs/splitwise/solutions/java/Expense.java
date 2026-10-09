import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A single expense: an amount, one payer, and the list of splits describing
 * who participates and how. Immutable after construction (defensive copies
 * both in and out) so it can be shared across services safely.
 *
 * Money is stored in paise (long) - a single integer unit avoids the
 * classic 0.1+0.2 != 0.3 problem entirely.
 */
public class Expense {
    private final String id;
    private final String description;
    private final long amountInPaise;
    private final String paidByUserId;
    private final List<Split> splits;

    public Expense(String id, String description, BigDecimal amountRupees,
                   String paidByUserId, List<Split> splits) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Expense id cannot be null or empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Expense description cannot be null or empty");
        }
        if (amountRupees == null || amountRupees.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive: " + amountRupees);
        }
        if (paidByUserId == null || paidByUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("Payer id cannot be null or empty");
        }
        if (splits == null || splits.size() < 2) {
            throw new IllegalArgumentException(
                "An expense needs at least 2 participants (payer included), got "
                + (splits == null ? 0 : splits.size()));
        }
        this.id = id;
        this.description = description.trim();
        this.amountInPaise = amountRupees.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact();
        this.paidByUserId = paidByUserId;
        this.splits = new ArrayList<>(splits);

        validateSplits();
    }

    /**
     * Cross-split validation. Individual splits validate themselves; only
     * here can we check things that need the whole list:
     * 1. no duplicate participants,
     * 2. all-PERCENT expenses must sum to exactly 100%,
     * 3. all-EXACT expenses must sum to the total amount.
     * Mixed lists are rejected rather than silently half-validated - in an
     * interview, say this out loud; it shows you think about invariants.
     */
    private void validateSplits() {
        List<String> seen = new ArrayList<>();
        for (Split split : splits) {
            if (!seen.add(split.getUserId())) {
                throw new IllegalArgumentException(
                    "Duplicate participant in expense '" + description + "': " + split.getUserId());
            }
        }

        boolean allPercent = true;
        boolean allExact = true;
        boolean allEqual = true;
        for (Split split : splits) {
            allPercent &= split instanceof PercentSplit;
            allExact &= split instanceof ExactSplit;
            allEqual &= split instanceof EqualSplit;
        }

        if (allPercent) {
            int sum = 0;
            for (Split split : splits) {
                sum += ((PercentSplit) split).getPercentBasisPoints();
            }
            if (sum != PercentSplit.PERCENT_SCALE) {
                throw new IllegalArgumentException(
                    "Percent splits of '" + description + "' must sum to 100.00% but sum to "
                    + String.format("%.2f", sum / 100.0) + "%");
            }
        } else if (allExact) {
            long sum = 0;
            for (Split split : splits) {
                sum += ((ExactSplit) split).getAmountInPaise();
            }
            if (sum != amountInPaise) {
                throw new IllegalArgumentException("Exact splits of '" + description
                    + "' must sum to " + Split.formatRupees(amountInPaise)
                    + " but sum to " + Split.formatRupees(sum));
            }
        } else if (!allEqual) {
            throw new IllegalArgumentException(
                "Mixed split types are not supported for one expense: '" + description
                + "'. Use all-Equal, all-Percent, or all-Exact.");
        }
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }

    public String getPaidByUserId() {
        return paidByUserId;
    }

    public List<Split> getSplits() {
        return Collections.unmodifiableList(splits);
    }

    @Override
    public String toString() {
        return description + " (" + Split.formatRupees(amountInPaise)
            + ", paid by " + paidByUserId + ")";
    }

    /** Convenience factory for equal splits across a set of users. */
    public static List<Split> equalAmong(String... userIds) {
        List<Split> splits = new ArrayList<>();
        for (String userId : userIds) {
            splits.add(new EqualSplit(userId));
        }
        return splits;
    }

    /** Convenience factory: builds a percent-split list from (user, percent) pairs. */
    public static List<Split> percentAmong(Object... userPercentPairs) {
        if (userPercentPairs == null || userPercentPairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Expected (userId, percent) pairs, got " + Arrays.toString(userPercentPairs));
        }
        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < userPercentPairs.length; i += 2) {
            splits.add(new PercentSplit((String) userPercentPairs[i],
                (Integer) userPercentPairs[i + 1]));
        }
        return splits;
    }
}
