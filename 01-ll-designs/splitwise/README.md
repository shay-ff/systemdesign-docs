# Splitwise — Low Level Design

A classic interview LLD: model expense groups with equal, percent and exact splits, maintain a pairwise "who owes whom" ledger to the paisa, simplify those debts into a minimal set of payments, and settle up — the problem where money arithmetic, not modelling, is what actually fails candidates.

## Problem Statement

Design a expense-sharing system (Splitwise). Users register, form groups (flat-share, trip, dinner club), and one member pays for things on behalf of the group. Each expense is split among participants — equally, by percentage, or by exact amounts. The system maintains per-group balances of who owes whom, can suggest a **minimal set of payments** that settles the whole group, and records actual settlements. Members with open balances cannot leave a group.

## Clarifying Questions to Ask the Interviewer

Asking these up front is half the round — they each change the design:

1. **Currency precision** (THE headline question): `Rs.1000.00 / 3 = 333.33 each, but 333.33 x 3 = 999.99` — a paisa short. Who absorbs the remainder? Is off-by-a-paisa acceptable? → floor every share, hand the remainder to the last participant; store money as integer paise, never `double`. See explanation.md — this is the question that separates candidates who have thought about ledgers from those who have not.
2. **Split types**: equal only, or also percent and exact? Can one expense mix types ("Anjali pays exact 200, the rest split the remainder equally")? → we support all three, but reject *mixed* lists rather than half-validate them; the remainder-split extension is discussed in explanation.md.
3. **Who is a participant?** Only group members, or can outsiders (the guest at dinner) be included? → members only; an expense naming a non-member is a modelling error and is rejected, not silently tolerated.
4. **Simplify vs raw ledger**: do we show raw pairwise debts, a simplified minimum-payment plan, or both? → both: the ledger keeps the truth (per-expense edges folded into pairwise balances); simplification is a *derived view* computed on demand and never overwrites the ledger.
5. **Removal semantics**: what happens when a member with open balances leaves the group? → blocked until they settle; otherwise their balances become unenforceable.
6. **Multi-group balances?** Does a user's debt to the same person across two groups net into one number? → no; ledgers are per-group (what the real Splitwise UI does). Cross-group netting is the "IOU vs netting" extension question below.
7. **Currency**: single currency? → yes, one implicit currency; multi-currency needs FX rates and is an extension, not the core.

## Functional Requirements

- Register users (unique id, name, email) and create groups of registered users.
- Add expenses to a group: amount, payer, and a split list (all-equal, all-percent, or all-exact).
- Equal splits support share weights ("the couple pays for 2 people") without needing percentages.
- Compute every participant's share **exactly**: shares always sum back to the expense total to the paisa, no matter how ugly the division.
- Validate: percent splits must sum to exactly 100%; exact splits must sum to the expense amount; no duplicate participants; payer and every participant must be group members.
- Maintain per-group net balances per user (positive = is owed money) and the raw pairwise "who owes whom" view.
- Suggest a minimal set of payments (greedy debt simplification) that settles the group's current debts.
- Record actual settlements (real payments between two members), including partial and over-payments.
- Block removal of a member who still owes or is owed money in the group.

## Non-Functional Requirements

- **Exact arithmetic**: money is `long` paise in the core and `BigDecimal` only at the API edges; percentages are integer basis points. Never `double` for money — a ledger that drifts by a paisa cannot be audited, and interviewers at fintech companies actively probe this.
- **Fail-fast validation**: invalid expenses (bad percents, non-member participants, mixed split types) are rejected at construction with a specific message; nothing half-validated ever reaches the ledger.
- **Extensible (OCP)**: a new *self-contained* split type (one whose amount it can compute from its own data, like percent or exact) drops in as a new `Split` subclass without touching share computation; only the list-level validation in `Expense` learns the new rule.
- **Thread-safety, honestly scoped**: repository reads are lock-free (`ConcurrentHashMap`); ledger writes serialize behind one `ReentrantLock` because booking one expense is a multi-key read-modify-write. Per-group lock striping is the stated next step.
- **Auditable**: the ledger never invents or destroys money — every invariant (shares sum to total, nets sum to zero) is checked at the boundary where it could first break.

## Core Entities

| Entity | Role |
|---|---|
| `User` | Registered person; immutable id, id-based equality; deliberately no balance fields |
| `Group` | Members + expenses; the scope for settle-up; validates membership on every expense; blocks removal of members with open balances |
| `Split` | Abstract: how ONE participant's share is *described* (data, not computation) |
| `EqualSplit` | "Equal share", with an optional weight (2 shares = the couple pays for two) |
| `PercentSplit` | "P percent of the expense", stored in integer basis points |
| `ExactSplit` | "Exactly this amount", given in rupees, stored in paise |
| `Expense` | Amount (paise), payer, split list; immutable; owns cross-split validation |
| `ExpenseService` | Pure function object: turns split *descriptions* into concrete paise shares; owns the rounding rule |
| `Transfer` | Directed flow "A owes B X" — used for both raw per-expense edges and settlement suggestions |
| `BalanceService` | The ledger: signed pairwise balances per group, folds debts, records settlements |
| `SimplifyDebtService` | Greedy min-cash-flow over per-user nets: the minimal-payment suggestion |
| `SplitwiseService` | Facade wiring repos + services; what a controller would talk to |
| `UserRepository` / `GroupRepository` | Storage contracts (the persistence seam) |
| `InMemoryUserRepository` / `InMemoryGroupRepository` | Thread-safe in-memory implementations |

