import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Product stock: how many of each product code the machine holds.
 *
 * OWNERSHIP (a load-bearing decision): inventory is a SEPARATE component from
 * the catalog (Product objects) and from the machine state. The state machine
 * ASKS it questions ("can you sell code X?") and TELLS it outcomes
 * ("dispense one now"); it never mutates stock on its own. Why: a restocker,
 * a sales report, and a low-stock alert all need the same single source of
 * truth; burying quantities inside the state machine makes each of those a
 * state-machine edit (OCP violation, and the classic way these codebases rot).
 *
 * Plan-then-commit: every dispense is a reserve -> (later) commit or release.
 * Nothing here debits stock without an explicit COMMIT, so a failed payment
 * path can never leak a half-sale.
 */
public class ProductInventory {

    private final Map<String, Integer> stockByCode = new HashMap<>();

    /** Restock (or set initial stock) for a product code. */
    public void restock(String code, int quantity) {
        validateCode(code);
        if (quantity < 0) {
            throw new IllegalArgumentException(
                    "Restock quantity cannot be negative, got " + quantity + " for code " + code);
        }
        stockByCode.merge(code, quantity, Integer::sum);
    }

    /** True when at least one unit of `code` is available. */
    public boolean isAvailable(String code) {
        return availableQuantity(code) > 0;
    }

    /** Units of `code` currently stocked (0 if unknown code). */
    public int availableQuantity(String code) {
        return stockByCode.getOrDefault(validateCode(code), 0);
    }

    /**
     * COMMIT a dispense: remove one unit. Only the state machine calls this,
     * and only on the success path (coins accepted, item released).
     */
    public void commitDispense(String code) {
        int remaining = availableQuantity(code);
        if (remaining < 1) {
            throw new IllegalStateException(
                    "Cannot dispense " + code + ": no stock (inventory says 0) — "
                    + "the state machine must check availability BEFORE payment");
        }
        stockByCode.put(code, remaining - 1);
    }

    /** All codes ever stocked (for display). */
    public Set<String> stockedCodes() {
        return Set.copyOf(stockByCode.keySet());
    }

    /** Total units across all codes (for the demo's restock narrative). */
    public int totalUnits() {
        int total = 0;
        for (int quantity : stockByCode.values()) {
            total += quantity;
        }
        return total;
    }

    @Override
    public String toString() {
        return "ProductInventory" + stockByCode;
    }

    private static String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Product code cannot be null or empty");
        }
        return code.trim();
    }
}
