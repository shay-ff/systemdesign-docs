import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * THE ATM CONTEXT - the heart of the state pattern.
 *
 * The Atm object is the machine: card reader, PIN pad, cash dispenser,
 * receipt printer, and the CURRENT STATE pointer. Every user action is
 * delegated to `state.action(this, ...)`, and the state hands back the next
 * state. The Atm never asks "am I in withdrawal mode?" - it does not know
 * what its states mean. That ignorance IS the pattern: behaviour lives in
 * state objects; the context is a dumb pointer-holder.
 *
 * WHY states are singletons (stateless) and the per-session data (card, pin
 * attempts, selected transaction, amount) lives HERE: two ATMs share
 * IdleState.INSTANCE safely because nothing session-specific is stored on
 * the state. The classic alternative (each state holds its card) forces a
 * new state object per transition - fine too, but the shared-instance form
 * makes the statelessness explicit and the GC pressure zero. Session state
 * on the context is also what makes the two-ATM concurrency demo work: two
 * Atm objects, one shared IdleState, no crosstalk.
 *
 * CONCURRENCY SCOPE: one Atm object = one physical terminal; its own state
 * transitions are guarded by a ReentrantLock so a second customer's key
 * presses cannot interleave mid-transaction. The MONEY race (two ATMs, one
 * account) is guarded elsewhere - on the Account itself - which is the
 * whole point: the lock lives with the shared resource.
 */
public class Atm {

    private final String terminalId;
    private final String location;
    private final BankBackend bank;
    private final CashDispenser dispenser;
    private final ReceiptPrinter printer;
    private final int maxPinAttempts;

    // ---- session state (guarded by sessionLock) --------------------------
    private final ReentrantLock sessionLock = new ReentrantLock();
    private AtmState state = IdleState.INSTANCE;
    private Card card;
    private int pinAttempts;
    private TransactionType selectedTransaction;
    private long enteredAmount;
    private final List<TransactionRecord> auditTrail =
            Collections.synchronizedList(new ArrayList<>());

    public Atm(String terminalId, String location, BankBackend bank,
               CashDispenser dispenser, int maxPinAttempts) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            throw new IllegalArgumentException("Terminal id cannot be null or empty");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Location cannot be null or empty");
        }
        if (bank == null) {
            throw new IllegalArgumentException("Bank backend cannot be null");
        }
        if (dispenser == null) {
            throw new IllegalArgumentException("Cash dispenser cannot be null");
        }
        if (maxPinAttempts <= 0) {
            throw new IllegalArgumentException("Max PIN attempts must be positive, got "
                    + maxPinAttempts);
        }
        this.terminalId = terminalId;
        this.location = location;
        this.bank = bank;
        this.dispenser = dispenser;
        this.printer = new ReceiptPrinter();
        this.maxPinAttempts = maxPinAttempts;
    }

    // ------------------------------------------------------------ user API

    /** Customer inserts a card. */
    public void insertCard(Card aCard) {
        sessionLock.lock();
        try {
            state = state.insertCard(aCard, this);
        } finally {
            sessionLock.unlock();
        }
    }

    /** Customer types a PIN. */
    public void enterPin(String pin) {
        sessionLock.lock();
        try {
            state = state.enterPin(pin, this);
        } finally {
            sessionLock.unlock();
        }
    }

    /** Customer picks a transaction from the menu. */
    public void selectTransaction(TransactionType type) {
        sessionLock.lock();
        try {
            state = state.selectTransaction(type, this);
        } finally {
            sessionLock.unlock();
        }
    }

    /** Customer keys in an amount (or the demo drives a balance inquiry). */
    public void enterAmount(long amount) {
        sessionLock.lock();
        try {
            state = state.enterAmount(amount, this);
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * Card comes out. In DispensingState this doubles as "confirm and
     * execute" (the demo's driver: amount entered -> press eject -> the
     * transaction runs and the card returns).
     */
    public void ejectCard() {
        sessionLock.lock();
        try {
            state = state.ejectCard(this);
        } finally {
            sessionLock.unlock();
        }
    }

    // -------------------------------------------------------- state hooks

    // Package-private: only the state classes (same package) touch these,
    // so the session fields cannot be corrupted by demo/application code.

    void setCard(Card aCard) {
        this.card = aCard;
        this.pinAttempts = 0;
    }

    Card getCard() {
        return card;
    }

    void incrementPinAttempts() {
        pinAttempts++;
    }

    void resetPinAttempts() {
        pinAttempts = 0;
    }

    int getPinAttempts() {
        return pinAttempts;
    }

    int getMaxPinAttempts() {
        return maxPinAttempts;
    }

    void setSelectedTransaction(TransactionType type) {
        this.selectedTransaction = type;
    }

    void setEnteredAmount(long amount) {
        this.enteredAmount = amount;
    }

    /**
     * Confiscation: the machine KEEPS the card (ATMs physically retain it)
     * and asks the bank to block it permanently. Two decisions, one action -
     * the physical retention is the ATM's; the block flag is the bank's
     * single source of truth so every other channel sees it too.
     */
    void confiscateCard() {
        System.out.println("  [ATM] Card " + card.getCardNumber() + " RETAINED by terminal "
                + terminalId);
        bank.blockCard(card.getCardNumber());
        this.card = null;
        this.pinAttempts = 0;
    }

    /** Card leaves the machine voluntarily. */
    void releaseCard() {
        this.card = null;
        this.pinAttempts = 0;
        this.selectedTransaction = null;
        this.enteredAmount = 0;
    }

    /**
     * Builds and runs the pending transaction as a COMMAND, records it in
     * the audit trail (approved or declined - the command's template method
     * returns a record for BOTH outcomes), and prints the receipt.
     *
     * The switch here is the ONLY place the concrete commands are named -
     * and it is a factory switch, not behaviour. New transaction types plug
     * in as: one enum constant + one command class + one case line; the
     * states, the bank, and the audit schema never change.
     */
    void runPendingTransaction() {
        if (card == null || selectedTransaction == null) {
            throw new IllegalStateException(
                "No pending transaction to run (state machine misuse)");
        }
        Transaction command;
        switch (selectedTransaction) {
            case WITHDRAWAL:
                command = new WithdrawCommand(card, enteredAmount, dispenser);
                break;
            case DEPOSIT:
                command = new DepositCommand(card, enteredAmount);
                break;
            case BALANCE_INQUIRY:
                command = new BalanceInquiryCommand(card);
                break;
            default:
                throw new IllegalStateException("Unsupported transaction type: "
                        + selectedTransaction);
        }
        TransactionRecord record = command.execute(bank, dispenser);
        auditTrail.add(record);
        long balanceAfter = currentBalanceFor(card);
        System.out.println("  [ATM] Receipt:\n"
                + printer.print(this, record, record.getNotePlan(), balanceAfter));
        selectedTransaction = null;
        enteredAmount = 0;
    }

    // -------------------------------------------------------------- helpers

    private long currentBalanceFor(Card aCard) {
        try {
            return bank.fetchAccount(aCard.getCardNumber()).getBalance();
        } catch (AtmException e) {
            return -1; // account unreachable - receipt shows a dash in production
        }
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getLocation() {
        return location;
    }

    public String getStateName() {
        sessionLock.lock();
        try {
            return state.getName();
        } finally {
            sessionLock.unlock();
        }
    }

    public List<TransactionRecord> getAuditTrail() {
        return auditTrail;
    }

    public CashDispenser getDispenser() {
        return dispenser;
    }
}
