import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * ATM demo - the full narrative, six sections:
 *
 *   1. Happy-path withdrawal with a printed receipt (greedy note plan).
 *   2. Wrong PIN twice, then the correct PIN (3-attempt policy shown live).
 *   3. Withdrawal exceeding balance -> clean decline, balance untouched.
 *   4. Denomination shortage (only 2000s left, INR 300 requested) ->
 *      declined BEFORE the account is debited.
 *   5. Two ATMs, one account, two threads -> the race is enforced correctly.
 *   6. Audit trail (one row per attempted transaction, approved + declined).
 *
 * Money is long paise throughout (INR 500.00 = 50000 paise); receipts format
 * it back. Run: `javac *.java && java AtmDemo`.
 */
public class AtmDemo {

    private static final long INR = 100L; // paise per rupee

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ATM Demo (State + Command, Indian denominations) ===\n");

        // ---- The bank and its customers --------------------------------
        InMemoryBankBackend bank = new InMemoryBankBackend();

        Card priyasCard = new Card("4321987654321234", "1234", "ACC-1001");
        Account priyasAccount = new Account("ACC-1001", "Priya Sharma", 25000 * INR);
        bank.register(priyasCard, priyasAccount);

        Card rahulsCard = new Card("5555666677778888", "9999", "ACC-2002");
        Account rahulsAccount = new Account("ACC-2002", "Rahul Verma", 8000 * INR);
        bank.register(rahulsCard, rahulsAccount);

        // ---- Two ATMs share one bank (and one shared account) ----------
        CashDispenser dispenser = new CashDispenser();
        dispenser.stock(2000, 10); // INR 20,000
        dispenser.stock(500, 10);   // INR  5,000
        dispenser.stock(200, 10);   // INR  2,000
        dispenser.stock(100, 10);   // INR  1,000
        Atm koramangala = new Atm("ATM-BLR-01", "Koramangala, Bengaluru", bank, dispenser, 3);

        System.out.println("Accounts: " + priyasAccount + " / " + rahulsAccount);
        System.out.println("Dispenser: " + dispenser.inventorySummary());
        System.out.println("ATM state: " + koramangala.getStateName() + "\n");

        // =================================================================
        System.out.println("=== Section 1: Happy-path withdrawal with receipt ===");
        koramangala.insertCard(priyasCard);
        koramangala.enterPin("1234");
        koramangala.selectTransaction(TransactionType.WITHDRAWAL);
        koramangala.enterAmount(3700 * INR); // INR 3,700 -> 1x2000 + 3x500 + 1x200
        koramangala.ejectCard();            // confirms and executes

        System.out.println("Dispenser after withdrawal: " + dispenser.inventorySummary());
        System.out.println("Account after withdrawal:   " + priyasAccount + "\n");

        // =================================================================
        System.out.println("=== Section 2: Wrong PIN twice, then correct ===");
        koramangala.insertCard(rahulsCard);
        koramangala.enterPin("1111"); // wrong #1
        koramangala.enterPin("2222"); // wrong #2 - one attempt left
        koramangala.enterPin("9999"); // correct - attempts reset for the session
        koramangala.selectTransaction(TransactionType.BALANCE_INQUIRY);
        koramangala.ejectCard(); // balance needs no amount; executes straight away
        System.out.println();

        // =================================================================
        System.out.println("=== Section 3: Withdrawal exceeding balance -> decline ===");
        koramangala.insertCard(rahulsCard);
        koramangala.enterPin("9999");
        koramangala.selectTransaction(TransactionType.WITHDRAWAL);
        koramangala.enterAmount(20000 * INR); // balance only INR 8,000
        koramangala.ejectCard();
        System.out.println("Balance after declined withdrawal (must be unchanged): "
                + rahulsAccount + "\n");

        // =================================================================
        System.out.println("=== Section 4: Denomination shortage (greedy cannot compose) ===");
        // A SECOND machine stocked with ONLY INR 2000 notes: greedy cannot
        // compose INR 300 (2000 does not divide 300 and there is nothing
        // smaller), so it declines. Crucially, the decline fires BEFORE the
        // account is debited - hardware feasibility is checked first.
        CashDispenser starving = new CashDispenser();
        starving.stock(2000, 5); // INR 10,000 in 2000s only
        Atm indiranagar = new Atm("ATM-BLR-02", "Indiranagar, Bengaluru", bank, starving, 3);
        System.out.println("ATM-2 (Indiranagar) dispenser: " + starving.inventorySummary()
                + " - a 2000-only machine cannot pay INR 300");
        indiranagar.insertCard(priyasCard);
        indiranagar.enterPin("1234");
        indiranagar.selectTransaction(TransactionType.WITHDRAWAL);
        indiranagar.enterAmount(300 * INR);
        indiranagar.ejectCard();
        System.out.println("Priya's balance after the shortage decline (must be unchanged): "
                + priyasAccount);
        System.out.println("ATM-2 dispenser (notes untouched by the declined attempt): "
                + starving.inventorySummary());
        System.out.println("-> The account was NEVER debited: the dispenser dry-run "
                + "ran before the bank call.\n");

