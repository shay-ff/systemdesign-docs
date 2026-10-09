# Vending Machine — Design Explanation

A walkthrough of every entity, why it exists in that shape, what was rejected, and where the classic edge cases bite.

## Entity-by-Entity Rationale

### `Coin` / `Note` — physical money as enums

`Coin` (₹1, ₹2, ₹5, ₹10) and `Note` (₹10, ₹20, ₹50, ₹100, ₹200) are enums, not ints. Every layer of this machine deals in **physical objects** — the coin slot accepts them, the inventory stores them, change is dispensed as them. An int like `5` could be a coin, a price or a sum; a `Coin.FIVE` can only be a coin. When the interviewer's first question is "what currency, what denominations?", the answer is literally the enum constants.

`Note` is deliberately **separate** from `Coin` rather than one shared "Currency" enum. A note and a coin are physically different objects: the note acceptor is its own hardware path, notes have their own inventory, and this machine never dispenses notes as change. A shared enum would force every monetary int into one type and erase the coin-vs-note distinction the domain actually needs.

### `Product` — the catalog, not the stock

`Product` (code, name, price) is immutable and its identity is its code (`equals`/`hashCode` on code only). It is a **catalog** object: *what* the machine sells. It is deliberately not the inventory — *how many* the machine holds. Keeping the two apart lets one `Product` describe many shelf slots and lets quantities live in one per-code map owned by one component. Prices and codes cannot mutate mid-transaction because the object cannot mutate at all.

### `ProductInventory` — one owner of quantities

`ProductInventory` holds a `Map<String, Integer>` from code to quantity. The load-bearing decision is **ownership**: inventory is a separate component from the catalog and from the state machine. The state machine *asks* it questions (`isAvailable("A1")`) and *tells* it outcomes (`commitDispense("A1")`); it never mutates stock itself. Why: a restocker, a sales report and a low-stock alert all need the same single source of truth; burying quantities inside the state machine makes each of those a state-machine edit (OCP violation, and the classic way these codebases rot).

`commitDispense` debits exactly one unit and throws if stock is zero — with a message that says the state machine must check availability **before** payment. The naming is the discipline: nothing debits stock without an explicit COMMIT.

### `CashInventory` — the two-pool money model

The heart of the design. Two separate stores:

- **Inserted money** (`insertedCoins` / `insertedNotes`, `Deque`s in insertion order): what the customer fed in for the current sale. It is *not the machine's money until the sale commits*. A refund returns exactly this; a commit moves it into reserves.
- **Change reserves** (`coinReserves` / `noteReserves`, `EnumMap`s by denomination): the machine's own float. What the refiller owns and what change comes from.

Blending the two into "the machine's total cash" is the classic design bug: refunds and change math would share one pool, so a refund could pay out change reserves (draining the float) or a change plan could "use" coins the customer just inserted but the sale hasn't committed. Two pools, two lifecycles, one commit path.

**Why insertion order matters**: a refund must return the customer's *own* money — "I put in a ₹50!" is a dispute, and insertion order is what reconstructs it. The `Deque` keeps the audit trail; `refundInserted()` snapshots it into an immutable `Refund` and clears the transaction.

### `ChangePlan` — a reservation over reserves

`planChange(amount)` is a **dry-run**: it computes which denominations would leave the reserves and returns an immutable `ChangePlan` — mutating nothing. `dispenseChange(plan)` later commits it. The gap between planning and committing is where the state machine finishes deciding; the plan is a *reservation* the reserves honour.

The plan records exactly which denominations and counts will be paid out, so a receipt prints the change composition without re-deriving it — and without a second greedy walk that might see **different** reserves if anything intervened in between. `ChangePlan.exact()` is the zero-change case (exact payment), which keeps the success path free of null checks.

### `Refund` — an event's receipt

A refund is one event carrying two lists (coins, notes) plus a `totalRupees()` and a printable receipt line. It is a class rather than a return-pair because formatting ("REFUND: 50-rupee note, 10-rupee note") belongs to the record — not to `CashInventory` (which shouldn't know about receipts) and not to the state machine (which shouldn't format).

### `VendingState` — three postures, not five

`IDLE`, `HAS_MONEY`, `DISPENSING`. The discipline is what *isn't* there:

- **No `REFUNDING` state** — a refund takes ~0 time: the machine returns inserted money and is instantly `IDLE` again. It's an event handled inside `HAS_MONEY`, not a posture the machine rests in.
- **No `OUT_OF_STOCK` state** — "out of stock" is a property of an inventory query at selection time, not a posture of the machine (another code can be fully stocked).

