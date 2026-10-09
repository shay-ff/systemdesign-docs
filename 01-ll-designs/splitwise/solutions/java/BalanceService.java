import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The ledger: net pairwise balances per group.
 *
 * DATA MODEL (read this twice - the orientation is the crux of the class):
 *   balances.get(groupId) is a TreeMap<"aId|bId", amount> where aId < bId
 *   (lexicographic, so a pair has exactly ONE key).
 *   amount is SIGNED: +amount means aId owes bId; -amount means bId owes aId.
 *   amount == 0 means the pair is settled and the entry is removed.
 *
 * Why signed? Because debts are DIRRECTED (A owes B is not B owes A) but
 * they fold against each other: "A owes B 500" + "B owes A 200" = "A owes B
 * 300". A single signed entry per unordered pair captures both directions
 * and does the folding automatically - we only have to add the signed
 * quantity on the pair's canonical key.
 *
 * Concurrency - writes funnel through one ReentrantLock. Why not just
 * ConcurrentHashMap? Because booking one expense is a read-modify-write
 * across several pair keys; CHM only makes individual ops atomic, not
 * multi-key sequences. A coarse lock is a deliberate, interview-worthy
 * simplification: obviously correct; per-group lock striping is the stated
 * next step (say that out loud).
 */
public class BalanceService {
    private final Map<String, TreeMap<String, Long>> groupToPairBalances
        = new java.util.concurrent.ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    // ---------------------------------------------------------------- writes

