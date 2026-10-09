/**
 * Decorator around any Dice that never shows a 6 (the classic interview twist).
 *
 * It implements Dice by *holding* a Dice: on roll() it asks the wrapped die
 * and re-rolls while the result is 6. Game never learns about crookedness —
 * it keeps calling roll() — so this is the problem's OCP showcase.
 */
public class CrookedDiceDecorator implements Dice {

    private final Dice wrapped;
    private final int suppressedFace;

    public CrookedDiceDecorator(Dice wrapped) {
        this(wrapped, 6);
    }

    public CrookedDiceDecorator(Dice wrapped, int suppressedFace) {
        if (wrapped == null) {
            throw new IllegalArgumentException("Wrapped dice cannot be null");
        }
        this.wrapped = wrapped;
        this.suppressedFace = suppressedFace;
    }

    @Override
    public int roll() {
        int result = wrapped.roll();
        while (result == suppressedFace) {
            result = wrapped.roll();
        }
        return result;
    }

    @Override
    public String describe() {
        return "crooked die (never rolls " + suppressedFace + "), wrapping a " + wrapped.describe();
    }
}
