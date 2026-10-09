# ATM System - Low Level Design

A single-ATM-terminal state machine over a bank backend: card + PIN authentication with 3-attempt confiscation, withdrawals/deposits/balance inquiries as command objects, Indian-denomination cash dispensing with greedy note planning, receipts, and a thread-safe account model that survives two ATMs racing on one account.

## Problem Statement

Design an ATM (Automated Teller Machine) where:

- A customer **inserts a card**, **enters a PIN** (3 wrong attempts -> card confiscation and a bank-side block).
- After PIN verification the customer picks a **transaction**: cash withdrawal, cash deposit, or balance inquiry.
- **Withdrawals** must check BOTH the account balance and the machine's cash inventory before any money moves.
- The machine **dispenses cash in Indian denominations** (2000 / 500 / 200 / 100 notes) and must **fail cleanly when the greedy plan cannot compose the amount** (e.g., only 2000s left and INR 300 requested).
- Every transaction produces a **printed receipt** and lands in an **audit trail** (approved and declined both).
- The machine talks to the **bank over a backend boundary** (real ATMs speak a network protocol to the bank's switch - ISO 8583 is the classic).
- Two ATMs (or an ATM and a UPI app) can hit the **same account concurrently**: exactly one withdrawal must win; the account must never go negative.

## Key Features

- **State machine**: `Idle -> CardInserted -> PinVerified -> TransactionSelected -> Dispensing -> Idle`, with confiscation/eject paths back to Idle. Every action illegal in a state is rejected with a message naming the state.
- **Command objects**: `WithdrawCommand`, `DepositCommand`, `BalanceInquiryCommand` behind an abstract `Transaction` template method (validate -> run -> audit-record). Adding a transaction type = one new class, zero state-machine changes.
- **PIN policy**: 3 attempts per session, then physical confiscation + `bank.blockCard` (the block is bank-owned truth, so every other channel sees it).
- **Cash dispenser**: denomination inventory with **plan-then-commit greedy dispensing** - a dry-run composes the note plan; only a full plan is committed, so a shortage can never leave the machine half-drained or the account debited with no cash out.
- **Concurrency**: per-`Account` `ReentrantLock` around check-then-debit; the demo races two ATMs on one account with two threads and shows exactly one winning.
- **Bank boundary**: `BankBackend` interface with `InMemoryBankBackend` (a stand-in for the network host; see ISO 8583 note below).

## Clarifying Questions an Interviewer Expects You to Ask

1. **How many wrong PINs do we allow, and what happens then?**
   3 attempts; the card is physically retained (confiscated) and blocked at the bank. The attempt counter is per-SESSION (on the ATM), the block is per-CARD (on the bank) - two different lifetimes, two different owners.
2. **What if the account has money but the machine does not (or cannot compose it)?**
   Decline with `DISPENSER_SHORTAGE` BEFORE debiting. Hardware feasibility is checked first so a failed dispense never owes the customer a reversal.
3. **What denominations, and what if greedy cannot compose the amount?**
   2000/500/200/100. Greedy walks largest-first; for the standard Indian chain it is optimal for composable amounts, but it has no backtracking - `INR 300` against a 2000-only machine is a clean decline.
4. **Can a withdrawal be a non-multiple of 100?**
   No - ATMs physically cannot pay it; rejected at validation, never reaching the bank.
5. **Who owns the balance check, the ATM or the bank?**
   The bank - the ATM is a client. `Account.tryDebit` makes read-check-write one atomic section so two concurrent ATMs cannot both pass a stale check.
6. **Is PIN verification state or a call?**
   A call to the bank (the bank holds the PIN record); the ATM only tracks ATTEMPT COUNT.
7. **What happens on card confiscation mid-session?**
   Back to Idle, card retained, session state cleared, card blocked bank-wide.
8. **Does the receipt print on declines?**
   Yes - insufficient-funds receipts are real; the audit trail records declines with the same schema as approvals.

## Core Entities

| Entity | Responsibility |
|---|---|
| `Atm` | The context: card reader, PIN pad, state pointer, session data, audit trail; delegates every action to the current state |
| `AtmState` (interface) | Contract for every user action; default methods reject illegal actions with the state named |
| `IdleState` / `CardInsertedState` / `PinVerifiedState` / `TransactionSelectedState` / `DispensingState` | The five states; stateless singletons, pure deciders returning the next state |
| `Card` | Physical key: number, PIN check (constant-time-style compare), linked account, blocked flag |
| `Account` | The money: atomic `tryDebit`/`credit` under a per-account lock - the concurrency crux |
| `BankBackend` (interface) | Bank boundary: verify PIN, withdraw, deposit, block card |
| `InMemoryBankBackend` | In-memory host stand-in (real: ISO 8583 over a host link) |
| `Transaction` (abstract) | Command template method: validate -> runCore -> audit-record; declines return as records |
| `WithdrawCommand` / `DepositCommand` / `BalanceInquiryCommand` | Concrete commands; withdrawal checks dispenser feasibility before debiting |
| `TransactionRecord` | Immutable audit-trail row (card, type, amount, result, thread, timestamp) |
| `CashDispenser` | Denomination inventory; plan-then-commit greedy dispense; atomic per dispense |
| `ReceiptPrinter` | Pure formatter: transaction record -> receipt text (notes plan included) |
| `TransactionType` / `AtmResult` | Enums: menu options; ISO-8583-flavoured outcome codes |
| `AtmException` | Checked domain exception carrying an `AtmResult` |
| `AtmDemo` | Six-section end-to-end narrative demo |

## Design Patterns Used (and why)

- **State pattern - the ATM flow.** ~8 user actions x 5 states is a 40-branch if/else matrix in one class; the pattern gives each state one small class and the context a single `state = state.action(this, ...)` delegation. Adding a `MaintenanceState` later touches one new class, not the matrix. States return the next state rather than mutating the context - pure deciders, one source of truth for the current state.
- **Command pattern - transactions.** Withdraw/deposit/balance are executable objects behind an abstract template. The state machine never learns transaction semantics; the audit record is produced in exactly one place (the template), so approved and declined outcomes share one schema; commands are the natural unit for production compensation (auto-reversal on a jammed dispense).
- **Template method - Transaction.execute.** Fixes validate -> run -> record; subclasses implement `runCore` only. A decline is a RETURNED record, not an exception - "insufficient funds" is a business outcome the machine displays, not a crash.
- **Strategy/Interface - BankBackend.** The ATM is a dumb terminal; the bank is the source of truth for PINs and balances. The interface is the network boundary (real deployments swap `InMemoryBankBackend` for a socket-speaking client - dependency inversion in one line).
- **Singleton-ish stateless states.** All five states are stateless shared instances; session data (card, PIN attempts, selection, amount) lives on the `Atm` context. This is what lets two ATMs share `IdleState.INSTANCE` safely in the concurrency demo.

Rejected alternatives and trade-off discussion live in [explanation.md](explanation.md).

## How to Run

```bash
cd solutions/java

# Option 1: compile then run
javac *.java
java AtmDemo

# Option 2 (no javac handy): merge into one file and run
#   see solutions/java/README.md - the merge_java.py trick
```

Total runtime: well under a second (the only waits are the two-thread race latches, milliseconds).

## Time Complexity

- State transition: O(1) - one interface call
- Greedy dispense plan: O(D), D = 4 denominations (constant in practice)
- PIN verification / balance: O(1) map lookups
- Audit trail append: O(1); full-trail print O(N)

## Interview Extension Questions

1. How would you make the dispense + debit atomic across machine and bank? (Saga/compensation: debit first, dispense, auto-reversal on jam - or dispense-first with a reversal flag; discuss both.)
2. How would you scale the per-account lock? (Striped lock pool, optimistic CAS on a version, or push the invariant into the DB: `UPDATE ... WHERE balance >= amount`.)
3. What if the bank is unreachable mid-transaction? (Timeout + decline + retry queue; the card must still eject - hardware safety first.)
4. Add a "mini statement" transaction - what changes? (One enum constant + one command class + one factory case line; no state or bank changes.)
5. How do real ATMs talk to banks? (ISO 8583: 0200 request / 0210 response with response codes - 00 approve, 51 insufficient funds, 55 bad PIN, 41 capture.)
6. How would you restock planning work at network scale? (Forecast-based cassette loading; cash management is a real ops science.)
7. Multi-language screens / accessibility? (State machine untouched; presentation is a view concern.)

## Files Structure

```
atm/
├── README.md              # This file
├── design.puml            # PlantUML class diagram
├── explanation.md         # Design walkthrough, trade-offs, edge cases
└── solutions/
    └── java/
        ├── README.md      # Class-by-class notes + run instructions
        ├── AtmDemo.java
        ├── Atm.java / AtmState.java / IdleState.java / CardInsertedState.java
        ├── PinVerifiedState.java / TransactionSelectedState.java / DispensingState.java
        ├── Card.java / Account.java
        ├── BankBackend.java / InMemoryBankBackend.java
        ├── Transaction.java / WithdrawCommand.java / DepositCommand.java
        ├── BalanceInquiryCommand.java / TransactionRecord.java
        ├── CashDispenser.java / ReceiptPrinter.java
        └── TransactionType.java / AtmResult.java / AtmException.java
```

See [explanation.md](explanation.md) for the full design walkthrough.
