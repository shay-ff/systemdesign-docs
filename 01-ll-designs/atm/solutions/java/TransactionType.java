/**
 * Transaction menu options. An enum (not a class hierarchy) because these
 * are data, not behaviour: the behaviour differences between withdraw /
 * deposit / balance live in the command objects, one per type.
 */
public enum TransactionType {
    WITHDRAWAL("Cash Withdrawal"),
    DEPOSIT("Cash Deposit"),
    BALANCE_INQUIRY("Balance Inquiry");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
