import java.util.List;

/**
 * Immutable record of a REFUND: exactly what the machine returns to the
 * customer (their own inserted money, in insertion order).
 *
 * WHY A CLASS: a refund is one event carrying two lists. Printing it as a
 * receipt line ("REFUND: 1x50 note, 1x10 note") is a presentation concern
 * that belongs to the record, not to the cash inventory (which should not
 * know about receipts) nor to the state machine (which should not format).
 */
public final class Refund {

    private final List<Coin> coins;
    private final List<Note> notes;

    public Refund(List<Coin> coins, List<Note> notes) {
        this.coins = List.copyOf(coins);
        this.notes = List.copyOf(notes);
    }

    public List<Coin> getCoins() {
        return coins;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public int totalRupees() {
        int total = 0;
        for (Coin coin : coins) {
            total += coin.getValue();
        }
        for (Note note : notes) {
            total += note.getValue();
        }
        return total;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Note note : notes) {
            sb.append(any ? ", " : "").append(note.getValue()).append("-rupee note");
            any = true;
        }
        for (Coin coin : coins) {
            sb.append(any ? ", " : "").append(coin.getValue()).append("-rupee coin");
            any = true;
        }
        if (!any) {
            return "nothing (no money was inserted)";
        }
        return sb.toString();
    }
}
