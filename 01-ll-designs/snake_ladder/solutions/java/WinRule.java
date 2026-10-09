/**
 * Strategy for how a roll translates into a destination near the finish.
 *
 * House rules differ: exact-roll-to-finish (overshoot = no move) versus
 * bounce-back off the finish line. The rule lives outside Game so a new
 * house rule is a new class, never an edit to the engine.
 */
public interface WinRule {

    /**
     * @param current the player's current position
     * @param roll    the face just rolled
     * @param finish  the winning cell (dimension squared)
     * @return the position the player should occupy after this move
     */
    int destination(int current, int roll, int finish);

    /** @return short human-readable description for game logs */
    String describe();
}