Fewer states = fewer illegal transitions to guard. The same discipline as the elevator's "no DOORS_CLOSED state" call. Every session fact (inserted money, selected product) lives on the `VendingMachine` context, not on the states — states are pure deciders and remain trivially inspectable.

## The State Machine — Transition Table

| Current state | Event | Guard | Action | Next state |
|---|---|---|---|---|
| `IDLE` | `insertCoin` / `insertNote` | valid denomination | hold money, start transaction | `HAS_MONEY` |
| `IDLE` | `selectProduct` | — | reject: no transaction in progress | `IDLE` |
| `IDLE` | `refund` | — | reject (nothing inserted); return "nothing" refund | `IDLE` |
| `HAS_MONEY` | `insertCoin` / `insertNote` | valid denomination | hold money | `HAS_MONEY` |
| `HAS_MONEY` | `selectProduct` | code unknown or out of stock | message; keep money held | `HAS_MONEY` |
| `HAS_MONEY` | `selectProduct` | inserted total < price | prompt for more; keep money held | `HAS_MONEY` |
| `HAS_MONEY` | `selectProduct` | inserted ≥ price, change = 0 | commit dispense, commit money to reserves, receipt | `IDLE` (via `DISPENSING`) |
| `HAS_MONEY` | `selectProduct` | inserted ≥ price, change composable | plan change → commit dispense → commit money to reserves → dispense change → receipt | `IDLE` (via `DISPENSING`) |
| `HAS_MONEY` | `selectProduct` | inserted ≥ price, change NOT composable | decline sale, refund all inserted money | `IDLE` |
| `HAS_MONEY` | `refund` | — | return exactly the inserted money, insertion order | `IDLE` |
| `DISPENSING` | any | — | internal: runs to completion (item + change + receipt), then resets | `IDLE` |

`DISPENSING` is a transient posture: the machine enters it to run the commit sequence and leaves it automatically — there is no customer event that fires while dispensing. (In a real machine, motors take time and the state absorbs interrupts; here it is the atomic commit boundary.)

## The Money Lifecycle

Every rupee in the machine is always in exactly one of three places:

```
customer's pocket
       |
       | insertCoin / insertNote          (IDLE → HAS_MONEY)
       v
   INSERTED pool  ---- cash.insertedCoins / insertedNotes (this transaction)
       |                         |
       | commitInsertedToReserves| refundInserted
       | (sale commits)         | (cancel / declined change)
       v                         v
  CHANGE RESERVES           back to the customer,
  (machine's float)         exactly what they inserted,
       |                    in insertion order
       | planChange → dispenseChange (only on a committed sale)
       v
  customer's tray (change)
```

Ownership moves **only** at commit: inserted money is the customer's until the sale is known-good; the commit moves it into the machine's reserves; change flows out of reserves only under a committed plan. There is no path from inserted money directly into the change tray, and no path from reserves into a refund — those two forbidden arrows are what the two-pool split enforces.

## Greedy Change-Making — Correctness

`planChange` walks the canonical chain **largest to smallest: ₹200, ₹100, ₹50, ₹20, ₹10 notes, then ₹10, ₹5, ₹2, ₹1 coins**, taking `min(available, remaining / denomination)` at each step, and returns `null` if anything remains.

**Why greedy is provably optimal with unlimited stock**: the chain {200, 100, 50, 20, 10, 5, 2, 1} is *canonical* — the classic induction holds (each denomination is reachable by smaller ones, so taking the largest first is never worse). Any amount the reserves could compose, greedy composes with the fewest denominations.

**Where greedy breaks — limited stock**: greedy has no backtracking, and the canonical-chain guarantee does not survive stock limits. Worked failure: change of **₹60 against reserves {₹50, ₹20, ₹20, ₹20}**. Greedy takes the ₹50 (largest first), leaving ₹10 — no ₹10 note, no ₹10 coin, so it declines. But **₹20 + ₹20 + ₹20 = ₹60 was composable**. Greedy caused the failure; an exhaustive search would have found the plan.

Contrast with the *legitimate* decline: ₹30 against reserves {₹20, ₹20}. Greedy takes one ₹20, needs ₹10, can't make it — but no subset of {20, 20} sums to 30, so the amount genuinely was impossible. Greedy declines correctly there.

The honest interview statement, and what this code does: **greedy is provably optimal with full stock (canonical chain), finds the plan when a greedy-composable one exists under limited stock, and cleanly declines (`null`) otherwise — real machines behave exactly this way ("use exact change").** A DP or exhaustive subset search over 9 denominations would find every composable amount, at the cost of complexity the hardware doesn't justify; declining is a product decision, not a bug. If the business wanted maximum sales, the fix is `planChange` internals only — the state machine, the decline path and the refund path never change.

