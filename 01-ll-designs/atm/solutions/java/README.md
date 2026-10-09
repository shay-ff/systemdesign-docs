# ATM System - Java Implementation

Java 11, no external libraries, no `package` declarations (one top-level class per file, repo convention).

## Class-by-Class Design

| File | Role |
|---|---|
| `AtmDemo.java` | Six-section end-to-end narrative demo with `main` |
| `Atm.java` | The state-pattern CONTEXT: hardware collaborators + state pointer + session data + audit trail; every public action is `state = state.action(this, ...)` under a session lock; session setters are package-private so only states can touch them |
| `AtmState.java` | Interface with one hook per user action; every hook has a default that rejects the action naming the state - states override only what they allow |
| `IdleState.java` | Only `insertCard` legal (3-line state) |
| `CardInsertedState.java` | PIN prompt + the 3-attempt policy: wrong stays, correct resets and advances, third wrong confiscates + bank-blocks; attempt count is session-scoped (on Atm), block is card-scoped (on Card, via bank) |
| `PinVerifiedState.java` | Transaction menu; auth is done, authorization happens at execution |
| `TransactionSelectedState.java` | Amount collection |
| `DispensingState.java` | The only state that executes a transaction; `ejectCard` doubles as "confirm and execute" in the demo driver |
| `Card.java` | Physical key: constant-time-style PIN compare, linked account id, `blocked` flag set via the bank |
| `Account.java` | The money (long paise) + THE CONCURRENCY CRUX: `tryDebit`/`credit`/`getBalance` under a per-account `ReentrantLock`, so two ATMs on one account serialize exactly |
| `BankBackend.java` | The bank boundary interface (verify PIN + withdraw + deposit + block card) |
| `InMemoryBankBackend.java` | Offline host stand-in; real deployments speak ISO 8583 (0200/0210, response codes) behind this same interface |
| `Transaction.java` | Abstract COMMAND with a template method: validate -> runCore -> audit-record; declines come back as records, not exceptions (one audit schema for both outcomes) |
| `WithdrawCommand.java` | Hardware feasibility (dry-run) -> bank debit (atomic) -> dispense (commit); the ordering that avoids auto-reversals |
| `DepositCommand.java` | Credit via the bank |
| `BalanceInquiryCommand.java` | Read-only command (shows commands scaling with zero state changes) |
| `TransactionRecord.java` | Immutable audit row: card last-4, type, amount, result, note plan, thread, timestamp |
| `CashDispenser.java` | 2000/500/200/100 inventory; plan-then-commit greedy dispense (dry-run plan, commit only a fully-composing plan); paise->note-rupee conversion; rejects non-multiples of 100 |
| `ReceiptPrinter.java` | Pure formatter (record + note plan + balance -> receipt text); prints on declines too |
| `TransactionType.java` / `AtmResult.java` | Menu enum; ISO-8583-flavoured outcome enum |
| `AtmException.java` | Checked domain exception carrying an `AtmResult` |

Key invariants:

- The account can never go negative: every debit passes `Account.tryDebit`'s atomic check-then-debit.
- A shortage can never debit the account: `WithdrawCommand` checks `canDispense` BEFORE `bank.withdraw`.
- The dispenser can never be half-drained by a failed dispense: `plan()` is a pure read; only a complete plan is committed.
- Session data is only mutable by state classes (package-private setters) and is fully cleared on eject and confiscation.
- Every attempted transaction - approved or declined - produces exactly one `TransactionRecord`.

## Run

```bash
cd solutions/java

# Option 1: compile then run
javac *.java
java AtmDemo

# Option 2: no javac handy - merge into ONE file and run it
#   (script hoists imports and concatenates the classes in dependency order)
python3 /path/to/merge_java.py . AtmDemo.java
java /tmp/merged_atm.java
```

Runtime is well under a second (the only waits are the two-thread race latches, in milliseconds). Output is grouped under `=== Section N ===` headers:

1. Happy-path withdrawal with receipt (greedy note plan printed).
2. Wrong PIN twice then correct (3-attempt policy live).
3. Withdrawal exceeding balance -> clean decline, balance untouched.
4. Denomination shortage (2000-only machine, INR 300) -> declined BEFORE the debit.
5. Two ATMs, one account, two threads -> exactly one withdrawal wins.
6. Audit trail (approved + declined rows, thread names included).

## Complexity

| Operation | Cost |
|---|---|
| State transition | O(1) |
| Dispense plan / commit | O(D), D = 4 denominations |
| PIN verify / balance / lookup | O(1) map lookups |
| Receipt + audit row | O(1) |

## Production Notes (interview talking points)

- The debit -> dispense window is not a distributed transaction; production closes it with an auto-reversal command (the command pattern makes that a new command class, not a redesign).
- Push the per-account lock into storage at scale: `UPDATE ... WHERE balance >= amount`, striped locks, or CAS versioning.
- Real cards: EMV chip challenge-response - the PIN never travels to the host; the demo's plain PIN is an interview-grade stand-in (flagged in comments).
- Swap `InMemoryBankBackend` for an ISO 8583 socket client - zero changes above the `BankBackend` interface.
