import java.util.List;
import java.util.Map;

/**
 * End-to-end Splitwise walkthrough.
 *
 * Scenario: four flatmates in a Bengaluru apartment group. Mixed split
 * types, the equal-split rounding edge case, validation failures, pairwise
 * balances, greedy debt simplification, and a real settle-up that clears
 * the group to zero.
 *
 * Run instructions: see solutions/java/README.md
 */
public class SplitwiseDemo {

    public static void main(String[] args) {
        SplitwiseService splitwise = new SplitwiseService(
            new InMemoryUserRepository(), new InMemoryGroupRepository());

        System.out.println("=== Splitwise: Setup ===");
        User anjali = splitwise.addUser("u1", "Anjali", "anjali@example.com");
        User rahul = splitwise.addUser("u2", "Rahul", "rahul@example.com");
        User vikram = splitwise.addUser("u3", "Vikram", "vikram@example.com");
        User meera = splitwise.addUser("u4", "Meera", "meera@example.com");
        System.out.println("Registered users: " + anjali + ", " + rahul + ", "
            + vikram + ", " + meera);

        Group flat = splitwise.createGroup("g1", "Koramangala Flat 4B",
            "u1", "u2", "u3", "u4");
        System.out.println("Created group: " + flat);

        System.out.println();
        System.out.println("=== Step 1: EQUAL split that divides cleanly ===");
        System.out.println("Rent Rs.3000.00 split equally among all four.");
        System.out.println("Anjali paid the landlord: 3000/4 = 750.00 each.");
        Expense rent = new Expense("e1", "September rent",
            new java.math.BigDecimal("3000.00"), "u1",
            Expense.equalAmong("u1", "u2", "u3", "u4"));
        splitwise.addExpense("g1", rent);
        printExpenseShares(new ExpenseService(), rent);
        printBalances(splitwise, "g1");

        System.out.println();
        System.out.println("=== Step 2: EQUAL split that does NOT divide cleanly ===");
        System.out.println("Dinner Rs.1000.00 among 3 people (Anjali, Rahul, Vikram).");
        System.out.println("1000.00 / 3 = 333.33 each, but 333.33 x 3 = 999.99 - a paisa short.");
        System.out.println("Rule: floor everyone, give the remainder to the LAST split entry.");
        System.out.println("Vikram paid. Split order: Anjali, Rahul, Vikram.");
        Expense dinner = new Expense("e2", "Trattoria dinner",
            new java.math.BigDecimal("1000.00"), "u3",
            Expense.equalAmong("u1", "u2", "u3"));
        splitwise.addExpense("g1", dinner);
        printExpenseShares(new ExpenseService(), dinner);
        printBalances(splitwise, "g1");

        System.out.println();
        System.out.println("=== Step 3: PERCENT split (must sum to exactly 100%) ===");
        System.out.println("Coorg trip Rs.5000.00, split 40/30/20/10 - Meera took the");
        System.out.println("master bedroom. Rahul paid for the car and the stay.");
        Expense trip = new Expense("e3", "Coorg trip",
            new java.math.BigDecimal("5000.00"), "u2",
            java.util.Arrays.asList(
                new PercentSplit("u1", 4000),   // Anjali 40.00%
                new PercentSplit("u2", 3000),   // Rahul 30.00%
                new PercentSplit("u3", 2000),   // Vikram 20.00%
                new PercentSplit("u4", 1000))); // Meera 10.00%
        splitwise.addExpense("g1", trip);
        printExpenseShares(new ExpenseService(), trip);
        printBalances(splitwise, "g1");

        System.out.println("Now try a percent expense whose splits sum to 99%...");
        try {
            new Expense("e-bad", "Bad percents", new java.math.BigDecimal("500.00"), "u1",
                java.util.Arrays.asList(
                    new PercentSplit("u1", 3300),
                    new PercentSplit("u2", 3300),
                    new PercentSplit("u3", 3300)));
            System.out.println("  UNEXPECTED: invalid percent expense was accepted!");
        } catch (IllegalArgumentException e) {
            System.out.println("  REJECTED: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== Step 4: EXACT split (must sum to the total) ===");
        System.out.println("Groceries Rs.840.50. Meera paid; everyone's share is exact.");
        Expense groceries = new Expense("e4", "BigBasket order",
            new java.math.BigDecimal("840.50"), "u4",
            java.util.Arrays.asList(
                new ExactSplit("u1", new java.math.BigDecimal("200.00")),
                new ExactSplit("u2", new java.math.BigDecimal("240.50")),
                new ExactSplit("u3", new java.math.BigDecimal("200.00")),
                new ExactSplit("u4", new java.math.BigDecimal("200.00"))));
        splitwise.addExpense("g1", groceries);
        printExpenseShares(new ExpenseService(), groceries);
        printBalances(splitwise, "g1");

        System.out.println();
        System.out.println("=== Step 5: Validation - non-member participant ===");
        splitwise.addUser("u9", "Deepak (guest)", "deepak@example.com");
        System.out.println("Deepak (u9) is registered but NOT in the flat group.");
        System.out.println("Try adding an expense with Deepak as a participant...");
        try {
            Expense bad = new Expense("e-bad2", "Guest dinner",
                new java.math.BigDecimal("600.00"), "u1",
                java.util.Arrays.asList(new EqualSplit("u1"), new EqualSplit("u9")));
            splitwise.addExpense("g1", bad);
            System.out.println("  UNEXPECTED: non-member expense was accepted!");
        } catch (IllegalArgumentException e) {
            System.out.println("  REJECTED: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== Step 6: Raw pairwise balances (before simplification) ===");
        System.out.println("Each expense created debtor->payer edges, folded against each");
        System.out.println("other. This is the current 'who owes whom' view:");
        List<Transfer> pairwise = splitwise.getPairwiseBalances("g1");
        printTransfers(pairwise);
        System.out.println("  -> " + pairwise.size() + " distinct debtor-creditor pairs.");

        System.out.println();
        System.out.println("=== Step 7: Debt simplification (greedy min-cash-flow) ===");
        System.out.println("Collapse to per-user nets, match largest creditor vs largest");
        System.out.println("debtor with two heaps. Fewest payments that settle the group:");
        List<Transfer> suggestions = splitwise.suggestSettlement("g1");
        printTransfers(suggestions);
        System.out.println("  -> " + suggestions.size() + " payments settle what "
            + pairwise.size() + " pairwise debts would take.");

        System.out.println();
        System.out.println("=== Step 8: The flatmates actually pay (settle up) ===");
        for (Transfer payment : suggestions) {
            splitwise.recordSettlement("g1", payment.getFromUserId(),
                payment.getToUserId(), payment.getAmountInPaise());
            System.out.println("  PAID: " + payment);
        }

        System.out.println();
        System.out.println("=== Step 9: Group is now square (but bookkeeping is not) ===");
        printBalances(splitwise, "g1");
        List<Transfer> after = splitwise.getPairwiseBalances("g1");
        System.out.println("Open pairwise debts: " + after.size());
        if (!after.isEmpty()) {
            System.out.println("  Interesting: every net is zero, yet " + after.size()
                + " pairs remain. The flatmates paid along the SIMPLIFIED");
            System.out.println("  routes, which cross the original debt routes - each");
            System.out.println("  payment was correct, but offsetting cycles survive.");
            System.out.println("  Economically settled; bookkeeping noise remains.");
            printTransfers(after);
        }
        List<Transfer> noMore = splitwise.suggestSettlement("g1");
        System.out.println("Suggested payments now: " + noMore.size()
            + (noMore.isEmpty() ? " (nothing left to settle)" : ""));

        System.out.println();
        System.out.println("=== Step 10: Removing a member with open balances is blocked ===");
        System.out.println("Vikram is moving out. First add one farewell expense so he has");
        System.out.println("an open balance, then try to remove him:");
        Expense farewell = new Expense("e5", "Vikram's farewell cake",
            new java.math.BigDecimal("300.00"), "u1",
            Expense.equalAmong("u1", "u2", "u3", "u4"));
        splitwise.addExpense("g1", farewell);
        try {
            flat.removeMember("u3", splitwise.getBalanceService());
            System.out.println("  UNEXPECTED: removal with open balance was allowed!");
        } catch (IllegalStateException e) {
            System.out.println("  REJECTED: " + e.getMessage());
        }
        System.out.println("  Now settle the farewell expense and remove him again...");
        splitwise.settleUpGroup("g1");
        flat.removeMember("u3", splitwise.getBalanceService());
        System.out.println("  Removed. Group members now: " + flat.getMemberIds());

        System.out.println();
        System.out.println("=== Demo Complete ===");
    }

    // ----------------------------------------------------------- helpers

    /** Prints each participant's computed share for one expense. */
    private static void printExpenseShares(ExpenseService expenseService, Expense expense) {
        Map<String, Long> shares = expenseService.computeShares(expense);
        long sum = 0;
        StringBuilder sb = new StringBuilder("  Shares for '" + expense.getDescription()
            + "' (" + Split.formatRupees(expense.getAmountInPaise()) + ", paid by "
            + expense.getPaidByUserId() + "):");
        for (Map.Entry<String, Long> entry : shares.entrySet()) {
            sb.append("\n    ").append(entry.getKey()).append(" -> ")
                .append(Split.formatRupees(entry.getValue().longValue()));
            sum += entry.getValue().longValue();
        }
        sb.append("\n    (shares sum back to exactly ").append(Split.formatRupees(sum))
            .append(")");
        System.out.println(sb.toString());
    }

    private static void printBalances(SplitwiseService splitwise, String groupId) {
        Map<String, Long> nets = splitwise.getNetBalances(groupId);
        if (nets.isEmpty()) {
            System.out.println("  Net balances: everyone square.");
            return;
        }
        StringBuilder sb = new StringBuilder("  Net balances:");
        for (Map.Entry<String, Long> entry : nets.entrySet()) {
            long net = entry.getValue().longValue();
            String state = net > 0
                ? "gets back " + Split.formatRupees(net)
                : "owes " + Split.formatRupees(-net);
            sb.append("\n    ").append(entry.getKey()).append(": ").append(state);
        }
        System.out.println(sb.toString());
    }

    private static void printTransfers(List<Transfer> transfers) {
        if (transfers.isEmpty()) {
            System.out.println("    (none)");
            return;
        }
        for (Transfer transfer : transfers) {
            System.out.println("    " + transfer);
        }
    }
}