        // =================================================================
        System.out.println("=== Section 5: Two ATMs, one account, two threads (the race) ===");
        // Shared account with exactly INR 5,000. Both ATMs try to withdraw
        // INR 4,000 AT THE SAME TIME. Without a per-account lock both would
        // pass the balance check against a stale read and the account would
        // go to -3,000. With Account.tryDebit's lock exactly one succeeds.
        Account jointAccount = new Account("ACC-3003", "Joint: Meera & Arjun", 5000 * INR);
        Card meerasCard = new Card("1111222233334444", "4321", "ACC-3003");
        bank.register(meerasCard, jointAccount);
        System.out.println("Joint account: " + jointAccount
                + " - both ATMs will race to withdraw INR 4,000");

        Atm atmA = new Atm("ATM-RACE-A", "MG Road, Bengaluru", bank, dispenser, 3);
        Atm atmB = new Atm("ATM-RACE-B", "HSR Layout, Bengaluru", bank, dispenser, 3);

        // Park both sessions in TransactionSelectedState BEFORE the race so
        // the only contested step is the withdraw/debit itself. (atmA/atmB
        // hold their own session locks; the ACCOUNT lock is the shared one.)
        atmA.insertCard(meerasCard);
        atmA.enterPin("4321");
        atmA.selectTransaction(TransactionType.WITHDRAWAL);
        atmA.enterAmount(4000 * INR);

        atmB.insertCard(meerasCard);
        atmB.enterPin("4321");
        atmB.selectTransaction(TransactionType.WITHDRAWAL);
        atmB.enterAmount(4000 * INR);

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch go = new CountDownLatch(1);

        Thread threadA = new Thread(() -> runRacedWithdrawal(atmA, ready, go), "ATM-A-thread");
        Thread threadB = new Thread(() -> runRacedWithdrawal(atmB, ready, go), "ATM-B-thread");
        threadA.start();
        threadB.start();
        // Both worker threads park on `go` so the two debits are released
        // together - that is the whole point: a genuine simultaneous hit.
        ready.await();
        System.out.println("  [DEMO] Both ATMs armed - releasing the race...");
        go.countDown();
        threadA.join();
        threadB.join();

        System.out.println("\nAfter the race: " + jointAccount);
        System.out.println("Exactly one withdrawal must have succeeded "
                + "(balance INR 1,000 = 5,000 - 4,000; never negative).");
        System.out.println("Note: the losing ATM's decline is an INSUFFICIENT_FUNDS row "
                + "in the audit trail below - the account lock serialized the two "
                + "check-then-debit sections.\n");

        // =================================================================
        System.out.println("=== Section 6: Audit trail (all attempts, approved + declined) ===");
        List<TransactionRecord> trail = koramangala.getAuditTrail();
        // Both racing ATMs funnel their records into the shared demo trail:
        // atmA/atmB have their own trails; merge them for the final view.
        trail.addAll(atmA.getAuditTrail());
        trail.addAll(atmB.getAuditTrail());
        System.out.println("Koramangala + race ATMs - " + trail.size() + " record(s):");
        for (TransactionRecord record : trail) {
            System.out.println("  " + record.auditLine());
        }

        System.out.println("\n=== End of ATM demo ===");
    }

    /**
     * The raced withdrawal: both threads park on the `go` latch; the demo's
     * main thread releases them together, and the ATTEMPT (not the whole
     * session) is what runs concurrently - the debit is the contested
     * critical section on the shared Account's lock.
     */
    private static void runRacedWithdrawal(Atm atm, CountDownLatch ready,
                                           CountDownLatch go) {
        ready.countDown(); // tell main: this thread is armed
        try {
            go.await();     // both threads released together
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // ejectCard in DispensingState executes the pending withdrawal.
        atm.ejectCard();
    }
}
