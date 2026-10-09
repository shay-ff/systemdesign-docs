import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for the real bank host (the switch the ATM dials into).
 *
 * A production ATM talks to the bank's switch over a network protocol -
 * ISO 8583 is the classic: a fixed-with bitmap-framed message where 0200 is
 * a financial request and the 0210 reply carries a response code (00 approve,
 * 51 insufficient funds, 55 bad PIN, 41 capture the card). This class exposes
 * the same decisions as Java methods so the state machine and demo can be
 * exercised offline. Swapping it for a socket/JSON implementation changes
 * nothing above the BankBackend interface - which is the entire point of
 * drawing that line.
 */
public class InMemoryBankBackend implements BankBackend {

    private final Map<String, Card> cards = new ConcurrentHashMap<>();
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    /** Registers a card and its account with the mock bank. */
    public void register(Card card, Account account) {
        if (card == null || account == null) {
            throw new IllegalArgumentException("Card and account cannot be null");
        }
        if (!card.getLinkedAccountId().equals(account.getAccountId())) {
            throw new IllegalArgumentException("Card " + card.getCardNumber()
                    + " links to account " + card.getLinkedAccountId()
                    + " but was registered with account " + account.getAccountId());
        }
        cards.put(card.getCardNumber(), card);
        accounts.put(account.getAccountId(), account);
    }

    @Override
    public long verifyPinAndFetchBalance(String cardNumber, String pin) throws AtmException {
        Card card = lookupCard(cardNumber);
        if (card.isBlocked()) {
            throw new AtmException(AtmResult.CARD_BLOCKED,
                    "Card " + mask(cardNumber) + " is blocked by the bank");
        }
        if (!card.matchesPin(pin)) {
            throw new AtmException(AtmResult.INVALID_PIN, "Incorrect PIN");
        }
        return accountFor(card).getBalance();
    }

    @Override
    public Account fetchAccount(String cardNumber) throws AtmException {
        Card card = lookupCard(cardNumber);
        if (card.isBlocked()) {
            throw new AtmException(AtmResult.CARD_BLOCKED,
                    "Card " + mask(cardNumber) + " is blocked by the bank");
        }
        return accountFor(card);
    }

    @Override
    public long withdraw(String cardNumber, long amountInPaise) throws AtmException {
        Account account = fetchAccount(cardNumber);
        if (!account.tryDebit(amountInPaise)) {
            // "Insufficient funds" is a DECLINED business outcome (ISO 51),
            // not a system error - the account balance stays untouched.
            throw new AtmException(AtmResult.INSUFFICIENT_FUNDS,
                    "Insufficient funds in account " + account.getAccountId()
                            + ": balance " + Account.formatRupees(account.getBalance())
                            + ", requested " + Account.formatRupees(amountInPaise));
        }
        return account.getBalance();
    }

    @Override
    public long deposit(String cardNumber, long amountInPaise) throws AtmException {
        Account account = fetchAccount(cardNumber);
        account.credit(amountInPaise);
        return account.getBalance();
    }

    @Override
    public void blockCard(String cardNumber) {
        Card card = cards.get(cardNumber);
        if (card != null) {
            card.block();
        }
    }

    // -------------------------------------------------------------- internals

    private Card lookupCard(String cardNumber) throws AtmException {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }
        Card card = cards.get(cardNumber);
        if (card == null) {
            // Unknown card: the classic "pick up" scenario in ISO terms.
            throw new AtmException(AtmResult.CARD_CAPTURED,
                    "Card " + mask(cardNumber) + " is not issued by this bank");
        }
        return card;
    }

    private Account accountFor(Card card) throws AtmException {
        Account account = accounts.get(card.getLinkedAccountId());
        if (account == null) {
            throw new AtmException(AtmResult.CARD_CAPTURED,
                    "No account found for card " + mask(card.getCardNumber()));
        }
        return account;
    }

    private String mask(String cardNumber) {
        return "****" + (cardNumber == null || cardNumber.length() < 4
                ? "????" : cardNumber.substring(cardNumber.length() - 4));
    }
}
