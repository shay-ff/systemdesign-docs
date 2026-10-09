/**
 * ISO 8583-flavoured outcome codes for every ATM transaction. Real switch
 * response codes: 00 approved, 51 insufficient funds, 55 incorrect PIN,
 * 41 pick-up (capture the card). We use readable enum constants instead of
 * two-digit codes - the mapping is one `code` field away if the interviewer
 * wants domain flavour.
 */
public enum AtmResult {
    APPROVED("Approved"),
    INSUFFICIENT_FUNDS("Insufficient funds"),
    INVALID_PIN("Incorrect PIN"),
    CARD_BLOCKED("Card blocked"),
    CARD_CAPTURED("Card captured"),
    DISPENSER_SHORTAGE("Cash unavailable in required denominations"),
    INVALID_AMOUNT("Invalid amount");

    private final String label;

    AtmResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return name() + " (" + label + ")";
    }
}
