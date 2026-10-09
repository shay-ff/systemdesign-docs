/**
 * The boundary between the ATM (hardware + UI) and the bank (money + truth).
 *
 * WHY an interface at all - the single most important modelling decision in
 * this problem: an ATM is a dumb terminal. It does NOT own account data or
 * PIN records; it asks the bank over a network. Real ATMs speak ISO 8583
 * (the international card-originated message standard: 0200 financial
 * request / 0210 response, with the response code telling the machine
 * "approved / insufficient funds / card captured") over a host link to the
 * bank's switch. Interviewers love hearing that the ATM is a client and the
 * bank is the source of truth - and the interface IS that boundary.
 *
 * Side benefit: tests and the demo get an InMemoryBankBackend while the real
 * deployment gets a socket-speaking implementation - zero ATM changes
 * (dependency inversion in one line).
 */
public interface BankBackend {

    /**
     * Verifies a card's PIN against the bank's records. Returns the linked
     * account's current balance on success. Returning the balance here (and
     * not via a second lookup) mirrors the real ISO 8583 0210 response,
     * which carries both the auth result and the available amount.
     */
    long verifyPinAndFetchBalance(String cardNumber, String pin) throws AtmException;

    /** Fetches the account object linked to a card (for transaction execution). */
    Account fetchAccount(String cardNumber) throws AtmException;

    /** Runs a withdrawal against the account; returns remaining balance. */
    long withdraw(String cardNumber, long amountInPaise) throws AtmException;

    /** Runs a deposit against the account; returns remaining balance. */
    long deposit(String cardNumber, long amountInPaise) throws AtmException;

    /** Marks a card permanently blocked (e.g., after confiscation). */
    void blockCard(String cardNumber);
}