## Design Patterns Used (and why)

- **Repository** — `UserRepository` / `GroupRepository` interfaces with in-memory implementations. The service layer depends on the contracts, so "in production this is Postgres-backed" is a drop-in swap, not a rewrite. This is the standard LLD persistence seam.
- **Facade** — `SplitwiseService`. Controllers need one entry point; behind it the expense, balance and simplification services stay separate and separately testable. `addExpense` shows the orchestration in four lines: validate membership via the group → compute shares → persist the expense → book transfers into the ledger.
- **A closed inheritance hierarchy for splits — deliberately NOT a Strategy.** The tempting move is a `SplitStrategy` with a `computeShare(amount)` method. But a `Split` is *data*, not behaviour: it describes a constraint ("33.33% of this"), and the computation that turns descriptions into money lives in `ExpenseService`, because only there is the whole list known (an equal split's amount depends on how many other splits share the expense). `EqualSplit.computeShareInPaise` honestly throws `UnsupportedOperationException` instead of faking an answer — forcing polymorphism where the data is not self-contained is worse than an honest "cannot". See explanation.md for the full argument.
- **Dependency injection** — `SplitwiseService` receives every collaborator; `ExpenseService` and `SimplifyDebtService` are stateless and trivially unit-testable.

## How to Run

```bash
cd solutions/java

# Java 22+ (single-file source launcher handles sibling classes):
java SplitwiseDemo.java

# Java 11+ (classic):
javac *.java && java SplitwiseDemo
```

The demo runs a setup section plus ten steps over one Bengaluru flat-share group: a clean equal split (3000/4), the unclean one (1000/3 — the paisa remainder), a percent split plus a rejected 99% expense, an exact split, a rejected non-member expense, raw pairwise balances, greedy simplification, a real settle-up, the bookkeeping residue it leaves behind, and a blocked-then-allowed member removal.

## Extension Questions Interviewers Ask

1. **"Remove a member who still has balances."** — Already in: `Group.removeMember` refuses while `BalanceService.userHasOpenBalanceInGroup` is true ("Settle up first"). If forced removal is required, the options are: auto-book their net as settlements against the remaining members (money is conserved but routes are chosen for the users), or carry their balance as an external IOU outside the group ledger — discuss the trade-off, don't just implement one.
2. **"Multi-currency?"** — Every amount needs a currency code; percents become meaningless across currencies (percent of *which* amount), FX conversion introduces rounding *and* a rate timestamp, and nets only fold within a currency. A `Money` value object (currency + paise) is the entry point.
3. **"Edit or delete an expense."** — The ledger is a fold of derived transfers, so an edit is: reverse the old expense's transfers (apply them negated), then book the new ones. This is why `applyTransfer` takes signed deltas — reversal is the same operation with flipped endpoints.
4. **"IOU vs netting — why store pairwise at all if you only ever show nets?"** — Because "who owes whom" is a product surface (real Splitwise shows it), and because a settlement between two specific people must shrink *their* pair, not the group's nets. Nets are the derived view; pairwise is the source of truth.
5. **"Database schema?"** — `users(id, name, email)`, `groups(id, name)`, `group_members(group_id, user_id)`, `expenses(id, group_id, payer_id, amount_paise, description, created_at)`, `expense_splits(expense_id, user_id, split_type, split_value, computed_share_paise)`, and either a `pair_balances(group_id, user_a, user_b, net_paise)` projection or pairwise balances computed on read from the expense log. The repository seam is where this discussion lands.
6. **"Millions of groups in parallel?"** — The current single `ReentrantLock` serializes all ledger writes; the stated next step is per-group lock striping (or per-group ledger objects), after which groups scale independently.

## Learning Objectives

- Exact money arithmetic: integer paise in the core, `BigDecimal` at the edges, basis points for percents, and a deterministic remainder rule
- A signed, canonical-key pairwise ledger that folds directed debts automatically
- Greedy min-cash-flow debt simplification with two heaps — and an honest account of what it does and does not guarantee
- The repository seam and facade orchestration that keep an LLD honest
- Knowing when *not* to reach for a pattern (why splits are a closed hierarchy, not strategies)

For the entity-by-entity walkthrough, rejected alternatives and complexity analysis, see [explanation.md](explanation.md).
