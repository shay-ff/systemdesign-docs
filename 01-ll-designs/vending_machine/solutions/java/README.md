# Vending Machine — Java Implementation

Java 11, no external libraries, no `package` declarations (repo convention: one top-level class per file, compiled side by side).

## Design Patterns

- **State** — `VendingMachine` + `VendingState`: three postures (IDLE, HAS_MONEY, DISPENSING), one inline transition table guarded by `requireState`. Deliberately enum-states instead of state-classes (unlike the ATM sibling): three states and four actions do not earn five files of ceremony — the interview point survives either way (name the states, name the events, argue why REFUNDING/OUT_OF_STOCK are events, not states).
- **Plan-then-commit** — `ChangePlan` is a reservation over the reserves: `planChange` dry-runs, `dispenseChange` commits, and a sale that cannot make change is declined with a refund *before* anything mutates.
- **Two-pool cash model** — `CashInventory` separates inserted money (Deque, insertion order, refundable) from change reserves (EnumMap, the machine's float); refunds can never drain the float and change can never spend the customer's un-committed coins.
- **Composition over inheritance** — catalog (`Product`), stock (`ProductInventory`), and cash (`CashInventory`) are owned components the context orchestrates; no money math or stock arithmetic lives in the state machine.

## Class-by-Class

| File | Class | Responsibility |
|---|---|---|
| `Coin.java` | `Coin` | Enum of physical coins (INR 1/2/5/10) — a coin can only be a coin |
| `Note.java` | `Note` | Enum of notes (INR 10–200), separate from `Coin` — the note acceptor is different hardware, and notes are never change |
| `Product.java` | `Product` | Immutable catalog item (code, name, price); equality by code |
| `ProductInventory.java` | `ProductInventory` | Per-code stock; `restock`/`isAvailable`/`availableQuantity`/`commitDispense` — nothing debits without an explicit commit |
| `CashInventory.java` | `CashInventory` | Inserted money (insertion-order Deques) + change reserves (EnumMaps); `planChange` greedy dry-run returning `null` when not composable; `commitInsertedToReserves`/`dispenseChange` commits |
| `ChangePlan.java` | `ChangePlan` | Immutable reservation: which notes/coins will leave the reserves; `exact()` for zero change |
| `Refund.java` | `Refund` | Immutable record of a refund: the customer's own money, insertion order |
| `VendingState.java` | `VendingState` | Enum IDLE / HAS_MONEY / DISPENSING; documents why refund is an event and states are stateless singletons |
| `VendingMachine.java` | `VendingMachine` | The state-machine context: owns the transition table and the commit ladder (stock → price → plan → decline-or-commit); session facts (selected code, state) live here |
| `VendingMachineDemo.java` | `VendingMachineDemo` | Nine-section demo with `===` headers |

## Run

```bash
# Java 22+ single-file source launcher (handles sibling types):
java VendingMachineDemo.java

# Java 11+ classic:
javac *.java && java VendingMachineDemo
```

## Demo Sections

1. **Machine setup + price list** — catalog fixed at construction (prices cannot mutate mid-transaction), stock and a coin-heavy change float loaded.
2. **Happy path** — INR 50 fed (coin + note + coins) for the INR 25 Samosa; change INR 25 composed and dispensed; receipt printed from the committed plan.
3. **Insufficient money → message** — INR 15 against the INR 30 Vada Pav: a shortfall-naming message, state stays HAS_MONEY, the customer tops up and completes the sale. A message, not an exception — the customer did nothing wrong.
4. **Refund mid-transaction** — a INR 50 note comes back as exactly a 50-rupee note (their own money, insertion order); machine straight back to IDLE — why refunding is an event, not a state.
5. **Exact payment** — INR 15 for the INR 15 chai: the trivial `ChangePlan.exact()`, receipt reads "no change".
6. **Change not composable → sale declined** — a starved float (one 10-coin only) cannot make INR 15 change, so the INR 40-for-INR 25 sale is declined ("exact change only"), the customer is refunded, and stock AND reserves are provably untouched — the plan ran before any commit.
7. **Out-of-stock rejection** — B2 empty: the choice is refused, the money kept; the customer picks a stocked product instead.
8. **Wrong-state guardrails** — select in IDLE, refund in IDLE, restock/loadChange in HAS_MONEY: each rejected with an `IllegalStateException` naming current and required state.
9. **Audit** — state, inserted total, reserves total with per-denomination breakdown, and per-code inventory.

Rerunning the demo reproduces the identical log (no threads, no sleeps, no `Random`).
