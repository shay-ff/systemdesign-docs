import java.util.Map;

/**
 * Cash withdrawal command - the most involved transaction, because TWO
 * resources must both succeed: the account (debit) and the cash box
 * (dispense). Order matters:
 *
 *   1. Check the dispenser can compose the amount (dry-run, no mutation).
 *   2. Debit the account (the bank's atomic tryDebit).
 *   3. Dispense (committed plan from step 1's twin).
 *
 * WHY check the dispenser FIRST - the crux of the flow's correctness: if we
 * debited first and the dispenser then could not compose the amount, we
 * would owe the customer an auto-reversal (and in a distributed setting,
 * a saga compensation). Checking hardware feasibility before touching
 * money eliminates the most common compensation path. Whatever remains -
 * debit succeeds, dispense panics (machine jam) - is the production
 * compensation story, discussed in explanation.md.
 */
public class WithdrawCommand extends Transaction {

    private final CashDispenser dispenser;

    public WithdrawCommand(Card card, long amountInPaise, CashDispenser dispenser) {
        super(card, amountInPaise, TransactionType.WITHDRAWAL);
        if (dispenser == null) {
            throw new IllegalArgumentException("Cash dispenser cannot be null");
        }
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive, got "
                    + Account.formatRupees(amountInPaise));
        }
        this.dispenser = dispenser;
    }

    @Override
    protected void validate() {
        if (amountInPaise % 100 != 0) {
            // Validate BEFORE touching the bank: a malformed request should
            // never even reach the switch (real ATMs enforce this on the pad).
            throw new IllegalArgumentException("Withdrawal amount "
                    + Account.formatRupees(amountInPaise)
                    + " must be a multiple of INR 100 (smallest note)");
        }
    }

    @Override
    protected Map<Integer, Integer> runCore(BankBackend bank, CashDispenser ignored) throws AtmException {
        // 1. Hardware feasibility FIRST (dry-run compose; no notes committed).
        if (!dispenser.canDispense(amountInPaise)) {
            throw new AtmException(AtmResult.DISPENSER_SHORTAGE,
                    "This ATM cannot dispense " + Account.formatRupees(amountInPaise)
                            + " with current denominations: " + dispenser.inventorySummary());
        }
        // 2. Account debit (atomic check-then-debit inside Account.tryDebit).
        bank.withdraw(card.getCardNumber(), amountInPaise);
        // 3. Commit the cash plan.
        return dispenser.dispense(amountInPaise);
    }
}
