/**
 * Vending machine demo - the full narrative, nine sections:
 *
 *   1. Machine setup + price list (catalog fixed, stock and float loaded).
 *   2. Happy path: coins + a note in, selection, change made, receipt.
 *   3. Insufficient money -> a MESSAGE, keep-inserting, then the sale.
 *   4. Refund mid-transaction: their own money back, insertion order.
 *   5. Exact payment: no change, trivial plan.
 *   6. Sale DECLINED when change is not composable (float drained of
 *      small coins) -> "exact change only", customer refunded.
 *   7. Out-of-stock rejection (money kept, choice refused, not customer).
 *   8. Wrong-state guardrails: select in IDLE, insert in HAS_MONEY,
 *      restock mid-sale.
 *   9. Audit: reserves total, per-denomination float, inventory.
 *
 * Deterministic: no threads, no sleeps, no Random. Run: `javac *.java
 * && java VendingMachineDemo` (or the single-file source launcher).
 */
public class VendingMachineDemo {

    public static void main(String[] args) {
        System.out.println("=== Vending Machine Demo (State pattern, Indian denominations) ===\n");

        // =================================================================
        System.out.println("=== 1. Machine setup + price list ===");
        VendingMachine machine = new VendingMachine(
                new Product("A1", "Masala Chai", 15),
                new Product("A2", "Filter Coffee", 20),
                new Product("B1", "Samosa", 25),
                new Product("B2", "Vada Pav", 30));
        machine.restock("A1", 3);
        machine.restock("A2", 2);
        machine.restock("B1", 4);
        machine.restock("B2", 1);
        // The change float: notes are accepted but NEVER dispensed as
        // change in this machine (see Note's Javadoc) - coins are what
        // change is made of, so the float is coin-heavy on purpose.
        machine.loadChange(Coin.TEN, 4);
        machine.loadChange(Coin.FIVE, 2);
        machine.loadChange(Coin.TWO, 3);
        machine.loadChange(Coin.ONE, 5);
        machine.loadChange(Note.TEN, 2); // float padding, not change material
        System.out.println("Price list:");
        System.out.print(machine.priceList());
        System.out.println("Change float: coins=" + machine.coinReserves()
                + " notes=" + machine.noteReserves()
                + " (INR " + machine.reservesTotal() + ")");
        System.out.println("State: " + machine.getState() + "\n");

        // =================================================================
        System.out.println("=== 2. Happy path: pay INR 50 for INR 25, change made ===");
        machine.insertCoin(Coin.TEN);
        machine.insertNote(Note.TWENTY);
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        System.out.println("  [demo] state after inserting: " + machine.getState());
        machine.selectProduct("B1"); // Samosa INR 25, paid INR 50, change INR 25
        System.out.println("State after sale: " + machine.getState()
                + " | B1 left: " + machine.availableQuantity("B1") + "\n");

        // =================================================================
        System.out.println("=== 3. Insufficient money -> message, keep inserting ===");
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.FIVE); // INR 15 vs INR 30 Vada Pav
        machine.selectProduct("B2");
        System.out.println("  [demo] still in " + machine.getState()
                + " with INR " + machine.insertedTotal() + " - topping up:");
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.FIVE); // INR 30 now - the second attempt lands
        machine.selectProduct("B2");
        System.out.println();

        // =================================================================
        System.out.println("=== 4. Refund mid-transaction ===");
        machine.insertNote(Note.FIFTY);
        System.out.println("  [demo] inserted INR " + machine.insertedTotal()
                + ", state " + machine.getState());
        machine.refund();
        System.out.println("State after refund: " + machine.getState()
                + " | inserted: INR " + machine.insertedTotal() + "\n");

        // =================================================================
        System.out.println("=== 5. Exact payment (no change) ===");
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.FIVE); // INR 15 = Masala Chai exactly
        machine.selectProduct("A1");
        System.out.println();

        // =================================================================
        System.out.println("=== 6. Change not composable -> sale DECLINED, refunded ===");
        // A fresh machine with a deliberately starved float shows it best:
        // INR 10 in the float (one ten-coin, nothing smaller) cannot make
        // INR 15 change, so a INR 40 payment for the INR 25 Samosa must be
        // declined - "exact change only" - and the money handed back.
        VendingMachine starved = new VendingMachine(
                new Product("A1", "Masala Chai", 15),
                new Product("B1", "Samosa", 25));
        starved.restock("A1", 5);
        starved.restock("B1", 5); // plenty of stock - only the float is starved
        starved.loadChange(Coin.TEN, 1);
        System.out.println("Starved machine float: " + starved.coinReserves()
                + " (INR " + starved.reservesTotal() + ") - cannot make INR 15 from a lone 10");
        starved.insertNote(Note.TWENTY);
        starved.insertNote(Note.TWENTY); // INR 40 for INR 25 -> change INR 15
        starved.selectProduct("B1");
        System.out.println("Starved machine after the decline: " + starved
                + " | B1 stock: " + starved.availableQuantity("B1")
                + " (untouched), inserted INR 0 (refunded)");
        System.out.println("-> The plan ran BEFORE any commit: product still stocked, "
                + "money back in the customer's hand, reserves INR "
                + starved.reservesTotal() + " (untouched).\n");

        // =================================================================
        System.out.println("=== 7. Out-of-stock rejection ===");
        // B2 (Vada Pav) started with 1 unit and Section 3 sold it.
        System.out.println("B2 stock on main machine: " + machine.availableQuantity("B2"));
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN); // INR 30, exactly the price
        machine.selectProduct("B2"); // out of stock: money kept, no exception
        machine.selectProduct("A2"); // picks a stocked one instead
        System.out.println();

        // =================================================================
        System.out.println("=== 8. Wrong-state guardrails ===");
        guardrail("selectProduct in IDLE",
                () -> machine.selectProduct("A1"));
        guardrail("refund in IDLE (nothing inserted, nothing to refund)",
                () -> machine.refund());
        machine.insertCoin(Coin.TEN); // park the machine in HAS_MONEY
        guardrail("restock in HAS_MONEY (shelf must not move under a sale)",
                () -> machine.restock("A1", 1));
        guardrail("loadChange in HAS_MONEY (float must not move under a plan)",
                () -> machine.loadChange(Coin.ONE, 10));
        machine.refund(); // clean exit from HAS_MONEY for the next section
        System.out.println();

        // =================================================================
        System.out.println("=== 9. Audit: reserves + inventory ===");
        System.out.println("Main machine: " + machine);
        System.out.println("State: " + machine.getState());
        System.out.println("Change reserves: INR " + machine.reservesTotal()
                + " | coins " + machine.coinReserves()
                + " | notes " + machine.noteReserves());
        System.out.println("Inventory (" + machine.totalUnitsStocked() + " units):");
        for (String code : machine.stockedCodes()) {
            System.out.println("  " + code + ": " + machine.availableQuantity(code)
                    + " left");
        }
        // Money conservation check across the whole demo: every rupee is
        // either in the float or went back out as change/refund/receipt.
        System.out.println("Starved machine (untouched by the decline): "
                + starved);

        System.out.println("\n=== End of Vending Machine demo ===");
    }

    /**
     * Prints the guardrail's name, runs the guarded action expecting the
     * IllegalStateException, and prints the machine's message. The demo's
     * equivalent of a unit assert - but visible in the log so the reader
     * sees the exact wording of each rejection.
     */
    private static void guardrail(String name, Runnable action) {
        System.out.println("  [demo] Guardrail: " + name);
        try {
            action.run();
            System.out.println("  [demo] !! NO EXCEPTION RAISED - guardrail missing");
        } catch (IllegalStateException expected) {
            System.out.println("  [demo] rejected: " + expected.getMessage());
        }
    }
}
