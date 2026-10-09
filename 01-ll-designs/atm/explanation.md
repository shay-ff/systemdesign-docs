# ATM System - Design Explanation

A walkthrough of every entity, the relationships, why each pattern was chosen, what was rejected, and the edge cases interviewers use to trip you up.

## The Problem in One Sentence

A dumb physical terminal must walk a customer through card -> PIN -> transaction -> cash, moving money safely against a remote bank while two machines can hit the same account at the same instant.

The ATM itself owns almost no policy: PIN records, balances, and blocks are the **bank's** truth; the machine owns hardware (card reader, keypad, cash box, printer) and a **state machine** for the customer flow. Everything else in this design follows from that split.

---

## Entity-by-Entity Rationale

### `AtmState` + the five states — the state pattern core

The interface exposes every user action (`insertCard`, `enterPin`, `selectTransaction`, `enterAmount`, `ejectCard`). Every hook has a **default implementation that rejects the action** with the current state named in the message - so `IdleState` is three lines (only `insertCard` is legal there) instead of five methods of boilerplate, and an illegal action ("enter a PIN with no card in") throws a message-carrying `IllegalStateException` instead of corrupting the session.

Why not a state enum + if/else in `Atm`? Because 8 actions x 5 states is a 40-branch matrix that grows in two dimensions. Each new state (Maintenance, OutOfService) or action (PIN change) edits the whole matrix. With the pattern, `MaintenanceState` is one new class; the matrix never exists. That is the OCP answer, and it is the answer interviewers want.

Two deliberate sub-decisions:

1. **States return the next state; they never mutate the ATM.** The context does `state = state.action(this, ...)`. States are pure deciders; the single `state` field is the one source of truth for where the machine is. This makes every state trivially unit-testable: build an `Atm`, call the state's hook, assert the returned state.
2. **States are stateless singletons; session data lives on `Atm`.** `IdleState.INSTANCE` is shared by every ATM in the process because it holds nothing session-specific (card, PIN attempt count, selected transaction, amount all live on the context). This is also what lets the two-ATM race demo work with zero crosstalk between the racing sessions.

The states, one line each:

- **IdleState** - only `insertCard` is legal.
- **CardInsertedState** - owns the PIN-attempt policy: count on the ATM (session-lifetime), block on the bank (card-lifetime). Wrong PIN stays here with attempts preserved; third wrong -> confiscate -> Idle; correct -> reset counter -> PinVerified.
- **PinVerifiedState** - the menu. Authentication is done; authorization is not - account checks happen later, at execution.
- **TransactionSelectedState** - collects the amount. Balance inquiry skips it (no amount needed).
- **DispensingState** - the only state that can execute a transaction. `ejectCard` doubles as "confirm and execute" in the demo driver; money moves, receipt prints, card returns, machine goes Idle.

### `Card` — the physical key

Card and Account are separate classes because they change for different reasons: cards are stealable, blockable, confiscatable, replaceable; accounts are the money. One account can hold several cards. Merging them forces the ATM to reason about card security policy while doing account math.

The PIN compare is deliberately loop-based without early exit (`diff |= a ^ b` per char, then `diff == 0`) - a stand-in for constant-time comparison, worth mentioning: a naive `equals` with early exit leaks how many leading digits were right via timing. The real world is EMV chip auth where the PIN never travels to the bank at all; this is the interview-grade simplification, and saying "in reality the chip does challenge-response" out loud is a plus.

`blocked` is a card field but set through `bank.blockCard(...)` - the BANK owns the truth so every channel (this ATM, net-banking, a phone-banking agent) sees the same flag.

### `Account` — the money, and the concurrency crux

The dangerous code in any ATM design is check-then-act:

```java
if (account.getBalance() >= amount) {   // check
    account.debit(amount);             // act  <- another thread can slip in here
}
```

