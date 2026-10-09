/**
 * The ATM is waiting for a customer: no card, screen showing the welcome
 * message, PIN pad asleep.
 *
 * Only ONE action is legal here - insertCard. Everything else hits the
 * interface's default illegal() (try entering a PIN on a machine with no
 * card in it: "not allowed in state Idle").
 */
public class IdleState implements AtmState {

    public static final IdleState INSTANCE = new IdleState();

    private IdleState() {
        // Stateless - one shared instance is enough. (Flyweight-lite.)
    }

    @Override
    public String getName() {
        return "Idle";
    }

    @Override
    public AtmState insertCard(Card card, Atm atm) {
        // The card's own logic (blocked check) runs inside CardInsertedState;
        // here we only validate that the ATM can read a card at all.
        if (card == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        atm.setCard(card);
        System.out.println("  [ATM] Card " + card.getCardNumber()
                + " inserted -> waiting for PIN");
        return CardInsertedState.INSTANCE;
    }
}
