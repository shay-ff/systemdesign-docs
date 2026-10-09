/**
 * PIN is verified: the customer now sees the transaction menu. This is the
 * "authenticated but nothing chosen yet" state.
 *
 * Note what does NOT live here: any account validation. Authentication
 * (who you are: the PIN) is done; authorization/lookup (what you may do:
 * account state) happens when a transaction executes. Keeping the two
 * separate is what lets the demo show "PIN correct but account not found"
 * as a distinct, later failure.
 */
public class PinVerifiedState implements AtmState {

    public static final PinVerifiedState INSTANCE = new PinVerifiedState();

    private PinVerifiedState() {
    }

    @Override
    public String getName() {
        return "PinVerified";
    }

    @Override
    public AtmState selectTransaction(TransactionType type, Atm atm) {
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        atm.setSelectedTransaction(type);
        System.out.println("  [ATM] Transaction selected: " + type);
        return TransactionSelectedState.INSTANCE;
    }

    @Override
    public AtmState ejectCard(Atm atm) {
        atm.releaseCard();
        System.out.println("  [ATM] Card ejected -> back to Idle");
        return IdleState.INSTANCE;
    }
}
