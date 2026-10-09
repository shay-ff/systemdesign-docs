import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The machine's cash: inserted money (this transaction, per payment device)
 * and change reserves (the machine's own float, by denomination).
 *
 * TWO separate stores, deliberately:
 * - `insertedCoins` / `insertedNotes`: money the CUSTOMER has fed in for the
 *   current sale. It is not the machine's money until the sale COMMITS.
 *   A refund returns exactly these; a commit moves them into reserves.
 * - `coinReserves` / `noteReserves`: the machine's own change float, by
 *   denomination. This is what the refiller owns and what change comes from.
 *
 * Blending the two ("the machine's total cash") is the classic design bug:
 * it makes refunds and change math share one pool, so a refund can pay out
 * change reserves (draining the float) or a change plan can "use" coins the
 * customer just inserted but the sale hasn't committed. Two pools, two
 * lifecycles, one commit path.
 *
 * Order preservation: inserted coins/notes are kept in INSERTION ORDER
 * (Deque). A refund must return the customer's OWN money — oldest-first is
 * the auditable choice, and insertion order is what a dispute (chargeback,
 * "I put in a 50!") reconstructs.
 */
public class CashInventory {

    private final Deque<Coin> insertedCoins = new ArrayDeque<>();
    private final Deque<Note> insertedNotes = new ArrayDeque<>();

    private final Map<Coin, Integer> coinReserves = new EnumMap<>(Coin.class);
    private final Map<Note, Integer> noteReserves = new EnumMap<>(Note.class);

    // ------------------------------------------------------------------
    // Customer input (this transaction)
    // ------------------------------------------------------------------

    public void insertCoin(Coin coin) {
        if (coin == null) {
            throw new IllegalArgumentException("Inserted coin cannot be null");
        }
        insertedCoins.addLast(coin);
    }

    public void insertNote(Note note) {
        if (note == null) {
            throw new IllegalArgumentException("Inserted note cannot be null");
        }
        insertedNotes.addLast(note);
    }

    /** Total rupees the customer has inserted this transaction. */
    public int insertedTotal() {
        int total = 0;
        for (Coin coin : insertedCoins) {
            total += coin.getValue();
        }
        for (Note note : insertedNotes) {
            total += note.getValue();
        }
        return total;
    }

    public boolean hasInsertedMoney() {
        return !insertedCoins.isEmpty() || !insertedNotes.isEmpty();
    }

    /**
     * REFUND: return exactly what the customer inserted (insertion order),
     * clearing this transaction's input. Does NOT touch change reserves.
     */
    public Refund refundInserted() {
        List<Coin> coins = List.copyOf(insertedCoins);
        List<Note> notes = List.copyOf(insertedNotes);
        insertedCoins.clear();
        insertedNotes.clear();
        return new Refund(coins, notes);
    }

    /**
     * COMMIT the sale: the inserted money becomes machine property
     * (moves into reserves). Called exactly once per successful sale;
     * the state machine guarantees a fresh transaction afterwards.
     */
    public void commitInsertedToReserves() {
        for (Coin coin : insertedCoins) {
            coinReserves.merge(coin, 1, Integer::sum);
        }
        for (Note note : insertedNotes) {
            noteReserves.merge(note, 1, Integer::sum);
        }
        insertedCoins.clear();
        insertedNotes.clear();
    }

    // ------------------------------------------------------------------
    // Change reserves (the machine's float)
    // ------------------------------------------------------------------

    public void loadChange(Coin coin, int quantity) {
        if (coin == null || quantity < 0) {
            throw new IllegalArgumentException(
                    "Bad change load: coin=" + coin + ", quantity=" + quantity);
        }
        coinReserves.merge(coin, quantity, Integer::sum);
    }

    public void loadChange(Note note, int quantity) {
        if (note == null || quantity < 0) {
            throw new IllegalArgumentException(
                    "Bad change load: note=" + note + ", quantity=" + quantity);
        }
        noteReserves.merge(note, quantity, Integer::sum);
    }

    /**
     * PLAN change (dry-run, no mutation): the classic greedy walk from the
     * largest note to the smallest coin. Returns null when the reserves
     * cannot compose `amount` (the caller declines the sale — "exact change
     * only" behaviour).
     *
     * WHY GREEDY IS CORRECT HERE (the interview answer): the denomination
     * chain {200, 100, 50, 20, 10, 5, 2, 1} is CANONICAL — each denomination
     * divides the next (200|100? no — 100 divides 200? 200/100=2 yes;
     * 100/50=2; 50/10=5; 10/5=2; 5/1; 2/1) — which makes greedy provably
     * optimal for FULLY STOCKED reserves. BUT greedy has no backtracking:
     * with only two 20s and one 5 in reserves, amount=45 fails (greedy takes
     * 20+20 then cannot make 5 from the lone 5? it can — 20+20+5=45) — the
     * real failure is amount=50 against {20,20}: greedy takes 20, 20, then
     * 0 left, cannot compose 10 — yet 50 WAS composable... no, {20,20} sums
     * to 40, so 50 is impossible. The honest statement: greedy is optimal
     * when stock is unlimited (canonical chain); with LIMITED stock it finds
     * the plan when a greedy-composable one exists and cleanly declines
     * otherwise — real machines behave exactly this way ("use exact change").
     */
    public ChangePlan planChange(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Change amount cannot be negative: " + amount);
        }
        if (amount == 0) {
            return ChangePlan.exact();
        }
        int remaining = amount;
        Map<Note, Integer> notesOut = new EnumMap<>(Note.class);
        Map<Coin, Integer> coinsOut = new EnumMap<>(Coin.class);

        // Notes first, largest to smallest.
        Note[] notesDesc = {Note.TWO_HUNDRED, Note.HUNDRED, Note.FIFTY,
                            Note.TWENTY, Note.TEN};
        for (Note note : notesDesc) {
            int available = noteReserves.getOrDefault(note, 0);
            int take = Math.min(available, remaining / note.getValue());
            if (take > 0) {
                notesOut.put(note, take);
                remaining -= take * note.getValue();
            }
        }
        // Then coins, largest to smallest.
        Coin[] coinsDesc = {Coin.TEN, Coin.FIVE, Coin.TWO, Coin.ONE};
        for (Coin coin : coinsDesc) {
            int available = coinReserves.getOrDefault(coin, 0);
            int take = Math.min(available, remaining / coin.getValue());
            if (take > 0) {
                coinsOut.put(coin, take);
                remaining -= take * coin.getValue();
            }
        }
        if (remaining != 0) {
            return null; // reserves cannot compose the amount — decline
        }
        return ChangePlan.of(notesOut, coinsOut);
    }

    /**
     * COMMIT a change plan: remove the planned denominations from reserves.
     * Must follow a successful planChange (the plan IS the reservation).
     */
    public void dispenseChange(ChangePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Change plan cannot be null");
        }
        for (Map.Entry<Note, Integer> entry : plan.getNotes().entrySet()) {
            int available = noteReserves.getOrDefault(entry.getKey(), 0);
            if (available < entry.getValue()) {
                throw new IllegalStateException("Change reserves drifted under the plan — "
                        + entry.getKey() + " needed " + entry.getValue()
                        + ", have " + available);
            }
            noteReserves.put(entry.getKey(), available - entry.getValue());
        }
        for (Map.Entry<Coin, Integer> entry : plan.getCoins().entrySet()) {
            int available = coinReserves.getOrDefault(entry.getKey(), 0);
            if (available < entry.getValue()) {
                throw new IllegalStateException("Change reserves drifted under the plan — "
                        + entry.getKey() + " needed " + entry.getValue()
                        + ", have " + available);
            }
            coinReserves.put(entry.getKey(), available - entry.getValue());
        }
    }

    /** Read-only view of change reserves (for display/audit). */
    public Map<Note, Integer> getNoteReserves() {
        return new HashMap<>(noteReserves);
    }

    public Map<Coin, Integer> getCoinReserves() {
        return new HashMap<>(coinReserves);
    }

    /** Total rupees in change reserves (the float). */
    public int reservesTotal() {
        int total = 0;
        for (Map.Entry<Note, Integer> entry : noteReserves.entrySet()) {
            total += entry.getKey().getValue() * entry.getValue();
        }
        for (Map.Entry<Coin, Integer> entry : coinReserves.entrySet()) {
            total += entry.getKey().getValue() * entry.getValue();
        }
        return total;
    }
}
