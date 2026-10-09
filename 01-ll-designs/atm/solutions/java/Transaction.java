import java.util.Map;

/**
 * COMMAND pattern: a bank transaction as an executable object.
 *
 * WHY commands instead of a switch(type) in the ATM?
 *   1. The ATM's flow code (state machine) stays ignorant of withdraw vs
 *      deposit vs balance semantics - it runs "the command" and gets a
 *      result. Adding a new transaction type (mini-statement, PIN change,
 *      transfer) is a new command class, not a new switch branch.
 *   2. Every execution produces a TransactionRecord (audit trail) with
 *      identical shape - who, what, how much, when, outcome - because the
 *      record creation lives in ONE place: this abstract class's template.
 *      Approved and declined outcomes get the same audit schema.
 *   3. Commands are the natural unit for undo/compensation and retry in the
 *      production story (a failed dispense after a successful debit
 *      triggers an auto-reversal command - see explanation.md).
 *
 * TEMPLATE METHOD: execute() fixes the SKELETON (validate -> run -> record)
 * while subclasses supply only runCore(). Subclasses cannot forget to
 * audit, and declines are captured as records instead of exceptions -
 * "insufficient funds" is a business outcome the machine must display and
 * log, not a crash.
 */
public abstract class Transaction {

    protected final Card card;
    protected final long amountInPaise;
    protected final TransactionType type;

    protected Transaction(Card card, long amountInPaise, TransactionType type) {
        if (card == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        if (amountInPaise < 0) {
            throw new IllegalArgumentException("Amount cannot be negative, got "
                    + Account.formatRupees(amountInPaise));
        }
        this.card = card;
        this.amountInPaise = amountInPaise;
        this.type = type;
    }

    /**
     * Template method: validate -> run -> audit-record. NEVER throws for a
     * business decline: the decline comes back as a TransactionRecord with
     * its AtmResult (INSUFFICIENT_FUNDS, DISPENSER_SHORTAGE, ...) so the
     * caller has exactly one control flow for both outcomes. Programming
     * errors (bad arguments) still throw IllegalArgumentException.
     */
    public final TransactionRecord execute(BankBackend bank, CashDispenser dispenser) {
        validate();
        TransactionRecord record;
        try {
            Map<Integer, Integer> notePlan = runCore(bank, dispenser);
            record = new TransactionRecord(card, type, amountInPaise, AtmResult.APPROVED,
                    notePlan, Thread.currentThread().getName());
        } catch (AtmException e) {
            // The decline path: same audit shape, different result code.
            System.out.println("  [ATM] DECLINED (" + e.getResult().name() + "): "
                    + e.getMessage());
            record = new TransactionRecord(card, type, amountInPaise, e.getResult(),
                    null, Thread.currentThread().getName());
        }
        return record;
    }

    /** Hook: per-command validation beyond the constructor's checks. */
    protected void validate() {
        // Default: constructor already validated. Subclasses add rules.
    }

    /**
     * The actual bank/hardware work. Returns the cash plan for withdrawals
     * (null otherwise). Throws AtmException for business declines.
     */
    protected abstract Map<Integer, Integer> runCore(BankBackend bank,
                                                     CashDispenser dispenser) throws AtmException;

    public TransactionType getType() {
        return type;
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }
}
