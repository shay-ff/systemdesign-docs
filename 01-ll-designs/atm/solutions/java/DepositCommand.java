import java.util.Map;

/**
 * Cash deposit command. Simpler than withdrawal: no account balance
 * constraint and no dispenser involvement - the money flows the other way.
 * (Real ATMs take envelopes or note-acceptor counts; here the amount is
 * trusted input, exactly like the deposit slot confirming a count.)
 */
public class DepositCommand extends Transaction {

    public DepositCommand(Card card, long amountInPaise) {
        super(card, amountInPaise, TransactionType.DEPOSIT);
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive, got "
                    + Account.formatRupees(amountInPaise));
        }
    }

    @Override
    protected Map<Integer, Integer> runCore(BankBackend bank, CashDispenser ignored) throws AtmException {
        long newBalance = bank.deposit(card.getCardNumber(), amountInPaise);
        System.out.println("  [ATM] Deposited " + Account.formatRupees(amountInPaise)
                + " -> new balance " + Account.formatRupees(newBalance));
        return null; // no cash plan for deposits
    }
}
