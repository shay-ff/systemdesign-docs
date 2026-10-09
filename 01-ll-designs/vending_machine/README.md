# Vending Machine — Low Level Design

A classic interview LLD: model a cash vending machine with a three-state lifecycle, a two-pool money model (customer's inserted money vs the machine's change float), greedy change-making with plan-then-commit, and inventory that is never debited until a sale commits.

## Problem Statement

Design a vending machine that:

- Sells **products selected by code** (e.g. `A1`), each with a fixed price in rupees.
- Accepts **coins (₹1, ₹2, ₹5, ₹10) and notes (₹10, ₹20, ₹50, ₹100, ₹200)** as payment.
- Dispenses the selected product and **returns change** when the customer overpays.
- **Declines the sale and refunds everything** when change cannot be composed from the machine's reserves ("use exact change" behaviour).
- Supports **cancelling a transaction** (refund of exactly what was inserted) before dispensing.
- Supports operator maintenance: **restocking products**, **loading change**, and **auditing** current stock and float.

## Clarifying Questions to Ask the Interviewer

Asking these up front is half the round — they each change the design:

1. **Which currency and denominations?** → `Coin` (1, 2, 5, 10) and `Note` (10, 20, 50, 100, 200) enums; every later monetary decision (greedy correctness, change planning) depends on this answer.
2. **Coins and notes, or coins only?** → Two separate enums: the coin slot and note acceptor are physically different devices with different hardware paths; notes are never dispensed as change in this machine.
3. **What happens when change can't be made?** → Decline the sale and refund the inserted money — the machine never accepts money it cannot make change against. `planChange` returning `null` is the trigger.
4. **Which states are real states, and which are events?** → Three states only: `IDLE`, `HAS_MONEY`, `DISPENSING`. Refund and out-of-stock are *events* handled inside `HAS_MONEY`, not states the machine rests in.
5. **What are the refund semantics?** → Return **exactly what the customer inserted, in insertion order** — never machine float. A refund is "give back what you're holding", not "pay out ₹N".
6. **Is inserted money the machine's money?** → No — not until the sale commits. Two pools: `inserted` (this transaction) and `reserves` (the machine's float). Commit moves money across; refund returns it.
7. **Can a customer buy multiple items in one transaction?** → No — one product per transaction; a new transaction starts after dispensing. A multi-item basket is an extension (see below).
8. **What happens on insufficient money?** → Stay in `HAS_MONEY` and keep accepting money or selection attempts — the machine does not auto-refund on a wrong selection, only on an explicit refund request or an unpayable change situation.

## Functional Requirements

- Insert coins and notes; first insertion in `IDLE` starts a transaction (`HAS_MONEY`).
- Select a product by code in `HAS_MONEY`:
  - Unknown code / out of stock → error message, stay in `HAS_MONEY` (money is still held, customer may re-select or refund).
  - Insufficient money → prompt for more, stay in `HAS_MONEY`.
  - Sufficient money → move to `DISPENSING`: plan change, and either
    - change **not composable** → decline the sale, refund all inserted money, back to `IDLE`; or
    - change composable → commit the dispense (inventory debit), commit inserted money to reserves, dispense the product and the change, print a receipt, back to `IDLE`.
- Cancel (refund) in `HAS_MONEY` → return exactly the inserted money, back to `IDLE`.
- Operator helpers: restock products, load change reserves, audit stock and float.

## Non-Functional Requirements

- **Correctness under failure**: no path debits product stock or spends change reserves before the whole sale is known-good (plan-then-commit everywhere).
- **Auditable**: refunds return the customer's own money in insertion order; a receipt records exactly what was dispensed and what changed hands.
- **Fail fast**: invalid products (negative price, empty codes), negative quantities and negative amounts throw at the boundary with meaningful messages.
- **Extensible (OCP)**: new denominations, products and restock behaviour arrive as data/parameters; no state-machine edits.
- **Readable output**: every transaction prints a one-line narrative (interviewers ask you to run the demo and eyeball the log).

## Core Entities

