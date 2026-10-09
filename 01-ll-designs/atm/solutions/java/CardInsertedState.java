/**
 * Card is in the machine but not yet verified: the PIN prompt state.
 *
 * THE edge case that lives here: PIN attempts. Real ATMs allow 3 tries, then
 * confiscate the card (a physical security decision - a thief should not be
 * able to keep guessing with the owner's card). We track attempts on the ATM
 * because the card leaves with the customer; per-session attempt counting is
 * exactly how the hardware behaves.
 *
 * Why is the attempt counter on Atm and not on Card? Because the LIMIT is a
 * property of the session, not of the card's life. A wrong PIN at THIS machine
 * three times -> confiscation here. The card's BLOCKED flag is a separate,
 * permanent outcome set by the bank (see Card.isBlocked()).
 */
public class CardInsertedState implements AtmState {

    public static final CardInsertedState INSTANCE = new CardInsertedState();

    private CardInsertedState() {
    }

    @Override
    public String getName() {
        return "CardInserted";
    }

    @Override
    public AtmState enterPin(String pin, Atm atm) {
        if (pin == null || pin.trim().isEmpty()) {
            throw new IllegalArgumentException("PIN cannot be null or empty");
        }
        Card card = atm.getCard(); // guaranteed non-null: this state is only reachable with a card in
        atm.incrementPinAttempts();

        if (card.isBlocked()) {
            // A card blocked by the bank is rejected at entry, not at PIN time;
            // but re-check here anyway - the bank may have blocked it between
            // insert and PIN (race with a phone banking block, say).
            System.out.println("  [ATM] Card " + card.getCardNumber()
                    + " is BLOCKED by the bank -> card confiscated");
            atm.confiscateCard();
            return IdleState.INSTANCE;
        }

        if (!card.matchesPin(pin)) {
            int remaining = atm.getMaxPinAttempts() - atm.getPinAttempts();
            System.out.println("  [ATM] Wrong PIN (" + atm.getPinAttempts()
                    + "/" + atm.getMaxPinAttempts() + " attempts used)");
            if (remaining <= 0) {
                System.out.println("  [ATM] PIN attempts exhausted -> CARD CONFISCATED "
                        + "(3 wrong entries: card retained for the customer's protection)");
                atm.confiscateCard();
                return IdleState.INSTANCE;
            }
            return this; // stay in PIN prompt, attempts preserved
        }

        // Correct PIN - reset the counter so the next session starts fresh.
        atm.resetPinAttempts();
        System.out.println("  [ATM] PIN verified for card ****"
                + card.last4() + " -> choose a transaction");
        return PinVerifiedState.INSTANCE;
    }

    @Override
    public AtmState ejectCard(Atm atm) {
        atm.releaseCard();
        System.out.println("  [ATM] Card ejected -> back to Idle");
        return IdleState.INSTANCE;
    }
}
