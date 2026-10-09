import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The game orchestrator: round-robin turns until someone wins.
 *
 * Game contains NO game-specific branching — no "if snake", no "if crooked
 * die", no "if bounce back". Everything that varies (dice behaviour, win
 * rule, board layout) is injected, which is what makes the design OCP-clean.
 */
public class Game {

    private final Board board;
    private final List<Player> players;
    private final Dice dice;
    private final WinRule winRule;

    private int currentPlayerIndex;
    private int turnCount;
    private Player winner;

    public Game(Board board, List<Player> players, Dice dice, WinRule winRule) {
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        if (dice == null) {
            throw new IllegalArgumentException("Dice cannot be null");
        }
        if (winRule == null) {
            throw new IllegalArgumentException("Win rule cannot be null");
        }
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("A game needs at least 2 players, got "
                    + (players == null ? "null" : players.size()));
        }
        Set<String> names = new HashSet<>();
        for (Player player : players) {
            if (player == null) {
                throw new IllegalArgumentException("Player list cannot contain null entries");
            }
            if (!names.add(player.getName())) {
                throw new IllegalArgumentException(
                        "Duplicate player name: " + player.getName() + " — round-robin bookkeeping would be ambiguous");
            }
        }
        this.board = board;
        this.players = new ArrayList<>(players);
        this.dice = dice;
        this.winRule = winRule;
        this.currentPlayerIndex = 0;
        this.turnCount = 0;
    }

    /**
     * Play one turn for the current player and advance the turn order.
     * @return the player that won this turn, or null if nobody won yet
     */
    public Player playTurn() {
        if (winner != null) {
            throw new IllegalStateException(
                    "Game is over — " + winner.getName() + " already won after " + turnCount + " turns");
        }
        Player player = players.get(currentPlayerIndex);
        turnCount++;

        int roll = dice.roll();
        int finish = board.getFinishPosition();
        int from = player.getPosition();
        int landing = winRule.destination(from, roll, finish);

        if (landing == from && roll > 0) {
            // Win rule refused the move (overshoot under exact-roll rule).
            System.out.printf("Turn %3d | %-7s rolls %d at %d -> overshoots finish %d, stays at %d%n",
                    turnCount, player.getName(), roll, from, finish, from);
            advanceTurn();
            return null;
        }

        StringBuilder narration = new StringBuilder();
        narration.append(String.format("Turn %3d | %-7s rolls %d: %d -> %d",
                turnCount, player.getName(), roll, from, landing));

        if (board.hasConsequenceAt(landing)) {
            int resting = board.resolve(landing);
            List<MovementConsequence> chain = board.traceChain(landing);
            narration.append(" [");
            for (int i = 0; i < chain.size(); i++) {
                if (i > 0) {
                    narration.append(" then ");
                }
                MovementConsequence step = chain.get(i);
                narration.append(step.getDirection() == Direction.LADDER ? "ladder " : "snake ")
                          .append(step.getStart()).append("->").append(step.getEnd());
            }
            narration.append(" -> rests at ").append(resting).append("]");
            player.moveTo(resting);
        } else {
            player.moveTo(landing);
        }

        System.out.println(narration.toString());

        if (player.getPosition() == finish) {
            winner = player;
            System.out.println(">>> " + player.getName() + " reaches the finish cell "
                    + finish + " and WINS the game in " + turnCount + " turns!");
            return player;
        }
        advanceTurn();
        return null;
    }

    private void advanceTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    /**
     * Loop turns until a winner emerges.
     * @return the winning player
     */
    public Player playUntilWinner() {
        Player w = playTurn();
        while (w == null) {
            w = playTurn();
        }
        return w;
    }

    public Player getWinner() {
        return winner;
    }

    public boolean isOver() {
        return winner != null;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public String describeSetup() {
        return "Game setup: board finish at " + board.getFinishPosition()
                + ", players=" + playerNames()
                + ", dice=[" + dice.describe() + "]"
                + ", win rule=[" + winRule.describe() + "]";
    }

    private String playerNames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(players.get(i).getName());
        }
        return sb.toString();
    }
}
