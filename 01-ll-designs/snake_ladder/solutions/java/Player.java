/**
 * A player token on the board. Deliberately thin: name plus position.
 * The Game orchestrator is the only class that knows how to move players.
 */
public class Player {

    private final String name;
    private int position;

    public Player(String name) {
        this(name, 1);
    }

    public Player(String name, int startPosition) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }
        if (startPosition < 1) {
            throw new IllegalArgumentException("Start position must be at least 1, got " + startPosition);
        }
        this.name = name.trim();
        this.position = startPosition;
    }

    public void moveTo(int position) {
        if (position < 1) {
            throw new IllegalArgumentException("Player position must be at least 1, got " + position);
        }
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return name + " (at " + position + ")";
    }
}
