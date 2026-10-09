/**
 * Notes the machine accepts (Indian denominations).
 *
 * Kept SEPARATE from Coin (a shared "Currency" enum would force every
 * monetary int to share one type — a note and a coin are physically different
 * objects: the note acceptor has its own hardware path, its own inventory,
 * and notes are never dispensed as change in this machine). The split mirrors
 * the hardware: coin slot vs note acceptor are different devices, so the
 * domain types are different too.
 */
public enum Note {
    TEN(10),
    TWENTY(20),
    FIFTY(50),
    HUNDRED(100),
    TWO_HUNDRED(200);

    private final int value;

    Note(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
