import java.util.Random;

/**
 * A fair six-sided die. The Random is injected (not created internally)
 * so a seeded dice makes games fully deterministic and reproducible.
 */
public class StandardDice implements Dice {

    public static final int DEFAULT_FACES = 6;

    private final Random random;
    private final int faces;

    public StandardDice() {
        this(new Random());
    }

    /** Deterministic die: same seed replays the same sequence forever. */
    public StandardDice(long seed) {
        this(new Random(seed));
    }

    public StandardDice(Random random) {
        this(random, DEFAULT_FACES);
    }

    public StandardDice(Random random, int faces) {
        if (random == null) {
            throw new IllegalArgumentException("Random source cannot be null");
        }
        if (faces < 1) {
            throw new IllegalArgumentException("A die needs at least 1 face, got " + faces);
        }
        this.random = random;
        this.faces = faces;
    }

    @Override
    public int roll() {
        // Random.nextInt(bound) returns [0, bound): +1 shifts to [1, faces].
        return random.nextInt(faces) + 1;
    }

    @Override
    public String describe() {
        return "standard " + faces + "-sided die";
    }
}
