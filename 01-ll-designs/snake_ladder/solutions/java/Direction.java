/**
 * Direction of a movement consequence on the board.
 * Modelled as an enum (not a boolean) so validation and output read cleanly:
 * "direction == SNAKE" beats "!isLadder".
 */
public enum Direction {
    SNAKE,
    LADDER
}