**"Exact change only"**: when `planChange` returns `null`, the machine declines the *entire sale* and refunds the inserted money. It never takes the money and owes the customer change it can't make — the customer is never a creditor of the machine.

## Pattern Choices and Rejected Alternatives

| Choice | Rejected alternative | Why |
|---|---|---|
| Two pools (inserted vs reserves) | One "total cash" map | One pool makes refunds drain the float and change plans spend uncommitted money — the classic bug |
| `planChange` dry-run + `dispenseChange` commit | Debit reserves during planning | A decline between plan and dispense would leave reserves already spent; the plan IS the reservation |
| States as enum of 3 | `REFUNDING` / `OUT_OF_STOCK` states | Refund is instantaneous; out-of-stock is an inventory query — extra states add illegal transitions to guard |
| `Coin` and `Note` as separate enums | One `Money`/`Currency` enum | The note acceptor and coin slot are different hardware; the domain distinguishes them everywhere |
| `Refund` class with receipt formatting | `Pair<List<Coin>, List<Note>>` return | Formatting belongs to the record; inventories shouldn't format, state machines shouldn't present |
| Insertion-order `Deque` for inserted money | Count-by-denomination map | A refund must return the customer's own money, oldest-first, for dispute reconstruction |
| Decline when change not composable | Accept money and owe change | The machine never becomes a debtor over pocket change; "exact change only" is the real-world behaviour |

## Complexity

| Operation | Time | Space |
|---|---|---|
| `insertCoin` / `insertNote` | O(1) | O(1) amortized |
| `insertedTotal` | O(inserted items) | O(1) |
| `planChange(amount)` | O(D) — D = 9 denominations, constant | O(D) |
| `dispenseChange(plan)` | O(D) | O(1) |
| `refundInserted` | O(inserted items) | O(inserted items) |
| `commitInsertedToReserves` | O(inserted items) | O(D) |
| `isAvailable` / `commitDispense` | O(1) map lookup | O(1) |
| One full transaction | O(inserted items + D) | O(inserted items) |

Everything is constant-bounded by the denomination count (9) and the transaction's inserted-money count (a handful of physical objects) — the machine is small and that is the point.

## Classic Edge Cases

1. **Change not composable.** Customer inserts ₹100, selects a ₹75 product, reserves cannot make ₹25 (say only ₹20 notes): the sale is declined, the full ₹100 is refunded, state → `IDLE`. The machine never owes change.
2. **Out of stock mid-selection.** `selectProduct("B2")` when `B2`'s stock is 0: message, money stays held, state stays `HAS_MONEY` — the customer re-selects or refunds. Availability is checked *before* any money is committed, so a failed selection can't leak a half-sale.
3. **Insufficient money.** Inserted ₹10, price ₹25: prompt for more, stay `HAS_MONEY`. The machine doesn't auto-refund — the customer may still add money.
4. **Wrong-state calls.** `selectProduct` or `refund` in `IDLE`: rejected with a message (refund in `IDLE` returns "nothing — no money was inserted"); `insertCoin` in `IDLE` *starts* a transaction. Every transition is guarded by the current state.
5. **Exact payment.** Inserted total == price: `planChange(0)` returns `ChangePlan.exact()`, commit + dispense, no change path taken.
6. **Refund returns the customer's own money.** Insert ₹50 note then ₹10 note, then cancel: the refund is "50-rupee note, 10-rupee note" — insertion order, never composed from reserves.
7. **Zero-quantity restock / negative inputs.** `restock(code, -1)`, `loadChange(coin, -2)`, `planChange(-5)`: rejected at the boundary with explicit messages.
8. **`commitDispense` on empty stock.** Impossible via the state machine (it checks first), but guarded anyway: `IllegalStateException` naming the invariant that was violated — defence in depth for future callers.

## Testing Strategy

- Golden-log the full demo: every transaction narrative is deterministic.
- Two-pool invariants: after any sequence, `insertedTotal()` equals what the customer inserted minus what was refunded/committed; `reservesTotal()` only grows at commit and only shrinks under a dispensed plan.
- Change planning: for every amount 0..₹400 against seeded reserves, assert plan ≠ null ⇒ `dispenseChange(plan)` succeeds and `totalRupees() == amount`; plan == null ⇒ no subset of reserves sums to the amount *when a greedy plan doesn't exist* (the honest decline contract).
- Refund ordering: insertion order preserved; refunded total == inserted total.
- State guards: every (state, event) pair in the transition table asserts both the action and the next state, including the rejects.