    /**
     * Records one directed flow: fromUser owes toUser the given amount.
     * Immediately folds against whatever the pair already holds.
     */
    public void applyTransfer(String groupId, String fromUserId, String toUserId,
                              long amountInPaise) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group id cannot be null or empty");
        }
        if (fromUserId == null || toUserId == null) {
            throw new IllegalArgumentException("Transfer endpoints cannot be null");
        }
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException(
                "Cannot book a transfer from a user to themselves: " + fromUserId);
        }
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive, got "
                + Split.formatRupees(amountInPaise));
        }
        String pairKey = pairKey(fromUserId, toUserId);
        long signedDelta = signedAmount(fromUserId, toUserId, amountInPaise);

        lock.lock();
        try {
            TreeMap<String, Long> pairs = groupToPairBalances.get(groupId);
            if (pairs == null) {
                pairs = new TreeMap<>();
                groupToPairBalances.put(groupId, pairs);
            }
            Long existing = pairs.get(pairKey);
            long updated = (existing == null ? 0L : existing.longValue()) + signedDelta;
            if (updated == 0) {
                pairs.remove(pairKey); // pair fully settled - keep the map sparse
            } else {
                pairs.put(pairKey, updated);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Records all directed flows derived from one expense. */
    public void applyTransfers(String groupId, List<Transfer> transfers) {
        if (transfers == null) {
            throw new IllegalArgumentException("Transfers cannot be null");
        }
        for (Transfer transfer : transfers) {
            applyTransfer(groupId, transfer.getFromUserId(), transfer.getToUserId(),
                transfer.getAmountInPaise());
        }
    }

    /**
     * Records an external payment: fromUser actually paid toUser money,
     * extinguishing debt. A payment is an asset transfer in the OPPOSITE
     * bookkeeping direction of a debt, so we credit it as
     * "toUser owes fromUser amount" - the sign flip is what makes the
     * balances SHRINK when people pay each other.
     *
     * Partial and over- payments are fine: the ledger just moves (possibly
     * flipping the pair's direction), exactly like real bookkeeping.
     */
    public void recordSettlement(String groupId, String fromUserId, String toUserId,
                                 long amountInPaise) {
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive, got "
                + Split.formatRupees(amountInPaise));
        }
        // fromUser pays toUser  <->  toUser now 'owes' fromUser the asset.
        applyTransfer(groupId, toUserId, fromUserId, amountInPaise);
    }

    // ----------------------------------------------------------------- reads

    /**
     * Net balance per user for one group: positive = user is owed money,
     * negative = user owes. Empty map = group fully settled.
     *
     * Users whose contributions cancel out are REMOVED, not kept as 0-entry
     * noise - the "empty map means settled" contract above must hold exactly.
     */
    public Map<String, Long> getNetBalances(String groupId) {
        Map<String, Long> net = new TreeMap<>();
        List<Transfer> pairs = getPairwiseBalances(groupId);
        for (Transfer pair : pairs) {
            // pair is canonical: debtor -> creditor
            net.merge(pair.getFromUserId(), -pair.getAmountInPaise(), Long::sum);
            net.merge(pair.getToUserId(), pair.getAmountInPaise(), Long::sum);
        }
        net.values().removeIf(v -> v.longValue() == 0L);
        return net;
    }

    /**
     * Net pairwise balances for one group, in canonical debtor->creditor
     * form with strictly positive amounts. This is the "who owes whom"
     * view BEFORE simplification - the input the greedy algorithm improves on.
     */
    public List<Transfer> getPairwiseBalances(String groupId) {
        List<Transfer> result = new ArrayList<>();
        TreeMap<String, Long> pairs = groupToPairBalances.get(groupId);
        if (pairs == null) {
            return result;
        }
        lock.lock();
        try {
            for (Map.Entry<String, Long> entry : pairs.entrySet()) {
                String[] users = splitKey(entry.getKey());
                long stored = entry.getValue().longValue();
                // +stored: users[0] owes users[1]; -stored: users[1] owes users[0]
                if (stored > 0) {
                    result.add(new Transfer(null, users[0], users[1], stored));
                } else if (stored < 0) {
                    result.add(new Transfer(null, users[1], users[0], -stored));
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /** True if the user still owes or is owed anything in the group. */
    public boolean userHasOpenBalanceInGroup(String groupId, String userId) {
        for (Transfer pair : getPairwiseBalances(groupId)) {
            if (pair.getFromUserId().equals(userId) || pair.getToUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Settle-up sweep for a fully square group.
     *
     * Motivation (an edge case worth understanding): users can pay along
     * DIFFERENT routes than the raw pairwise debts (e.g. they follow the
     * simplified settlement suggestions). Each payment correctly shrinks or
     * flips its pair, but when payment routes cross the original debt routes,
     * offsetting cycles survive: every net is zero, yet pairs remain open.
     * Economically the group is settled; bookkeeping-wise entries linger.
     *
     * Rule: if every net is zero the debts are unenforceable noise - clearing
     * the group's pairwise map is a no-op financially. This method REQUIRES
     * that precondition (it refuses to clear real debt; use recordSettlement
     * first) and then drops the residue.
     */
    public void settleUpGroup(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group id cannot be null or empty");
        }
        lock.lock();
        try {
            TreeMap<String, Long> pairs = groupToPairBalances.get(groupId);
            if (pairs == null || pairs.isEmpty()) {
                return; // already clean
            }
            Map<String, Long> net = getNetBalances(groupId);
            if (!net.isEmpty()) {
                throw new IllegalStateException("Group '" + groupId
                    + "' is not fully settled: " + net.size() + " user(s) still carry net"
                    + " balances. Record settlements first.");
            }
            pairs.clear();
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------- internals

    /**
     * Canonical key: lower user id first, so {A,B} has exactly one key no
     * matter which direction the original transfer flowed.
     */
    private String pairKey(String fromUserId, String toUserId) {
        return fromUserId.compareTo(toUserId) < 0
            ? fromUserId + "|" + toUserId
            : toUserId + "|" + fromUserId;
    }

    /**
     * Express "fromUser owes toUser amount" as a signed quantity on the
     * canonical key: positive if the canonical-first user is the debtor.
     */
    private long signedAmount(String fromUserId, String toUserId, long amountInPaise) {
        return fromUserId.compareTo(toUserId) < 0 ? amountInPaise : -amountInPaise;
    }

    private String[] splitKey(String key) {
        int idx = key.indexOf('|');
        return new String[] {key.substring(0, idx), key.substring(idx + 1)};
    }
}