Two ATMs race: both check against INR 5,000, both debit INR 4,000, the account ends at -3,000. The fix is to make read-check-write ONE critical section, and the lock must live **on the account** - the shared resource - not on the ATM (each ATM's monitor guards nothing but its own session) and not on the bank object (one global lock serializes every customer; name this and reject it out loud).

Lock granularity, in interview-credit order:

1. **Per-account lock (implemented)** - `ReentrantLock` inside `Account`; `tryDebit`/`credit`/`getBalance` all pass through it. Unrelated accounts never contend. Simple, obviously correct.
2. **Striped locks** - `Lock[] stripes; stripes[hash(accountId) % N].lock()`. ~N-way parallelism with tiny memory; the production answer when accounts are numerous or lock identity is awkward (e.g., entity objects rebuilt per request).
3. **Optimistic / CAS** - a `version` field; retry on collision. Wins when contention is rare.
4. **Database** - `UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?` - the invariant moves into storage; the definitive production answer for a real core-banking system.

`tryDebit` returns a **boolean, not an exception**, for the insufficient-funds case: "you don't have that much" is a normal business outcome the machine must display and the receipt must print, not a system fault.

Money is `long` **paise** (INR 1 = 100) with `formatRupees` for display - the "never use double for money" point, made in code.

### `BankBackend` — the boundary (and the ISO 8583 note)

An ATM is a client. It does not own PIN records or balances; it asks the host. Real ATMs speak **ISO 8583** - the international card-originated message standard - to the bank's switch: a **0200** financial request goes out, a **0210** response comes back carrying a response code: **00** approved, **51** insufficient funds, **55** incorrect PIN, **41** pick-up (capture the card). `AtmResult` mirrors those codes with readable names (`APPROVED`, `INSUFFICIENT_FUNDS`, `INVALID_PIN`, `CARD_CAPTURED`...), and `verifyPinAndFetchBalance` returning balance+auth in one call mirrors the real 0210 payload shape.

The interface is dependency inversion made physical: `InMemoryBankBackend` for tests/demo today, a socket-speaking client in production - zero changes above the line. In an interview, drawing this line and saying "the ATM is a dumb terminal, the bank is the source of truth" is worth more than any pattern name.

### `Transaction` + commands — the command pattern

`Transaction` is an abstract command with a **template method**:

```java
public final TransactionRecord execute(bank, dispenser) {
    validate();
    try {
        notePlan = runCore(bank, dispenser);
        record = ... APPROVED ...;
    } catch (AtmException e) {
        record = ... e.getResult() ...;   // decline -> SAME record schema
    }
    return record;
}
```

Three wins worth calling out:

1. **The state machine stays ignorant.** `DispensingState` runs "the command"; it never learns withdrawal semantics. A new transaction type (mini-statement, PIN change, transfer) is one enum constant + one command class + one factory `case` line.
2. **One audit schema for both outcomes.** The record creation lives in exactly one place, so an `INSUFFICIENT_FUNDS` decline lands in the audit trail with the same shape as an `APPROVED` - which is what a real switch log looks like.
3. **Declines are returned, not thrown.** The template catches the checked `AtmException` and converts it to a record. The caller (Atm) has one control flow. Throwing for business outcomes forces try/catch noise into every caller and makes "print a receipt for the decline" awkward - this shape makes it free.

`WithdrawCommand.runCore` encodes the ordering that makes the whole flow safe:

```
1. dispenser.canDispense(amount)   // dry-run: can the HARDWARE pay?
2. bank.withdraw(...)               // atomic account debit
3. dispenser.dispense(amount)      // commit the note plan
```

Hardware feasibility FIRST, because a failed dispense after a successful debit owes the customer an auto-reversal (a saga compensation). Checking the box before touching the money deletes the most common compensation path. The remaining window (debit succeeds, machine jams before notes leave) is the production compensation story - see Trade-offs.

### `CashDispenser` — greedy, plan-then-commit

Indian ATM cassettes: 2000 / 500 / 200 / 100 (post-2016 demonetisation configuration; older machines had 1000s).

Greedy walks denominations largest-first taking as many as fit. Two facts an interviewer will probe:

- **Greedy is optimal for the standard Indian chain** - each denomination divides the next, so greedy always finds a plan when one exists... in INFINITE supply.
- **Greedy has no backtracking** - with FINITE supply it can paint itself into a corner: only 2000s left, INR 300 requested -> greedy takes zero notes and reports failure. It cannot "give change" by taking a 2000 and returning 1700 (an ATM never takes money in during a withdrawal).

The implementation detail that separates a strong answer: **plan-then-commit**. `plan()` is a pure dry-run that only READS the inventory; `dispense()` commits the plan only if it fully composes the amount. The naive greedy-with-mutation version can eat some notes and then discover the amount is incomposable, leaving the machine half-drained with nothing dispensed. Two phases = atomicity, at the cost of one extra loop over four denominations.

Amounts arrive in **paise**; the planner converts to whole-note rupee units first and rejects any non-multiple of INR 100 up front - ATMs physically cannot pay INR 3700.50.

Methods are `synchronized`: one machine, one exit shutter, so the plan-commit pair must not interleave with a restock or another dispense.

### `ReceiptPrinter` — deliberately dumb

A pure formatter: record + note plan + balance -> text. No state, no policy, no dependencies on anything behavioural. Printers are output devices; the moment a printer starts making decisions it is in the wrong class. The receipt prints on declines too (insufficient-funds receipts are real) and shows the note plan - Indian ATM receipts do print denominations.

### `TransactionRecord` — immutable audit row

Final fields, no setters, one row per ATTEMPTED transaction (approved or declined) with thread name and timestamp. Audit rows are facts about the past; mutation would let a later bug rewrite history. The synchronized-list trail prints at the end of the demo: `timestamp | thread | card | type | amount | result` - the exact shape a switch log entry takes.

### `Atm` — the context

Owns: terminal identity, the five hardware collaborators, the **state pointer**, and all SESSION data (card, PIN attempt count, selected transaction, entered amount, audit trail). Every public user action is `state = state.action(this, ...)` under a `ReentrantLock` - one physical terminal should not interleave two customers' key presses.

Package-private setters (`setCard`, `incrementPinAttempts`, ...) mean only the state classes can touch session fields - demo/application code cannot corrupt a session. In this no-package layout that guard is conventional (same directory), but the shape is right for real packages.

---

## Class Relationships

```
Atm  o--  AtmState            : current state pointer (delegates every action)
Atm  *--  BankBackend         : talks to the bank (interface)
Atm  *--  CashDispenser       : one cash box per machine
Atm  *--  ReceiptPrinter      : one printer per machine
Atm  o--  Card                : session-scoped reference (insert -> eject/confiscate)
InMemoryBankBackend o-- many Card / Account     : the bank's truth (maps)
Card  --> 1 Account           : linked account id
Transaction (abstract) --> Card, BankBackend, CashDispenser
WithdrawCommand / DepositCommand / BalanceInquiryCommand  --|> Transaction
TransactionRecord  o--  Card fields, AtmResult  : immutable audit row
Atm  1 -- *  TransactionRecord : audit trail
```

No state class depends on a concrete command; no command depends on a state class. The two patterns interlock only through the `Atm` context and the abstract `Transaction` - which is why each can be extended independently.

---

## The Crux: Two ATMs, One Account (the race)

The demo builds a joint account with exactly INR 5,000 and two ATMs that both try to withdraw INR 4,000, released together by a latch:

- **Without the account lock**: both `tryDebit` calls pass the balance check against a stale read; both debit; balance = -INR 3,000. Money created from nothing; the bank eats it.
- **With `Account.tryDebit`'s `ReentrantLock`**: the two check-debit sections serialize; the first wins (balance INR 1,000), the second's check fails cleanly, and the losing ATM prints an `INSUFFICIENT_FUNDS` receipt. The audit trail shows one `APPROVED` and one `INSUFFICIENT_FUNDS` row - the race, won.

What the lock must NOT be:

- **On the ATM object** - each machine has its own monitor; two ATMs never share one, so it guards nothing.
- **On the bank object (global)** - serializes every customer in the country behind one lock; a throughput disaster.
- **On `synchronized(bank)` inside the command** - same global-lock mistake one level down.

One subtlety the demo shows but does not belabour: the two racing ATMs share ONE `Card` object in the demo (a physical card cannot be in two machines, but a card NUMBER can be - via card-not-present fraud or, legitimately, an ATM + UPI app hitting the account). The per-account lock covers both cases because it guards the MONEY, not the plastic.

### PIN retry limits - who counts, who blocks

The classic probe: "3 wrong PINs - where does the counter live?" The answer has two halves with different lifetimes:

- **The attempt counter is session-scoped** and lives on the `Atm`: a wrong PIN at THIS machine three times -> confiscate HERE. The counter resets on eject and on success. Real hardware behaves exactly this way.
- **The blocked flag is card-scoped** and lives on the `Card`, set through the bank: after confiscation the card is dead at EVERY channel, not just the machine that swallowed it.

Why not count on the card (persistent attempts)? A thief with a stolen card could farm attempts across many ATMs; a legitimate customer with fat fingers must not be blocked bank-wide forever by one bad session. Session-scoped counting + bank-scoped blocking on confiscation is the balance real systems strike. (Real issuers additionally track failed-verify counts host-side; mention it as the production refinement.)

### Dispenser denomination shortage

The INR 300-vs-2000s case, played out in the demo's Section 4:

- `plan()` returns null (300/100 paise-converted is 3 note-rupees; 2000s cannot compose it).
- `WithdrawCommand` catches this at step 1 - BEFORE `bank.withdraw` - so the account is never debited. This ordering is the entire defence against "money left my account but no cash came out".
- The decline prints a receipt with `DISPENSER_SHORTAGE` and lands in the audit trail.

---

## Edge Cases and How the Code Handles Them

| Edge case | Behaviour |
|---|---|
| Third wrong PIN | Card confiscated (retained), bank-blocked, machine -> Idle |
| Wrong PIN then eject | Counter resets with session; card is fine |
| Blocked card inserted | Confiscated at PIN time (re-check in CardInsertedState) |
| Withdrawal > balance | `INSUFFICIENT_FUNDS` decline; balance untouched; receipt printed |
| Withdrawal incomposable from notes | `DISPENSER_SHORTAGE` decline BEFORE debit; notes untouched |
| Non-multiple of INR 100 | Rejected at validation; never reaches the bank |
| Two ATMs, one account, same instant | Exactly one wins; loser declines; never negative |
| Amount entered in wrong state | `IllegalStateException` naming the state |
| Unknown card at the bank | `CARD_CAPTURED` ("not issued by this bank") |
| Deposit to a blocked card | `CARD_BLOCKED` from the bank |
| Receipt after decline | Printed, with the decline result and current balance |
| Session data after eject/confiscate | Card, attempts, selection, amount all cleared |

---

## Trade-offs Accepted

1. **Paise `long`, not `double`** - the correct production choice for once, made in a demo; `formatRupees` keeps display clean.
2. **Two-phase plan-then-commit over backtracking dispensing** - greedy + dry-run covers the standard Indian chain; a knapsack solver for exotic denominations is not worth the code in 45 minutes, and the failure mode is a clean decline either way.
3. **The debit-dispense window is not a true distributed transaction.** If the machine jams between debit and dispense, production logic is an auto-reversal (a `ReverseWithdrawalCommand` appended to the audit trail). We CHECK the dispenser first to make that window as small as possible rather than eliminating it - say this trade-off out loud; eliminating it needs 2PC or a saga, and 2PC over a WAN to a switch is its own interview conversation.
4. **`synchronized` on the dispenser, `ReentrantLock` on the account** - mixed vocabulary, deliberately: `synchronized` where the critical section is a whole method; explicit lock where we want the lock object to be a private field of the shared resource and document WHY it is there.
5. **PIN stored in plain text on the demo card** - flagged in comments; the real answer is EMV chip challenge-response (the PIN never travels; the bank verifies a chip-derived cryptogram).
6. **One receipt printer per machine hardcoded in `Atm`** - a real machine's printer is a fixed part; injecting it anyway would be ceremony. (Inject it the moment the interview says "the ATM is software simulating many machines".)

---

## Complexity Summary

| Operation | Complexity |
|---|---|
| State transition | O(1) |
| Greedy dispense plan / commit | O(D), D = 4 |
| PIN verify / balance / account lookup | O(1) |
| Receipt formatting | O(1) |
| Audit trail append | O(1) amortized |
| Audit trail print | O(N) |

---

## How to Extend (Interview Talking Points)

- **Auto-reversal on jam**: a `ReverseWithdrawalCommand` (credit + audit row) executed by the machine's reconciliation loop; the command pattern means it plugs into the existing trail with zero new plumbing.
- **Maintenance state**: `OutOfCashState` / `MaintenanceState` as new `AtmState` classes - the OCP payoff, visible.
- **Mini-statement / PIN change / transfer**: one enum constant + one command class + one factory case.
- **Striped account locks** or CAS versioning when account count is large.
- **ISO 8583 client**: a `SocketBankBackend` framing real 0200/0210 messages - drop-in behind the interface.
- **Cash forecasting**: restock planning per machine from historical denomination usage - the ops science layer.
