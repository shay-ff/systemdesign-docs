# Splitwise — Java Implementation

Java 11, no external libraries, no `package` declarations (repo convention: one top-level class per file, compiled side by side).

## Design Patterns

- **Repository** — `UserRepository` / `GroupRepository` interfaces with in-memory implementations; the service layer depends on the contracts, so a real database is a drop-in swap.
- **Facade** — `SplitwiseService`: one entry point for controllers; `addExpense` orchestrates validate → compute → persist → book in four lines.
- **Closed inheritance hierarchy for splits (deliberately not Strategy)** — `Split` describes *data* ("33.33% of this"); computation lives in `ExpenseService` where the whole list is known. `EqualSplit.computeShareInPaise` honestly throws rather than fake an answer.
- **Dependency injection** — every collaborator of `SplitwiseService` is injected; `ExpenseService` and `SimplifyDebtService` are stateless pure-function objects.

## Class-by-Class

| File | Class | Responsibility |
|---|---|---|
| `User.java` | `User` | Registered person; immutable id/name/email; id-based equality; no balance fields (balances belong to the ledger) |
| `Group.java` | `Group` | Members + expenses; membership validation per expense; `removeMember` blocked while the member has open balances |
| `Split.java` | `Split` (abstract) | How one participant's share is *described*; paise-based `computeShareInPaise` contract; `formatRupees` helper |
| `EqualSplit.java` | `EqualSplit` | Equal share with optional weight (`shares=2` = the couple pays for two); `computeShareInPaise` intentionally throws — depends on the other splits |
| `PercentSplit.java` | `PercentSplit` | P percent, stored as integer basis points (`3333` = 33.33%); validates `(0%, 100%]` |
| `ExactSplit.java` | `ExactSplit` | Exactly this amount; rupees in (`BigDecimal`), paise stored; self-contained `computeShareInPaise` |
| `Expense.java` | `Expense` | Amount (paise), payer, split list; immutable with defensive copies; cross-split validation (no duplicates, percents sum to 100%, exacts sum to total, no mixing); `equalAmong`/`percentAmong` factories |
| `ExpenseService.java` | `ExpenseService` | Pure function object: `computeShares` (equal-split rounding: floor all but last, last absorbs the remainder — shares sum to the total exactly) and `deriveTransfers` (debtor→payer edges) |
| `Transfer.java` | `Transfer` | Directed flow "A owes B X"; used for both raw per-expense edges and settlement suggestions |
| `BalanceService.java` | `BalanceService` | The ledger: signed `TreeMap<"aId|bId", Long>` per group (canonical key, one entry per pair); folds debts by addition; `recordSettlement` books real payments (opposite direction); `getNetBalances` / `getPairwiseBalances`; `userHasOpenBalanceInGroup`; `settleUpGroup` clears residue only when every net is zero; coarse `ReentrantLock` |
| `SimplifyDebtService.java` | `SimplifyDebtService` | Greedy min-cash-flow: nets → max-heap of creditors vs min-heap of debtors → ≤ n−1 transfers; validates the sum-zero invariant; `O(n log n)` |
| `SplitwiseService.java` | `SplitwiseService` | Facade: users, groups, `addExpense` (validate → derive → book), balances, `suggestSettlement`, `recordSettlement`, `settleUpGroup` |
| `UserRepository.java` | `UserRepository` | Storage contract for users (`save`, `findById`, `findAll`) |
| `GroupRepository.java` | `GroupRepository` | Storage contract for groups (`save`, `findById`) |
| `InMemoryUserRepository.java` | `InMemoryUserRepository` | `ConcurrentHashMap`-backed; lock-free reads; duplicate-id rejection |
| `InMemoryGroupRepository.java` | `InMemoryGroupRepository` | `ConcurrentHashMap`-backed; duplicate-id rejection |
| `SplitwiseDemo.java` | `SplitwiseDemo` | Ten-section end-to-end demo with `===` headers |

## Run

```bash
# Java 22+ single-file source launcher (handles sibling types):
java SplitwiseDemo.java

# Java 11+ classic:
javac *.java && java SplitwiseDemo
```

## Demo Sections

Scenario: four flatmates (Anjali u1, Rahul u2, Vikram u3, Meera u4) in a Bengaluru flat group. The demo prints a setup section plus ten numbered steps:

0. **Setup** — register the four users, create the "Koramangala Flat 4B" group.
1. **Equal split that divides cleanly** — rent `Rs.3000.00` among 4: `Rs.750.00` each; per-user nets printed after the expense.
2. **Equal split that does NOT divide cleanly** — dinner `Rs.1000.00` among 3: `333.33 / 333.33 / 333.34` — the last split entry absorbs the paisa; shares sum back to exactly `Rs.1000.00`.
3. **Percent split (must sum to exactly 100%)** — Coorg trip `Rs.5000.00` split 40/30/20/10 (Meera took the master bedroom); then a percent expense summing to 99% is **rejected** with its actual sum printed.
4. **Exact split (must sum to the total)** — groceries `Rs.840.50` with exact per-person amounts; shares sum to the paisa.
5. **Validation — non-member participant** — Deepak is registered but not in the flat group; an expense naming him is **rejected**.
6. **Raw pairwise balances (before simplification)** — per-expense debtor→payer edges folded against each other: **6 distinct debtor-creditor pairs**.
7. **Debt simplification (greedy min-cash-flow)** — collapse to per-user nets, match largest creditor vs largest debtor with two heaps: **3 payments settle what 6 pairwise debts would take** (u3→u2 `Rs.1283.34`, u4→u2 `Rs.609.50`, u1→u2 `Rs.283.33`).
8. **The flatmates actually pay (settle up)** — each suggested payment recorded as a real settlement.
9. **Group is now square (but bookkeeping is not)** — every net is zero, yet **6 pairwise entries remain non-zero**: the flatmates paid along the simplified routes, which cross the original debt routes — offsetting cycles survive. Economically settled; bookkeeping noise remains (printed explicitly).
10. **Removing a member with open balances is blocked** — a farewell expense gives Vikram an open balance; removal is **rejected** ("Settle up first"); after `settleUpGroup` books the residue-clearing settle-up, removal succeeds and the group's remaining members are printed.

The demo is fully deterministic — rerun it and diff the log.
