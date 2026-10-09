import java.util.Collections;
import java.util.Map;

/**
 * An immutable, pre-computed change plan (a RESERVATION over change reserves).
 *
 * Plan-then-commit: `CashInventory.planChange(amount)` returns this object
 * WITHOUT mutating anything; `dispenseChange(plan)` later commits it. The
 * gap between planning and committing is where the state machine asks the
 * user anything else it needs to (nothing in this build — but in a real
 * machine, e.g. exact-change-only warnings and receipts happen here).
 *
 * The plan records exactly which denominations and counts will leave the
 * reserves, so a receipt can print the change composition without re-deriving
 * it (and without another greedy walk that might see DIFFERENT reserves if
 * anything intervened).
 */
public final class ChangePlan {

    private final Map<Note, Integer> notes;
    private final Map<Coin, Integer> coins;

    private ChangePlan(Map<Note, Integer> notes, Map<Coin, Integer> coins) {
        this.notes = Collections.unmodifiableMap(notes);
        this.coins = Collections.unmodifiableMap(coins);
    }

    /** The trivial zero-change plan (exact payment). */
    public static ChangePlan exact() {
        return new ChangePlan(Map.of(), Map.of());
    }

    public static ChangePlan of(Map<Note, Integer> notes, Map<Coin, Integer> coins) {
        return new ChangePlan(notes, coins);
    }

    public Map<Note, Integer> getNotes() {
        return notes;
    }

    public Map<Coin, Integer> getCoins() {
        return coins;
    }

    public int totalRupees() {
        int total = 0;
        for (Map.Entry<Note, Integer> entry : notes.entrySet()) {
            total += entry.getKey().getValue() * entry.getValue();
        }
        for (Map.Entry<Coin, Integer> entry : coins.entrySet()) {
            total += entry.getKey().getValue() * entry.getValue();
        }
        return total;
    }

    public boolean isExact() {
        return notes.isEmpty() && coins.isEmpty();
    }

    @Override
    public String toString() {
        if (isExact()) {
            return "no change (exact payment)";
        }
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Map.Entry<Note, Integer> entry : notes.entrySet()) {
            sb.append(any ? " + " : "")
              .append(entry.getValue()).append("x")
              .append(entry.getKey().getValue()).append(" note");
            if (entry.getValue() > 1) {
                sb.append("s");
            }
            any = true;
        }
        for (Map.Entry<Coin, Integer> entry : coins.entrySet()) {
            sb.append(any ? " + " : "")
              .append(entry.getValue()).append("x")
              .append(entry.getKey().getValue()).append(" coin");
            if (entry.getValue() > 1) {
                sb.append("s");
            }
            any = true;
        }
        return sb.toString();
    }
}
