import java.util.concurrent.locks.ReentrantLock;

/**
 * The money: one bank account, identified by an account id.
 *
 * CONCURRENCY - THE CRUX OF THIS PROBLEM (read this comment twice):
 * Two ATMs (or an ATM + UPI app) can hit the same account at the same
 * instant. The dangerous code is check-then-act:
 *
 *     if (account.getBalance() >= amount) { account.debit(amount); }   // RACE
 *
 * Interleaved, both ATMs pass the check against a stale balance, both
 * debit, and the account goes negative. The fix is to make
 * read-check-write ONE atomic section, and the lock must live ON THE
 * ACCOUNT (the shared resource), not on the ATM (each ATM has its own
 * monitor - synchronizing there guards nothing).
 *
 * Lock granularity - the interviewer's follow-up:
 *   - synchronized(this) on the account object: simplest, guards exactly
 *     this account, no interference between unrelated accounts. This is
 *     what we implement (a ReentrantLock for the same semantics plus a
 *     tryLock-based demo path).
 *   - One global bank lock: serializes ALL customers - a throughput
 *     disaster; name it and reject it out loud.
 *   - A striped lock pool (N locks, lock = hash(accountId) % N): the
 *     production answer when accounts are short-lived or lock identity is
 *     awkward; ~N-way parallelism, tiny memory.
 *   - Optimistic (CAS on a version field) or a DB row lock / UPDATE ...
 *     WHERE balance >= amount: the real-bank answer - the invariant moves
 *     into the storage layer.
 */
public class Account {
    private final String accountId;
    private final String holderName;
    private long balanceInPaise;
    private final ReentrantLock lock = new ReentrantLock();

    public Account(String accountId, String holderName, long openingBalanceInPaise) {
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account id cannot be null or empty");
        }
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Holder name cannot be null or empty");
        }
        if (openingBalanceInPaise < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative, got "
                    + formatRupees(openingBalanceInPaise));
        }
        this.accountId = accountId;
        this.holderName = holderName;
        this.balanceInPaise = openingBalanceInPaise;
    }

    /**
     * Atomically debits the account if and only if the balance covers the
     * amount. Returns true on success, false when funds are insufficient -
     * NOT an exception, because "you don't have that much money" is a normal
     * business outcome the ATM must display, not a system fault.
     */
    public boolean tryDebit(long amountInPaise) {
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive, got "
                    + formatRupees(amountInPaise));
        }
        lock.lock();
        try {
            if (balanceInPaise >= amountInPaise) {
                balanceInPaise -= amountInPaise;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** Atomically credits the account. */
    public void credit(long amountInPaise) {
        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive, got "
                    + formatRupees(amountInPaise));
        }
        lock.lock();
        try {
            balanceInPaise += amountInPaise;
        } finally {
            lock.unlock();
        }
    }

    /** Point-in-time balance read (atomically consistent with the writes). */
    public long getBalance() {
        lock.lock();
        try {
            return balanceInPaise;
        } finally {
            lock.unlock();
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    ReentrantLock getLock() {
        return lock; // package-private: used by the demo's two-ATM race section
    }

    /** INR formatting used across the demo and receipts (paise -> rupees). */
    public static String formatRupees(long paise) {
        return String.format("INR %d.%02d", paise / 100, Math.abs(paise % 100));
    }

    @Override
    public String toString() {
        return "Account[" + accountId + " (" + holderName + "), balance "
                + formatRupees(balanceInPaise) + "]";
    }
}
