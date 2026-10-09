/**
 * Movement direction of a car. Also used to classify passenger intent:
 * a hall call from floor 4 with direction UP means "someone on floor 4
 * wants to go up".
 *
 * NOTE (interview detail): the enum constant order matters here. We rely on
 * Direction.UP.compareTo(...) only for intent classification, but the LOOK
 * scheduler's "continue sweep" check asks `car.direction == Direction.UP`
 * explicitly. Keeping IDLE out of this enum avoids the classic bug where an
 * idle car is treated as "moving up" by comparison logic.
 */
public enum Direction {
    UP,
    DOWN;

    /** True when this direction is a moving direction (i.e. not NONE). */
    public boolean isMoving() {
        return this == UP || this == DOWN;
    }

    /** The direction a car at `from` must travel to service `to`. */
    public static Direction between(int from, int to) {
        if (to > from) {
            return UP;
        }
        if (to < from) {
            return DOWN;
        }
        throw new IllegalArgumentException(
                "No direction between identical floors: from=" + from + ", to=" + to);
    }
}
