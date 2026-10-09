import java.util.Map;

/**
 * Balance inquiry command - the read-only transaction. Exists mostly to show
 * the command set scaling with zero state-machine changes: adding this
 * class (or a MiniStatementCommand) requires no edits to Atm or any state.
 */
public class BalanceInquiryCommand extends Transaction {

    public BalanceInquiryCommand(Card card) {
        super(card, 0, TransactionType.BALANCE_INQUIRY);
    }

    @Override
    protected Map<Integer, Integer> runCore(BankBackend bank, CashDispenser ignored) throws AtmException {
        Account account = bank.fetchAccount(card.getCardNumber());
        System.out.println("  [ATM] Balance for account " + account.getAccountId()
                + ": " + Account.formatRupees(account.getBalance()));
        return null;
    }
}
