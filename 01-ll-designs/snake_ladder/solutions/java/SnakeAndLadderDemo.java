import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Snake & Ladder demo.
 *
 * NOTE: the played game uses a compact 5x5 board (finish at 25) with custom
 * snakes/ladders so the game log stays tight and readable. The standard
 * 10x10 board is printed for reference first — everything is dimension-
 * agnostic by design.
 */
public class SnakeAndLadderDemo {

    public static void main(String[] args) {
        System.out.println("=== Snake & Ladder — Low Level Design Demo ===");
        System.out.println();

        section1StandardBoard();
        section2ValidationGuardrails();
        section3SeededGameToCompletion();
        section4ChainResolution();
        section5WinRuleStrategies();
        section6CrookedDice();

        System.out.println();
        System.out.println("=== Demo Complete ===");
    }

    // ------------------------------------------------------------------
    private static void section1StandardBoard() {
        System.out.println("=== Section 1: Standard 10x10 Board ===");
        Board standardBoard = new Board(10, Arrays.asList(
                new MovementConsequence(17, 4, Direction.SNAKE),
                new MovementConsequence(54, 34, Direction.SNAKE),
                new MovementConsequence(62, 18, Direction.SNAKE),
                new MovementConsequence(64, 60, Direction.SNAKE),
                new MovementConsequence(87, 24, Direction.SNAKE),
                new MovementConsequence(93, 73, Direction.SNAKE),
                new MovementConsequence(95, 75, Direction.SNAKE),
                new MovementConsequence(98, 79, Direction.SNAKE),
                new MovementConsequence(1, 38, Direction.LADDER),
                new MovementConsequence(4, 14, Direction.LADDER),
                new MovementConsequence(9, 31, Direction.LADDER),
                new MovementConsequence(21, 42, Direction.LADDER),
                new MovementConsequence(28, 84, Direction.LADDER),
                new MovementConsequence(51, 67, Direction.LADDER),
                new MovementConsequence(72, 91, Direction.LADDER),
                new MovementConsequence(80, 99, Direction.LADDER)
        ));
        System.out.println(standardBoard.prettyPrint());
        System.out.println("Snakes and ladders on this board:");
        for (MovementConsequence consequence : standardBoard.getConsequences()) {
            System.out.println("  " + consequence.describe());
        }
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section2ValidationGuardrails() {
        System.out.println("=== Section 2: Board Validation Guardrails (fail fast) ===");

        expectRejection("snake that climbs instead of descending",
                () -> new MovementConsequence(5, 20, Direction.SNAKE));

        // Bounds need the Board's context (a consequence alone does not know N^2):
        expectRejection("ladder whose top is off the 5x5 board",
                () -> new Board(5, Arrays.asList(
                        new MovementConsequence(3, 30, Direction.LADDER))));

        expectRejection("two consequences starting on the same cell",
                () -> new Board(5, Arrays.asList(
                        new MovementConsequence(7, 2, Direction.SNAKE),
                        new MovementConsequence(7, 20, Direction.LADDER))));

        // A genuine cycle: every piece is individually valid, but the chain
        // 10 -> 5 -> 12 -> 2 -> 10 never reaches a plain cell.
        expectRejection("cyclic chain (snake head -> ladder foot -> ... -> same snake head)",
                () -> new Board(5, Arrays.asList(
                        new MovementConsequence(10, 5, Direction.SNAKE),
                        new MovementConsequence(5, 12, Direction.LADDER),
                        new MovementConsequence(12, 2, Direction.SNAKE),
                        new MovementConsequence(2, 10, Direction.LADDER))));

        expectRejection("game with only one player",
                () -> new Game(new Board(5, new ArrayList<MovementConsequence>()),
                        Arrays.asList(new Player("Lonely")),
                        new StandardDice(42),
                        new ExactRollWinRule()));

        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section3SeededGameToCompletion() {
        System.out.println("=== Section 3: Seeded Game Played to Completion (5x5 board) ===");
        System.out.println("NOTE: compact 5x5 board (finish at 25) keeps this log tight;");
        System.out.println("the same classes run a 10x10 board unchanged (Section 1).");

        // 5x5 board: finish at 25.
        // Ladder 3 -> 22 whose top (22) is snake 22 -> 8: a chained jump.
        Board board = new Board(5, Arrays.asList(
                new MovementConsequence(22, 8, Direction.SNAKE),
                new MovementConsequence(14, 5, Direction.SNAKE),
                new MovementConsequence(3, 22, Direction.LADDER),
                new MovementConsequence(11, 19, Direction.LADDER)
        ));
        System.out.print(board.prettyPrint());
        System.out.println("Snakes and ladders:");
        for (MovementConsequence consequence : board.getConsequences()) {
            System.out.println("  " + consequence.describe());
        }
        System.out.println();

        List<Player> players = Arrays.asList(
                new Player("Asha"),
                new Player("Bhanu"),
                new Player("Chitra"));

        // Seeded die: seed 7 makes this exact game reproducible on any JVM.
        Game game = new Game(board, players, new StandardDice(7), new ExactRollWinRule());
        System.out.println(game.describeSetup());
        System.out.println();

        Player winner = game.playUntilWinner();
        System.out.println();
        System.out.println("Final standings:");
        for (Player player : game.getPlayers()) {
            System.out.println("  " + player);
        }
        System.out.println("Winner: " + winner.getName()
                + " after " + game.getTurnCount() + " total turns.");
        System.out.println("Same seed replays the identical game — rerun the demo to confirm.");
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section4ChainResolution() {
        System.out.println("=== Section 4: Multi-Hop Chain Resolution ===");
        // Ladder 3 -> 22, and 22 is a snake head (22 -> 8):
        // landing on 3 climbs to 22, then immediately slides down to 8.
        Board board = new Board(5, Arrays.asList(
                new MovementConsequence(22, 8, Direction.SNAKE),
                new MovementConsequence(3, 22, Direction.LADDER)
        ));
        List<MovementConsequence> chain = board.traceChain(3);
        System.out.println("Player lands on 3. Consequences fired, in order:");
        for (MovementConsequence step : chain) {
            System.out.println("  " + step.describe());
        }
        System.out.println("Resolved resting position: " + board.resolve(3));
        System.out.println();

        // A two-ladder chain: 4 -> 11, and 11 is another ladder foot (11 -> 19).
        Board chained = new Board(5, Arrays.asList(
                new MovementConsequence(4, 11, Direction.LADDER),
                new MovementConsequence(11, 19, Direction.LADDER)
        ));
        List<MovementConsequence> chain2 = chained.traceChain(4);
        System.out.println("Player lands on 4. Consequences fired, in order:");
        for (MovementConsequence step : chain2) {
            System.out.println("  " + step.describe());
        }
        System.out.println("Resolved resting position: " + chained.resolve(4));
        System.out.println("Cycle protection: construction-time check already rejected a cyclic");
        System.out.println("board in Section 2; runtime resolve() also carries a visited-set guard.");
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section5WinRuleStrategies() {
        System.out.println("=== Section 5: Win Rule Strategies (pluggable overshoot rule) ===");
        WinRule exact = new ExactRollWinRule();
        WinRule bounce = new BounceBackWinRule();
        int finish = 25;

        System.out.println("Standing at 22, rolling 4 (finish 25):");
        System.out.println("  " + exact.describe() + " -> " + exact.destination(22, 4, finish));
        System.out.println("  " + bounce.describe() + " -> " + bounce.destination(22, 4, finish));

        System.out.println("Standing at 23, rolling 2 (finish 25):");
        System.out.println("  " + exact.describe() + " -> " + exact.destination(23, 2, finish));
        System.out.println("  " + bounce.describe() + " -> " + bounce.destination(23, 2, finish));

        System.out.println("Standing at 24, rolling 1 (finish 25):");
        System.out.println("  " + exact.describe() + " -> " + exact.destination(24, 1, finish));
        System.out.println("  " + bounce.describe() + " -> " + bounce.destination(24, 1, finish));
        System.out.println("Rules are injected into Game — swapping the house rule is a");
        System.out.println("constructor argument, never an engine edit.");
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section6CrookedDice() {
        System.out.println("=== Section 6: Crooked Dice Decorator (never rolls 6) ===");
        System.out.println("The decorator wraps any Dice; Game is untouched (OCP).");

        // Fair die first, then the crooked wrapper around an identically-seeded
        // die (seed 5): the fair sequence contains 6s which the crooked one replaces.
        StandardDice fair = new StandardDice(5);
        System.out.print("Fair die,     20 rolls: ");
        for (int i = 0; i < 20; i++) {
            System.out.print(fair.roll() + " ");
        }
        System.out.println();

        CrookedDiceDecorator crooked = new CrookedDiceDecorator(new StandardDice(5));
        System.out.print("Crooked die,  20 rolls: ");
        for (int i = 0; i < 20; i++) {
            System.out.print(crooked.roll() + " ");
        }
        System.out.println();
        System.out.print("Crooked die,  12 rolls: ");
        for (int i = 0; i < 12; i++) {
            System.out.print(crooked.roll() + " ");
        }
        System.out.println();
        System.out.println("Dice used: " + crooked.describe());

        // Statistical evidence: 10,000 crooked rolls contain no 6, and all five
        // remaining faces still occur.
        int[] counts = new int[7];
        for (int i = 0; i < 10000; i++) {
            counts[crooked.roll()]++;
        }
        StringBuilder faces = new StringBuilder();
        for (int face = 1; face <= 5; face++) {
            faces.append(face).append(":").append(counts[face]).append("  ");
        }
        System.out.println("After 10,000 crooked rolls -> face " + faces.toString().trim());
        System.out.println("Face 6 appeared: " + (counts[6] == 0 ? "0 times (correctly suppressed)"
                : counts[6] + " times (BUG!)"));
        System.out.println();
    }

    // ------------------------------------------------------------------
    private interface Construction {
        Object construct();
    }

    private static void expectRejection(String description, Construction construction) {
        try {
            construction.construct();
            System.out.println("  [BUG] Accepted: " + description);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            System.out.println("  [OK] Rejected: " + description);
            System.out.println("        Reason: " + expected.getMessage());
        }
    }
}