| Entity | Role |
|---|---|
| `VendingMachine` | State-machine context: current `VendingState`, orchestrates inventory and cash; the only component that knows transitions |
| `VendingState` | Enum: `IDLE`, `HAS_MONEY`, `DISPENSING` — one constant per legal posture |
| `Product` | A catalog item (code, name, price); immutable; identity by code |
| `ProductInventory` | Stock per product code; availability queries and the `commitDispense` debit |
| `Coin` / `Note` | Physical money enums — `Coin` (1, 2, 5, 10), `Note` (10, 20, 50, 100, 200) |
| `CashInventory` | Two-pool money model: inserted coins/notes (this transaction, insertion order) and change reserves (the float); greedy `planChange` |
| `ChangePlan` | An immutable reservation over change reserves — planned denominations, committed later |
| `Refund` | Immutable record of exactly what is returned to the customer |
| `VendingMachineDemo` | End-to-end narrative demo |

## Design Patterns Used (and why)

- **State** — the headline pattern. `VendingState` (`IDLE`, `HAS_MONEY`, `DISPENSING`) defines the machine's legal postures; `VendingMachine` is the context holding per-transaction facts. Keeping the state count at three (refund and out-of-stock are events, not states) is the design discipline interviewers probe: every extra state is another set of illegal transitions to guard.
- **Plan-then-commit (reservation)** — `planChange` is a pure dry-run returning an immutable `ChangePlan`; `dispenseChange` commits it later. Same discipline in `ProductInventory.commitDispense`. Nothing irreversible happens until every precondition has passed — a failed path can never leak a half-sale.
- **Two-pool money model** — `CashInventory` separates inserted money (the customer's, this transaction) from reserves (the machine's float). Blending them into "the machine's total cash" is the classic bug: refunds would drain the float, and change plans could spend money the sale hasn't committed yet.
- **Composition + single ownership** — `VendingMachine` *has a* `ProductInventory` and *has a* `CashInventory`; the state machine asks them questions and tells them outcomes, never mutating their internals directly. Restockers, sales reports and low-stock alerts all read the same single source of truth.

## How to Run

```bash
cd solutions/java

# Java 11+ (single-file source launcher):
java VendingMachineDemo.java

# or compile then run:
javac *.java && java VendingMachineDemo
```

The demo walks through: a successful sale with change, an exact-payment sale, a declined sale (change not composable → refund), an explicit cancel/refund, out-of-stock and wrong-state guardrails, and operator restock/load-change/audit.

## Extension Questions Interviewers Ask

1. **"Add card payments."** — A `PaymentMethod` abstraction: cash follows the insert/hold/commit path already modelled; card is authorize-then-capture with no change pool (card terminal makes exact change). The state machine's transitions stay identical.
2. **"Multiple items in one basket?"** — `HAS_MONEY` accumulates a selection list; total price drives the same insufficient/composable checks; one commit covers all items.
3. **"Low-stock alerts?"** — `ProductInventory` already centralizes quantities; add a threshold check on `commitDispense` and a listener/observer the operator registers.
4. **"Persist across restarts?"** — Serialize `ProductInventory.stockByCode` and both cash pools; the two-pool split means a crash mid-transaction refunds only the inserted pool (recoverable from a transaction log).
5. **"What if two customers use it concurrently?"** — A vending machine is physically single-user; model it with one lock on the context or a per-session state machine per machine. Discuss, don't over-engineer.
6. **"Greedy change-making — when does it fail?"** — The chain {200, 100, 50, 20, 10, 5, 2, 1} is canonical, so greedy is provably optimal with full stock. With limited stock it can miss composable amounts: change of ₹60 against reserves {₹50, ₹20, ₹20, ₹20} — greedy takes the ₹50, can't make the remaining ₹10, declines; yet ₹20×3 was composable. The decline-then-refund contract ("exact change only") absorbs the miss. Full discussion in explanation.md.

## Learning Objectives

- State pattern: modelling legal postures, and resisting the urge to turn events into states
- Plan-then-commit: deferring every irreversible action until the whole operation is known-good
- Two-pool money modelling: ownership of cash changes at commit, not at insertion
- Greedy algorithms: when canonical denomination chains make greedy provably optimal, and what breaks under limited stock

For the entity-by-entity walkthrough, the state transition table, money lifecycle, greedy correctness discussion and edge cases, see [explanation.md](explanation.md).
