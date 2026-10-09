import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * THE star feature: debt simplification.
 *
 * Given pairwise debts ("A owes B 500, B owes C 400"), the raw view has more
 * transactions than necessary. Since debt is transferable within a group,
 * the SAME net positions can be settled with fewer payments ("A pays C
 * 100, A pays B 400" or similar). This service computes that minimal
 * transaction list.
 *
 * Algorithm - greedy min-cash-flow:
 *   1. Collapse everything to one net number per user
 *      (positive = is owed, negative = owes).
 *   2. Split users into a max-heap of creditors and a min-heap of debtors.
 *   3. Repeatedly match the largest creditor against the largest debtor;
 *      transfer min(their amounts), fold the remainder back into its heap.
 *   4. Stop when both heaps are empty; the transfer list is the answer.
 *
 * Guarantees:
 *   - Net positions are preserved exactly (invariant: sum of nets == 0),
 *     so after executing these transfers the group is fully settled.
 *   - Transaction count <= n-1 where n = number of users with non-zero net
 *     (a matching/flow argument), which is the practical "optimal" people
 *     care about; note it is not always the theoretical minimum *number of
 *     transfers* (that is NP-hard subset-sum style), and saying so in an
 *     interview earns points.
 *
 * Complexity: O(n log n) for the heap loop - n users, each settles at least
 * one user fully per iteration.
 */
public class SimplifyDebtService {

    /**
     * @param netBalances userId -> net amount in paise (positive = owed to
     *                    user, negative = user owes). Sum must be 0.
     * @return minimal list of transfers that settles all debts
     */
    public List<Transfer> simplify(Map<String, Long> netBalances) {
        if (netBalances == null) {
            throw new IllegalArgumentException("Net balances map cannot be null");
        }
        // Validate the invariant up front; a non-zero sum means the caller's
        // ledger is corrupt and we would silently invent or destroy money.
        long total = 0;
        for (Long net : netBalances.values()) {
            if (net == null) {
                throw new IllegalArgumentException("Net balance for a user is null");
            }
            total += net.longValue();
        }
        if (total != 0) {
            throw new IllegalStateException("Net balances must sum to 0 (conservation of"
                + " money), but sum to " + Split.formatRupees(Math.abs(total))
                + (total > 0 ? " (too much credit)" : " (too much debt)"));
        }

        // Max-heap of creditors: largest positive net first.
        PriorityQueue<UserNet> creditors = new PriorityQueue<>(
            (x, y) -> Long.compare(y.net, x.net));
        // Min-heap of debtors: most negative (owes most) first.
        PriorityQueue<UserNet> debtors = new PriorityQueue<>(
            (x, y) -> Long.compare(x.net, y.net));

        for (Map.Entry<String, Long> entry : netBalances.entrySet()) {
            long net = entry.getValue().longValue();
            if (net > 0) {
                creditors.add(new UserNet(entry.getKey(), net));
            } else if (net < 0) {
                debtors.add(new UserNet(entry.getKey(), net));
            }
            // net == 0 -> settled user, drops out naturally
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            UserNet creditor = creditors.poll();
            UserNet debtor = debtors.poll();
            long amount = Math.min(creditor.net, -debtor.net);
            if (amount <= 0) {
                throw new IllegalStateException(
                    "Simplification produced a non-positive transfer; invariant broken");
            }
            transfers.add(new Transfer(null, debtor.userId, creditor.userId, amount));
            creditor.net -= amount;
            debtor.net += amount;
            if (creditor.net > 0) {
                creditors.add(creditor);
            }
            if (debtor.net < 0) {
                debtors.add(debtor);
            }
        }
        // If either heap is non-empty here, the sum-zero invariant was
        // violated - but we validated it above, so both must be empty.
        return transfers;
    }

    /** Tiny internal pair: a user and their remaining net amount. */
    private static final class UserNet {
        private final String userId;
        private long net;

        private UserNet(String userId, long net) {
            this.userId = userId;
            this.net = net;
        }
    }
}
