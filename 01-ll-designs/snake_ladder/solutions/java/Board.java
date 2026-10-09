import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * An N x N board of positions 1..dimension^2.
 *
 * The board is a SPARSE map of consequences, not an N x N grid of cells:
 * a cell in this game has no state or behaviour of its own — the only thing
 * that distinguishes one cell from another is whether a consequence starts
 * there. A map IS the board (O(S+L) memory instead of O(N^2)).
 *
 * Board owns all validation (fail fast at construction) and chain resolution
 * with cycle protection.
 */
public class Board {

    private final int dimension;
    private final int finishPosition;
    private final Map<Integer, MovementConsequence> consequences;

    public Board(int dimension, List<MovementConsequence> snakesAndLadders) {
        if (dimension < 1) {
            throw new IllegalArgumentException("Board dimension must be at least 1, got " + dimension);
        }
        if (snakesAndLadders == null) {
            throw new IllegalArgumentException("Consequence list cannot be null (pass an empty list)");
        }
        this.dimension = dimension;
        this.finishPosition = dimension * dimension;
        this.consequences = new LinkedHashMap<>();

        for (MovementConsequence consequence : snakesAndLadders) {
            if (consequence == null) {
                throw new IllegalArgumentException("Consequence list cannot contain null entries");
            }
            validateBounds(consequence);
            validateNoOverlap(consequence);
            this.consequences.put(consequence.getStart(), consequence);
        }
        validateNoCycles();
    }

    private void validateBounds(MovementConsequence consequence) {
        if (consequence.getStart() < 1 || consequence.getStart() > finishPosition) {
            throw new IllegalArgumentException(
                    "Consequence start " + consequence.getStart()
                    + " is outside board positions 1.." + finishPosition);
        }
        if (consequence.getEnd() < 1 || consequence.getEnd() > finishPosition) {
            throw new IllegalArgumentException(
                    "Consequence end " + consequence.getEnd()
                    + " is outside board positions 1.." + finishPosition);
        }
        if (consequence.getStart() == finishPosition) {
            throw new IllegalArgumentException(
                    "Consequence cannot start at the finish cell " + finishPosition
                    + " — landing there wins the game before any jump could occur");
        }
    }

    private void validateNoOverlap(MovementConsequence consequence) {
        MovementConsequence existing = consequences.get(consequence.getStart());
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Two consequences start at cell " + consequence.getStart()
                    + " (" + existing.describe() + " and " + consequence.describe()
                    + ") — jump would be ambiguous");
        }
    }

    /**
     * Detect consequence cycles (snake head -> ladder foot -> same snake head)
     * by walking every chain with a visited set. Better to throw here than to
     * spin forever mid-game.
     */
    private void validateNoCycles() {
        for (Integer start : consequences.keySet()) {
            Set<Integer> visited = new TreeSet<>();
            int position = start;
            visited.add(position);
            while (consequences.containsKey(position)) {
                position = consequences.get(position).jump(position);
                if (!visited.add(position)) {
                    throw new IllegalArgumentException(
                            "Cyclic consequence chain detected: " + visited
                            + " re-enters cell " + position
                            + " — jumping would never terminate");
                }
                if (position == finishPosition) {
                    break;
                }
            }
        }
    }

    /**
     * Resolve landing on a cell: follow snake/ladder jumps until a plain cell.
     * Handles multi-hop chains (ladder foot whose top is a snake head, etc.).
     *
     * @param position the cell just landed on
     * @return the final resting cell after all jumps
     */
    public int resolve(int position) {
        if (position < 1 || position > finishPosition) {
            throw new IllegalArgumentException(
                    "Position " + position + " is outside board positions 1.." + finishPosition);
        }
        if (position == finishPosition) {
            return position; // winning cell is terminal
        }
        Set<Integer> visited = new TreeSet<>();
        visited.add(position);
        while (consequences.containsKey(position)) {
            MovementConsequence consequence = consequences.get(position);
            position = consequence.jump(position);
            if (!visited.add(position)) {
                throw new IllegalStateException(
                        "Cycle while resolving chain: re-entered cell " + position
                        + " after visiting " + visited);
            }
        }
        return position;
    }

    /**
     * Trace the full jump chain from a landing cell, for demo narration.
     * @return list of consequences fired in order (empty if none)
     */
    public List<MovementConsequence> traceChain(int position) {
        List<MovementConsequence> fired = new ArrayList<>();
        if (position < 1 || position > finishPosition) {
            throw new IllegalArgumentException(
                    "Position " + position + " is outside board positions 1.." + finishPosition);
        }
        Set<Integer> visited = new TreeSet<>();
        visited.add(position);
        while (position != finishPosition && consequences.containsKey(position)) {
            MovementConsequence consequence = consequences.get(position);
            fired.add(consequence);
            position = consequence.jump(position);
            if (!visited.add(position)) {
                break; // cycle; resolve() would throw, trace just stops
            }
        }
        return fired;
    }

    public boolean hasConsequenceAt(int position) {
        return consequences.containsKey(position);
    }

    public int getFinishPosition() {
        return finishPosition;
    }

    public int getDimension() {
        return dimension;
    }

    public List<MovementConsequence> getConsequences() {
        return new ArrayList<>(consequences.values());
    }

    /**
     * Render the board boustrophedon (rows alternate direction, like the
     * real game), marking snake heads with S, ladder feet with L and the
     * finish with W. Purely presentation — the game engine never needs 2-D.
     */
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("Board ").append(dimension).append("x").append(dimension)
          .append(" (positions 1..").append(finishPosition).append("):\n");
        for (int row = dimension; row >= 1; row--) {
            StringBuilder line = new StringBuilder();
            if (row % 2 == 0) {
                // even rows (from the bottom) run left-to-right in real boards:
                // row 2 of a 10x10 is 11..20 read left to right
                for (int col = 1; col <= dimension; col++) {
                    appendCell(line, cellNumber(row, col));
                }
            } else {
                for (int col = dimension; col >= 1; col--) {
                    appendCell(line, cellNumber(row, col));
                }
            }
            sb.append("  ").append(line.toString().trim()).append("\n");
        }
        return sb.toString();
    }

    private int cellNumber(int row, int col) {
        // row 1 (bottom) is 1..dimension, row r occupies ((r-1)*dimension)+1 .. r*dimension
        return (row - 1) * dimension + col;
    }

    private void appendCell(StringBuilder line, int cell) {
        String marker = "  " + cell;
        if (consequences.containsKey(cell)) {
            MovementConsequence consequence = consequences.get(cell);
            marker = marker + (consequence.getDirection() == Direction.SNAKE ? "S" : "L");
        }
        if (cell == finishPosition) {
            marker = marker + "W";
        }
        line.append(String.format("%5s", marker));
    }
}
