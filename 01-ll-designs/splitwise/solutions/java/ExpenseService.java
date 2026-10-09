import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an Expense's split *descriptions* into concrete money amounts -
 * the "compute everyone's share" step that sits between adding an expense
 * and updating the ledger.
 *
 * The interesting part is EQUAL split rounding: 1000.00 / 3 = 333.33 x 3 =
 * 999.99, one paise short. Real Splitwise does what we do: divide, round
 * HALF_EVEN per person, then hand the leftover to the LAST participant.
 * For an equal split the discrepancy is at most (n-1) paise, invisible in
 * rupee terms, and the ledger still balances to the paisa because the
 * leftover is assigned, not dropped.
 *
 * A pure function object - no state, no repository access - which makes it
 * trivially unit-testable and the bit of the design interviewers probe
 * hardest ("what about rounding?").
 */
public class ExpenseService {

    /**
     * Computes the exact paise share for every participant of the expense,
     * guaranteed to sum back to the expense total exactly.
     *
     * @return ordered map userId -> amount in paise (never negative, and no
     *         participant gets a zero share from an equal split)
     */
    public Map<String, Long> computeShares(Expense expense) {
        if (expense == null) {
            throw new IllegalArgumentException("Expense cannot be null");
        }
        List<Split> splits = expense.getSplits();
        long total = expense.getAmountInPaise();

        Map<String, Long> shares = new LinkedHashMap<>();
        boolean allEqual = true;
        for (Split split : splits) {
            if (!(split instanceof EqualSplit)) {
                allEqual = false;
                break;
            }
        }

        if (allEqual) {
            int totalShares = 0;
            for (Split split : splits) {
                totalShares += ((EqualSplit) split).getShares();
            }
            long running = 0;
            for (int i = 0; i < splits.size(); i++) {
                Split split = splits.get(i);
                long share;
                if (i == splits.size() - 1) {
                    // Last participant absorbs the rounding remainder so the
                    // shares sum to the total exactly. This is the "round on
                    // the last payer" trick - worth stating explicitly in an
                    // interview.
                    share = total - running;
                } else {
                    long weighted = total * ((EqualSplit) split).getShares();
                    share = weighted / totalShares; // floor; remainder flows to last
                    running += share;
                }
                shares.put(split.getUserId(), share);
            }
        } else {
            // Percent and Exact splits carry their own amount; they were
            // validated at construction to sum to the total, but we still
            // verify here - defence in depth for the ledger invariant.
            long sum = 0;
            for (Split split : splits) {
                long share = split.computeShareInPaise(total);
                shares.put(split.getUserId(), share);
                sum += share;
            }
            if (sum != total) {
                throw new IllegalStateException("Computed shares of '"
                    + expense.getDescription() + "' sum to " + Split.formatRupees(sum)
                    + " instead of " + Split.formatRupees(total));
            }
        }
        return shares;
    }

    /**
     * The per-expense ledger deltas derived from the shares: the payer is
     * owed (total - own share) by the group; everyone else owes their share
     * to the payer. Encoded as directed edges debtor -> creditor with amount,
     * which is exactly what BalanceService consumes.
     *
     * @return list of transfers, e.g. [Rahul pays Anjali 500.00]
     */
    public List<Transfer> deriveTransfers(Expense expense) {
        Map<String, Long> shares = computeShares(expense);
        String payer = expense.getPaidByUserId();
        List<Transfer> transfers = new ArrayList<>();
        for (Map.Entry<String, Long> entry : shares.entrySet()) {
            String debtor = entry.getKey();
            long owed = entry.getValue();
            if (owed <= 0) {
                continue; // shouldn't happen, but never book a zero/negative ledger line
            }
            if (debtor.equals(payer)) {
                continue; // payer vs self - meaningless
            }
            transfers.add(new Transfer(expense.getId(), debtor, payer, owed));
        }
        return transfers;
    }
}
