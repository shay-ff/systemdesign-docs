/**
 * State pattern contract: every interaction a user can have with the ATM
 * hardware is a state transition hook.
 *
 * WHY a state interface instead of a state enum + if/else ladder in Atm?
 * A real ATM has ~8 possible user actions and 5+ states; an if/else matrix is
 * 40 branches that grows quadratically with each new state or action. With the
 * state pattern each state class answers only the questions it can answer and
 * everything else falls through to a shared "not allowed here" DEFAULT - adding
 * DispensingState or a MaintenanceState later touches ONE new class, not the
 * whole matrix (Open/Closed Principle).
 *
 * Note the defaults: every hook has a default implementation that rejects the
 * action with a clear message ("enterPin is not allowed in state Idle").
 * Concrete states therefore override ONLY their legal hooks - a three-line
 * IdleState instead of five methods of boilerplate. Illegal actions become a
 * NullPointerException-free, message-carrying IllegalStateException.
 *
 * Design note: transitions RETURN the next state rather than mutating the ATM
 * from inside the state. The states stay pure deciders; the Atm context owns
 * its current state - one source of truth, trivially testable.
 */
public interface AtmState {

    /** What the ATM should display / do next (for the narrative demo). */
    String getName();

    /** User inserts a card. Legal from Idle. */
    default AtmState insertCard(Card card, Atm atm) {
        return illegal("insertCard", atm);
    }

    /** User types a PIN. Only legal while a card is pending verification. */
    default AtmState enterPin(String pin, Atm atm) {
        return illegal("enterPin", atm);
    }

    /** User picks a transaction type (withdraw / deposit / balance). */
    default AtmState selectTransaction(TransactionType type, Atm atm) {
        return illegal("selectTransaction", atm);
    }

    /** Amount entry for the selected transaction. */
    default AtmState enterAmount(long amount, Atm atm) {
        return illegal("enterAmount", atm);
    }

    /** Card comes out (eject / confiscation are both handled here). */
    default AtmState ejectCard(Atm atm) {
        return illegal("ejectCard", atm);
    }

    /** The shared rejection: keeps its message state-aware. */
    default AtmState illegal(String action, Atm atm) {
        throw new IllegalStateException(
            "Action '" + action + "' is not allowed in state " + getName());
    }
}
