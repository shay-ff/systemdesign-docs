import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cash box: denomination inventory + greedy dispensing.
 *
 * Indian ATM flavour: 2000 / 500 / 200 / 100 cassettes (post-2016
 * demonetisation-era machine configuration; older machines had 1000s).
 * The inventory is a Map<Integer, Integer> of denomination -> note count.
 *
 * THE GREEDY ALGORITHM - and the classic trap with it:
 *   Walk denominations largest-first, take as many of each as fit, move on.
 *   Greedy is NOT always optimal for arbitrary denomination sets (e.g., with
 *   1/3/4 it misses 6 = 3+3 and gives 4+1+1), but for the standard Indian
 *   chain {2000, 500, 200, 100}, where each denomination divides the next,
 *   greedy IS optimal for the amount itself. The interesting failure is
 *   AVAILABILITY: greedy commits notes early and only DISCOVERS at the end
 *   that it cannot compose the amount (say, only 2000s left and you asked
 *   for 300). It has no backtracking.
 *
 * WE DO IT PROPERLY (and this is the interview differentiator): a dry-run
 * pass that only READS the inventory composes the plan; only if the plan
 * fully covers the amount do we commit it as a second pass. That makes
 * dispensing effectively atomic: a partial compose can never eat notes
 * (the naive greedy-with-mutation leaves the machine with, say, 700 removed
 * and nothing dispensed). Two-phase plan-then-commit is a one-paragraph
 * talking point that shows you think about failure atomicity.
 *
 * CONCURRENCY note: two ATMs obviously do not share one physical cash box,
 * but one dispenser serves the machine's single exit shutter, so the
 * plan-then-commit pair must not interleave. synchronized methods make each
 * dispense (and each restock) atomic. In a real machine this is trivially
 * true (one note path); in software it matters the moment a service layer
 * fronts multiple cash sources.
 */
public class CashDispenser {

    /** Ordered high -> low. LinkedHashMap preserves insertion order = greedy walk order. */
    private final Map<Integer, Integer> noteInventory = new LinkedHashMap<>();

    public CashDispenser() {
        // Seeded empty except the starter kit; the demo (and restock) fill it.
    }

    /** Stocks (or adds) notes of one denomination. */
    public synchronized void stock(int denomination, int count) {
        validateDenomination(denomination);
        if (count <= 0) {
            throw new IllegalArgumentException("Note count must be positive, got " + count);
        }
        noteInventory.merge(denomination, count, Integer::sum);
    }

    /** Removes notes of one denomination (maintenance). */
    public synchronized void removeNotes(int denomination, int count) {
        validateDenomination(denomination);
        Integer held = noteInventory.get(denomination);
        if (held == null || held < count) {
            throw new IllegalStateException("Cannot remove " + count + " x INR "
                    + denomination + " notes: only " + (held == null ? 0 : held) + " held");
        }
        noteInventory.merge(denomination, -count, Integer::sum);
        if (noteInventory.get(denomination) == 0) {
            noteInventory.remove(denomination);
        }
    }

    /**
     * True if the current inventory can compose exactly this amount.
     * Pure read - no mutation; the demo calls this to show a clean decline
     * BEFORE the machine commits the account debit. (In the real flow the
     * account debit and the dispense are a distributed transaction; the
     * compensation is an auto-reversal - see explanation.md.)
     */
    public synchronized boolean canDispense(long amountInPaise) {
        return plan(amountInPaise) != null;
    }

    /**
     * Dispenses cash: plans greedily, and only if the plan composes the full
     * amount, commits it to the inventory. Returns the note plan; throws if
     * the amount cannot be composed (the account is NOT debited in that
     * case - the caller checks canDispense/plan BEFORE debiting).
     */
    public synchronized Map<Integer, Integer> dispense(long amountInPaise) {
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Dispense amount must be positive, got "
                    + Account.formatRupees(amountInPaise));
        }
        if (amountInPaise % 100 != 0) {
            // ATMs physically cannot pay a non-multiple of the smallest note.
            throw new IllegalStateException("Amount " + Account.formatRupees(amountInPaise)
                    + " is not a multiple of INR 100 (smallest cassette)");
        }
        Map<Integer, Integer> plan = plan(amountInPaise);
        if (plan == null) {
            throw new IllegalStateException("Cannot compose " + Account.formatRupees(amountInPaise)
                    + " from available denominations: " + inventorySummary()
                    + " (greedy has no backtracking - try a smaller/multiple amount)");
        }
        for (Map.Entry<Integer, Integer> entry : plan.entrySet()) {
            noteInventory.merge(entry.getKey(), -entry.getValue(), Integer::sum);
        }
        return plan;
    }

    // -------------------------------------------------------------- internals

    /**
     * The greedy planner (dry run). Returns denomination -> count, or null
     * when greedy cannot exactly compose the amount.
     *
     * Amounts are in PAISE (INR 3700.00 = 370000 paise) while cassettes
     * count WHOLE NOTES - so the amount is converted to note-rupees FIRST
     * (one paise-fine amount would never match a whole number of notes).
     */
    private Map<Integer, Integer> plan(long amountInPaise) {
        if (amountInPaise % 100 != 0) {
            return null; // not composable from whole notes - caller declines
        }
        long remaining = amountInPaise / 100;
        Map<Integer, Integer> plan = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : noteInventory.entrySet()) {
            int denomination = entry.getKey();
            int held = entry.getValue();
            int take = (int) Math.min(remaining / denomination, held);
            if (take > 0) {
                plan.put(denomination, take);
                remaining -= (long) take * denomination; // note-rupee units
            }
            if (remaining == 0) {
                break;
            }
        }
        return remaining == 0 ? plan : null;
    }

    private void validateDenomination(int denomination) {
        if (denomination != 100 && denomination != 200
                && denomination != 500 && denomination != 2000) {
            throw new IllegalArgumentException("Unsupported denomination INR " + denomination
                    + " (supported: 2000, 500, 200, 100)");
        }
    }

    public synchronized String inventorySummary() {
        if (noteInventory.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        long total = 0;
        for (Map.Entry<Integer, Integer> entry : noteInventory.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(entry.getValue()).append(" x INR ").append(entry.getKey());
            total += (long) entry.getValue() * entry.getKey();
        }
        sb.append(" = ").append(Account.formatRupees(total * 100));
        return sb.toString();
    }

    public synchronized long totalCashInPaise() {
        long total = 0;
        for (Map.Entry<Integer, Integer> entry : noteInventory.entrySet()) {
            total += (long) entry.getValue() * entry.getKey();
        }
        return total * 100;
    }
}
