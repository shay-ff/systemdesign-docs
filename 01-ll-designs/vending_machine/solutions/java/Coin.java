/**
 * Physical coins the machine accepts (Indian denominations).
 *
 * WHY AN ENUM and not int amounts floating around: every layer of this
 * machine deals in physical objects — the coin slot accepts them, the
 * inventory stores them, change is dispensed as them. An int like "5" could
 * be a coin, a note, a price or a sum; a Coin.FIVE can only be a coin.
 * Type safety documents intent and makes the "which physical objects exist"
 * question answerable by listing constants — the first thing an interviewer
 * asks: "what currency? what denominations?"
 */
public enum Coin {
    ONE(1),
    TWO(2),
    FIVE(5),
    TEN(10);

    private final int value;

    Coin(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
