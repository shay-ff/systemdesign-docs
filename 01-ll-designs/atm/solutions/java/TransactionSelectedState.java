/**
 * A transaction type is chosen; the machine is asking for the amount.
 *
 * Balance inquiry skips this state entirely (no amount needed) - it is the
 * one transaction that can execute straight from selection, so we handle it
 * here and jump directly to DispensingState's completion path. Withdrawals
 * and deposits collect an amount first.
 */
public class TransactionSelectedState implements AtmState {

    public static final TransactionSelectedState INSTANCE = new TransactionSelectedState();

    private TransactionSelectedState() {
    }

    @Override
    public String getName() {
        return "TransactionSelected";
    }

    @Override
    public AtmState enterAmount(long amount, Atm atm) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "Amount must be positive, got " + amount);
        }
        atm.setEnteredAmount(amount);
        return DispensingState.INSTANCE;
    }

    @Override
    public AtmState ejectCard(Atm atm) {
        atm.releaseCard();
        System.out.println("  [ATM] Card ejected -> back to Idle");
        return IdleState.INSTANCE;
    }
}
