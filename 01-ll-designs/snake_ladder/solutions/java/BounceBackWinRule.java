/**
 * Bounce-back rule: if a roll passes the finish, the excess is reflected
 * back off the finish line instead of being wasted.
 * e.g. standing at 23 with finish 25 and rolling 5 lands you on 25 - (28 - 25) = 22.
 */
public class BounceBackWinRule implements WinRule {

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
        if (candidate <= finish) {
            return candidate;
        }
        int excess = candidate - finish;
        int bounced = finish - excess;
        if (bounced < 1) {
            // A giant roll near the start could reflect below cell 1; clamp safely.
            bounced = 1;
        }
        return bounced;
    }

    @Override
    public String describe() {
        return "bounce back off the finish line";
    }
}
