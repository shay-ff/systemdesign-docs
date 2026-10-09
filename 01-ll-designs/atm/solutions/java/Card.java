/**
 * A payment card: the physical key to an account.
 *
 * WHY a Card exists separate from Account: a card is a PHYSICAL, stealable,
 * blockable, confiscatable token; an account is the money. One account can
 * have several cards (primary + add-on), one card normally maps to one
 * account, cards expire and get replaced while the account lives on. Merging
 * them would force the ATM to care about card security policy (PIN, blocked
 * flag) while doing account math - two reasons to change one class.
 *
 * The card holds its PIN hash-equivalent (here: the PIN itself, demo-only -
 * say out loud in an interview that a real card's chip holds a derived key
 * and the PIN never travels; this is an in-memory stand-in).
 */
public class Card {
    private final String cardNumber;
    private final String pin;
    private final String linkedAccountId;
    private boolean blocked;

    public Card(String cardNumber, String pin, String linkedAccountId) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }
        if (pin == null || pin.trim().isEmpty() || pin.length() != 4) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits, got '"
                    + pin + "'");
        }
        if (!pin.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("PIN must be digits only, got '" + pin + "'");
        }
        if (linkedAccountId == null || linkedAccountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Linked account id cannot be null or empty");
        }
        this.cardNumber = cardNumber;
        this.pin = pin;
        this.linkedAccountId = linkedAccountId;
        this.blocked = false;
    }

    /** Constant-time-in-spirit comparison: no early exit on mismatch. */
    public boolean matchesPin(String candidate) {
        if (candidate == null || candidate.length() != pin.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < pin.length(); i++) {
            diff |= pin.charAt(i) ^ candidate.charAt(i);
        }
        return diff == 0;
    }

    public void block() {
        this.blocked = true;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    /** Masked form for logs/receipts: only the last 4 digits ever printed. */
    public String last4() {
        return cardNumber.length() <= 4
                ? cardNumber
                : cardNumber.substring(cardNumber.length() - 4);
    }

    public String getLinkedAccountId() {
        return linkedAccountId;
    }

    @Override
    public String toString() {
        return "Card[****" + last4() + " -> account " + linkedAccountId
                + (blocked ? ", BLOCKED" : "") + "]";
    }
}
