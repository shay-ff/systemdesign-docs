/**
 * Exact-roll-to-finish rule: you win only by landing exactly on the finish.
 * An overshooting roll results in NO MOVE — the player stays put.
 */
public class ExactRollWinRule implements WinRule {

    @Override
    public int destination(int current, int roll, int finish) {
        if (current < 1) {
            throw new IllegalArgumentException("Current position must be at least 1, got " + current);
        }
        if (roll < 1) {
            throw new IllegalArgumentException("Roll must be at least 1, got " + roll);
        }
        if (finish < 1) {
            throw new IllegalArgumentException("Finish position must be at least 1, got " + finish);
        }
        int candidate = current + roll;
        if (candidate == finish) {
            return finish; // exact landing wins
        }
        if (candidate > finish) {
            return current; // overshoot: no move under this rule
        }
        return candidate;
    }

    @Override
    public String describe() {
        return "exact roll to finish (overshoot = no move)";
    }
}
