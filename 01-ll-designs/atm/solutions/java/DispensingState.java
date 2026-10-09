/**
 * The money-moves state: the chosen transaction executes against the bank
 * and, if cash must physically leave the machine, the dispenser pays out.
 *
 * This is the ONLY state that knows how to run a transaction - which is the
 * point of both patterns at once:
 *   - STATE: "executing a transaction" is only legal here, and the machine
 *     physically locks the card/shutter during it, so no other action is
 *     legal while money is moving.
 *   - COMMAND: the transaction runs as an executable object
 *     (WithdrawCommand.execute()), so the ATM stays ignorant of withdrawal
 *     vs deposit vs balance logic - and every execution is appended to the
 *     audit trail with its outcome.
 */
public class DispensingState implements AtmState {

    public static final DispensingState INSTANCE = new DispensingState();

    private DispensingState() {
    }

    @Override
    public String getName() {
        return "Dispensing";
    }

    /**
     * Balance inquiry arrives here directly from TransactionSelectedState
     * (no amount prompt was needed). We simply fall through to the shared
     * execution path with amount = 0.
     */
    @Override
    public AtmState selectTransaction(TransactionType type, Atm atm) {
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        atm.setSelectedTransaction(type);
        atm.setEnteredAmount(0);
        return this;
    }

    @Override
    public AtmState enterAmount(long amount, Atm atm) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "Amount must be positive, got " + amount);
        }
        atm.setEnteredAmount(amount);
        return this;
    }

    /**
     * Executes the pending transaction (from TransactionSelectedState) or the
     * just-chosen balance inquiry (from selectTransaction above), then always
     * ends back in IdleState with the card out.
     */
    @Override
    public AtmState ejectCard(Atm atm) {
        atm.runPendingTransaction();
        atm.releaseCard();
        return IdleState.INSTANCE;
    }
}
