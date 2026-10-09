/**
 * The machine's states (State pattern). One constant per legal posture:
 *
 *   IDLE          no transaction; accepting money starts one
 *   HAS_MONEY     money inserted; accepting product selection
 *                 (the "waiting for choice" state)
 *   DISPENSING    sale committed; release item + change; returns to IDLE
 *
 * (REFUNDING and OUT_OF_STOCK are EVENTS handled inside HAS_MONEY, not
 * states: a refund takes ~0 time — the machine returns inserted money and
 * is instantly IDLE again; "out of stock" is a property of an inventory
 * query at selection time, not a posture the machine rests in. Fewer
 * states = fewer illegal transitions to guard — the same discipline as the
 * elevator's "no DOORS_CLOSED state" call.)
 *
 * The states are stateless singletons (pure deciders): every session fact
 * (inserted money, selected product) lives on the VendingMachine context.
 * Stateless states are trivially shareable and make the transition table
 * inspectable — you can print it from the constants themselves.
 */
public enum VendingState {
    IDLE,
    HAS_MONEY,
    DISPENSING;
}
