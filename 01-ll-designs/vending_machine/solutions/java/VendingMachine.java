import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * THE VENDING MACHINE CONTEXT - the heart of the state pattern (the repo's
 * ATM sibling, in vending form).
 *
 * The VendingMachine object IS the machine: coin slot, note acceptor,
 * product shelf, change dispenser, and the CURRENT STATE pointer. Unlike
 * the ATM (whose behaviour is delegated to state CLASSES), this build keeps
 * the states as a plain enum and puts the transition table INLINE in the
 * context - a deliberate simplification worth defending out loud in an
 * interview: the vending machine has THREE states and four legal actions;
 * a state-per-class hierarchy would be five+ files of ceremony around a
 * table that fits on one screen. The ATM earns its state classes because
 * its states carry real policy (PIN retry counting, confiscation); here
 * the only per-state logic is "which actions are legal", which a guard
 * helper expresses exactly. The enum still gives the interview answer the
 * pattern is fishing for: name the states, name the events, and explain
 * why REFUNDING and OUT_OF_STOCK are NOT states (see VendingState's Javadoc).
 *
 * DIVISION OF LABOUR (the load-bearing decision): this class owns only the
 * ORDER of operations. It ASKS CashInventory and ProductInventory questions
 * ("is code in stock?", "can you plan change?") and TELLS them outcomes
 * ("commit the dispense", "move inserted to reserves"). No money math, no
 * stock arithmetic, no greedy walk lives here - re-implementing any of it
 * would fork the single source of truth (the classic rot in these designs).
 *
 * ORDER OF OPERATIONS in selectProduct (the transaction's commit ladder):
 *   1. stock check        - cheapest first; rejects before money moves
 *   2. price vs inserted  - a message, NOT an exception (see below)
 *   3. planChange dry-run - reserves can still decline the sale
 *   4. DECLINE branch     - refund inserted, stay honest, return to IDLE
 *   5. COMMIT             - product first, then cash, then receipt, then IDLE
 * The decline path between 3 and 4 is the whole point of plan-then-commit:
 * a machine that commits the product before planning change can end up
 * having sold an item it cannot make change for - unrecoverable with
 * physical money.
 *
 * INSERTING MONEY is legal in IDLE (the first feed STARTS the transaction,
 * IDLE -> HAS_MONEY) and in HAS_MONEY (later feeds top it up). The rejected
 * alternative - accepting money ONLY in IDLE, "one feed per transaction" -
 * is unworkable on real hardware: a customer paying INR 50 feeds 10+20+10+10,
 * and after the first coin the machine is definitionally in HAS_MONEY, so an
 * IDLE-only rule would throw on the second coin; worse, an INR 15 product
 * would be unpayable by any single coin or note. What the machine DOES
 * reject is money in DISPENSING (never rests there - the commit is atomic)
 * and every action outside its legal states: SELECT/REFUND in IDLE,
 * SELECT in DISPENSING, admin loads mid-sale (see requireState).
 *
 * INSUFFICIENT MONEY is a MESSAGE, not an exception (the documented pick):
 * a customer who inserts INR 20 for an INR 25 product has done nothing
 * wrong - "keep inserting" is normal vending behaviour, and the machine
 * stays in HAS_MONEY so they can add the missing INR 5. Compare WRONG
 * STATE, which throws IllegalStateException: refunding in IDLE IS
 * machine misuse (nothing is inserted; there is nothing to refund).
 * Recoverable-by-design events return; illegal ones throw.
 *
 * SELECTED PRODUCT LIVES HERE (per VendingState's Javadoc: states are
 * stateless singletons, session facts live on the context). The code is
 * reset on every path that exits HAS_MONEY - dispense, refund, decline -
 * so no stale selection can leak into the next customer's transaction.
 * (Inserted money needs no such field: it lives in CashInventory's
 * inserted deques, which refundInserted()/commitInsertedToReserves()
 * already clear on both exits.)
 */
public class VendingMachine {

    private final Map<String, Product> catalog = new LinkedHashMap<>();
    private final CashInventory cashInventory = new CashInventory();
    private final ProductInventory productInventory = new ProductInventory();

    // ---- session facts (the states are stateless; the context is not) ----
    private VendingState state = VendingState.IDLE;
    private String selectedCode;

    /**
     * The machine is built empty of stock but with its catalog: what it CAN
     * sell (codes -> products) is fixed at construction - the machine is
     * loaded, not re-programmed, afterwards. Prices changing mid-transaction
     * (the mutable-catalog bug) is impossible by construction.
     */
    public VendingMachine(Product... products) {
        if (products == null || products.length == 0) {
            throw new IllegalArgumentException(
                    "A vending machine needs at least one product in its catalog");
        }
        for (Product product : products) {
            if (product == null) {
                throw new IllegalArgumentException("Catalog products cannot be null");
            }
            Product existing = catalog.put(product.getCode(), product);
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate product code in catalog: "
                        + product.getCode());
            }
        }
    }

    // ------------------------------------------------------------------
    // Customer actions (the legal-per-state surface)
    // ------------------------------------------------------------------

    /**
     * Customer feeds in a coin. Legal in IDLE (starts the transaction,
     * IDLE -> HAS_MONEY) and in HAS_MONEY (tops up the payment - see the
     * class Javadoc for why IDLE-only was rejected: multi-coin payments
     * are the normal case, not misuse). Rejected in DISPENSING: the commit
     * ladder runs to completion; new money mid-dispense would be a second
     * transaction starting inside the first one's commit.
     */
    public void insertCoin(Coin coin) {
        if (coin == null) {
            throw new IllegalArgumentException("Inserted coin cannot be null");
        }
        if (state == VendingState.DISPENSING) {
            throw new IllegalStateException("Cannot insertCoin in state " + state
                    + " (the sale is committing - wait for IDLE)");
        }
        cashInventory.insertCoin(coin);
        state = VendingState.HAS_MONEY;
        System.out.println("  [machine] Coin INR " + coin.getValue() + " accepted "
                + "(inserted total: INR " + cashInventory.insertedTotal() + ")");
    }

    /**
     * Customer feeds in a note. Same rules as insertCoin - a note has its
     * own hardware path (note acceptor) but identical transaction semantics.
     */
    public void insertNote(Note note) {
        if (note == null) {
            throw new IllegalArgumentException("Inserted note cannot be null");
        }
        if (state == VendingState.DISPENSING) {
            throw new IllegalStateException("Cannot insertNote in state " + state
                    + " (the sale is committing - wait for IDLE)");
        }
        cashInventory.insertNote(note);
        state = VendingState.HAS_MONEY;
        System.out.println("  [machine] Note INR " + note.getValue() + " accepted "
                + "(inserted total: INR " + cashInventory.insertedTotal() + ")");
    }

    /**
     * Customer selects a product code. Legal only in HAS_MONEY.
     *
     * Out-of-stock and unknown codes are MESSAGES (stay in HAS_MONEY, keep
     * the money, let the customer pick again or refund) - rejecting the
     * choice is not rejecting the customer. Insufficient money is likewise
     * a message with the shortfall named. When money suffices, the commit
     * ladder runs (see the class Javadoc): plan, decline-or-commit, receipt,
     * back to IDLE. Declines REFUND the inserted money - a real machine
     * returns the coins rather than holding them hostage.
     */
    public void selectProduct(String code) {
        requireState(VendingState.HAS_MONEY, "selectProduct");
        String clean = validateCode(code);
        Product product = catalog.get(clean);
        if (product == null) {
            System.out.println("  [machine] Unknown code '" + clean + "' - codes are "
                    + catalog.keySet() + " (money kept; pick again or press refund)");
            return;
        }
        if (!productInventory.isAvailable(clean)) {
            System.out.println("  [machine] '" + product.getName() + "' is OUT OF STOCK "
                    + "(money kept; pick another or press refund)");
            return;
        }
        int inserted = cashInventory.insertedTotal();
        int price = product.getPriceInRupees();
        if (inserted < price) {
            System.out.println("  [machine] '" + product.getName() + "' costs INR " + price
                    + ", inserted INR " + inserted + " - short by INR " + (price - inserted)
                    + "; please insert more or press refund");
            return;
        }
        selectedCode = clean;
        state = VendingState.DISPENSING;
        completeSale(product, inserted - price);
    }

    /**
     * Customer (or a timeout in a real build) presses refund. Legal only in
     * HAS_MONEY. Returns the Refund record (their OWN money, insertion
     * order) and the machine goes straight back to IDLE - the reason
     * REFUNDING is an event, not a state (see VendingState's Javadoc).
     */
    public Refund refund() {
        requireState(VendingState.HAS_MONEY, "refund");
        Refund refund = cashInventory.refundInserted();
        resetSession();
        System.out.println("  [machine] REFUND issued: " + refund
                + " (total INR " + refund.totalRupees() + ") - back to IDLE");
        return refund;
    }

    // ------------------------------------------------------------------
    // The commit ladder (called only from selectProduct)
    // ------------------------------------------------------------------

    /**
     * Runs the sale to completion from DISPENSING. The decline branch
     * matters as much as the commit branch: when planChange returns null
     * the reserves cannot compose the change, and a real machine says
     * "exact change only" and hands the money back rather than selling
     * itself into an unmakeable position.
     */
    private void completeSale(Product product, int changeDue) {
        ChangePlan plan = cashInventory.planChange(changeDue);
        if (plan == null) {
            System.out.println("  [machine] Cannot make change for INR " + changeDue
                    + " from reserves (exact change only) - declining the sale of '"
                    + product.getName() + "'");
            Refund refund = cashInventory.refundInserted();
            resetSession();
            System.out.println("  [machine] Sale DECLINED - customer refunded: " + refund
                    + " (total INR " + refund.totalRupees() + ") - back to IDLE");
            return;
        }
        // COMMIT: product first, then cash, then receipt. Each inventory
        // owns exactly one mutation; the context owns only the order.
        productInventory.commitDispense(product.getCode());
        cashInventory.commitInsertedToReserves();
        cashInventory.dispenseChange(plan);
        printReceipt(product, changeDue, plan);
        resetSession();
    }

    /**
     * The receipt is printed AFTER the commit mutations: paper trails
     * describe what happened, never what is about to (a receipt printed
     * before a jam is a lying receipt). Change composition comes from the
     * committed plan, not a re-derived walk - same reserves snapshot, no
     * second greedy pass (see ChangePlan's Javadoc).
     */
    private void printReceipt(Product product, int changeDue, ChangePlan plan) {
        System.out.println("  [machine] === RECEIPT ===");
        System.out.println("  [machine]   " + product.getName() + " (code "
                + product.getCode() + ")   INR " + product.getPriceInRupees());
        System.out.println("  [machine]   paid: INR "
                + (product.getPriceInRupees() + changeDue) + "   change: INR "
                + changeDue + " (" + plan + ")");
        System.out.println("  [machine]   dispensed: " + product.getName()
                + " + change" + (plan.isExact() ? " (none - exact payment)" : ""));
        System.out.println("  [machine] =======================");
    }

    // ------------------------------------------------------------------
    // Admin passthroughs (the refiller's API - thin on purpose)
    // ------------------------------------------------------------------

    /**
     * Restock a catalog code. Legal only in IDLE: restocking mid-sale would
     * let stock appear between the availability check and the commit - the
     * plan-then-commit guarantee assumes the shelf does not move under a
     * running transaction.
     */
    public void restock(String code, int quantity) {
        requireState(VendingState.IDLE, "restock");
        if (catalog.get(validateCode(code)) == null) {
            throw new IllegalArgumentException("Cannot restock unknown code '" + code
                    + "': not in this machine's catalog " + catalog.keySet());
        }
        productInventory.restock(code, quantity);
    }

    /**
     * Load the change float. Legal only in IDLE for the same reason as
     * restock: a planChange dry-run must not see reserves that a concurrent
     * refiller is editing (the plan IS a reservation over the reserves).
     */
    public void loadChange(Coin coin, int quantity) {
        requireState(VendingState.IDLE, "loadChange");
        cashInventory.loadChange(coin, quantity);
    }

    public void loadChange(Note note, int quantity) {
        requireState(VendingState.IDLE, "loadChange");
        cashInventory.loadChange(note, quantity);
    }

    // ------------------------------------------------------------------
    // Display / audit helpers (read-only; legal in any state)
    // ------------------------------------------------------------------

    public VendingState getState() {
        return state;
    }

    public int insertedTotal() {
        return cashInventory.insertedTotal();
    }

    public int reservesTotal() {
        return cashInventory.reservesTotal();
    }

    public Map<Note, Integer> noteReserves() {
        return cashInventory.getNoteReserves();
    }

    public Map<Coin, Integer> coinReserves() {
        return cashInventory.getCoinReserves();
    }

    public int availableQuantity(String code) {
        return productInventory.availableQuantity(validateCode(code));
    }

    public Set<String> stockedCodes() {
        return productInventory.stockedCodes();
    }

    public int totalUnitsStocked() {
        return productInventory.totalUnits();
    }

    /** The price list as the machine would print it on its front panel. */
    public String priceList() {
        StringBuilder sb = new StringBuilder();
        for (Product product : catalog.values()) {
            sb.append("  ").append(product.getCode()).append("  ")
              .append(product.getName()).append("   INR ")
              .append(product.getPriceInRupees()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "VendingMachine[state=" + state + ", inserted=INR "
                + cashInventory.insertedTotal() + ", reserves=INR "
                + cashInventory.reservesTotal() + ", stock="
                + productInventory.totalUnits() + " units]";
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * The single wrong-state guard (the house pattern - a message-carrying
     * IllegalStateException, as in the ATM's AtmState.default handler).
     * Every action funnels through here, so the transition table reads as
     * one requireState call per action: the guard IS the table.
     */
    private void requireState(VendingState required, String action) {
        if (state != required) {
            throw new IllegalStateException("Cannot " + action + " in state " + state
                    + " (requires " + required + ")");
        }
    }

    /** Clears the session on EVERY exit from a transaction (no stale leaks). */
    private void resetSession() {
        selectedCode = null;
        state = VendingState.IDLE;
    }

    private static String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Product code cannot be null or empty");
        }
        return code.trim();
    }
}
