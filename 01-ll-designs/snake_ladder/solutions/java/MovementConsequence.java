/**
 * One snake or one ladder on the board.
 *
 * Deliberately a single class rather than Snake/Ladder subclasses: both pieces
 * behave identically (carry the player from start to end); they differ only in
 * data (direction) and presentation. See explanation.md for the full argument.
 */
public class MovementConsequence {

    private final int start;
    private final int end;
    private final Direction direction;

    public MovementConsequence(int start, int end, Direction direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Direction cannot be null for a movement consequence");
        }
        if (start == end) {
            throw new IllegalArgumentException(
                    "Consequence start and end must differ (both were " + start + ")");
        }
        if (direction == Direction.SNAKE && end >= start) {
            throw new IllegalArgumentException(
                    "Snake at " + start + " must descend, but its tail (" + end + ") is not below its head");
        }
        if (direction == Direction.LADDER && end <= start) {
            throw new IllegalArgumentException(
                    "Ladder at " + start + " must ascend, but its top (" + end + ") is not above its foot");
        }
        this.start = start;
        this.end = end;
        this.direction = direction;
    }

    /**
     * Where does a player landing on this consequence's start cell end up?
     * @param from the cell the player just landed on
     * @return the destination cell
     */
    public int jump(int from) {
        if (from != start) {
            throw new IllegalArgumentException(
                    "Cannot jump from " + from + ": consequence starts at " + start);
        }
        return end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public Direction getDirection() {
        return direction;
    }

    /**
     * Human-readable description used in board printouts and game logs.
     */
    public String describe() {
        if (direction == Direction.SNAKE) {
            return "Snake " + start + " -> " + end + " (slides down)";
        }
        return "Ladder " + start + " -> " + end + " (climbs up)";
    }

    @Override
    public String toString() {
        return describe();
    }
}
