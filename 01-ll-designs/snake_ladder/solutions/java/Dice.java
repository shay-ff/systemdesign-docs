/**
 * A die of any behaviour. One-method interface so new dice behaviours
 * (sum of two dice, crooked variants, weighted dice) drop in without
 * the Game ever changing.
 */
public interface Dice {

    /** @return the face shown by this roll, in [1, faces] */
    int roll();

    /** @return short human-readable description for game logs */
    String describe();
}
