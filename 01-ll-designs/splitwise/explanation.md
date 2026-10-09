# Splitwise — Design Explanation

A walkthrough of every entity, why it exists in that shape, what was rejected, and where the money arithmetic actually bites.

## Entity-by-Entity Rationale

### `User` — deliberately dumb

Immutable id, name, email; id-based equality (`equals`/`hashCode` on id only) so the same person can sit in many groups without duplicating ledger entries. There are **no balance fields**, and that is a decision, not laziness: balances are a *relationship between users within a group*, so they live in `BalanceService`'s ledger. Putting `amountOwed` on `User` couples the entity to groups, forces the ugly question "owed *to whom*, in *which* group?", and makes every expense add a write to N user objects. The ledger is one map; `User` stays a token.

### `Group` — the scope of everything

Members (a `LinkedHashSet` of ids) plus the expenses added against it. The group is the unit of settle-up — net balances and simplification are computed **per group**, which is exactly what the real Splitwise UI does; you settle your flat, not your life.

`Group.addExpense` enforces the membership invariant: the payer and *every* split participant must be members. An expense naming a non-member is a modelling error — rejected, not swallowed. `Group.removeMember(userId, balanceService)` is the interesting one: it refuses while the member still owes or is owed money in this group ("Settle up first"), because a departed member's balances are unenforceable. Note the dependency direction — the group *asks* the ledger rather than owning balance data, keeping exactly one source of truth for money.

### `Split` and friends — data, not strategies

The tempting design is a `SplitStrategy` interface with `computeShare(amount)`. Look at what each split type actually knows:

- `ExactSplit` is self-contained: its amount is its own data (`computeShareInPaise` just returns it).
- `PercentSplit` is self-contained: `total × basisPoints / 10000`, no other split needed.
- `EqualSplit` is **not** self-contained: its amount depends on how many *other* splits share the expense — and their weights.

So the subclasses differ only in the constraint they place on themselves and how they describe their share; the *computation* of concrete money lives in `ExpenseService`, where the whole list is visible. That makes `Split` a small, closed is-a hierarchy (data with a contract), not a Strategy. The proof that this is deliberate: `EqualSplit.computeShareInPaise` **throws** `UnsupportedOperationException` rather than faking an answer. Forcing polymorphism where the data is not self-contained is worse than an honest "cannot" — say that sentence in the interview.

Two more details worth copying:

- `PercentSplit` stores percents as **integer basis points** (`3333` = 33.33%). `0.1 + 0.2 != 0.3` applies to percents as much as to money; `33.33` as a `double` is already wrong before any arithmetic starts.
- `EqualSplit` carries an optional **weight** (`new EqualSplit("u1", 2)` = "the couple pays for two"), which covers the most common real-world weighted case without dragging percentages in.

### `Expense` — immutable, self-validating

Amount (converted to paise in the constructor), payer, defensive-copied split list. All validation that needs the *whole list* happens here, in `validateSplits`:

1. no duplicate participants;
2. if all splits are percents, they must sum to **exactly 100.00%** (in basis points, an integer equality — no epsilon);
3. if all exact, they must sum to the expense amount (in paise, another integer equality);
4. anything mixed is **rejected** rather than half-validated.

Point 4 is an invariant worth stating out loud: a mixed list ("exact 200, the rest equally") could be supported, but then neither the percent-sum nor the exact-sum check applies, and you have invented a fourth split semantics nobody asked for. Reject, and offer the extension as a discussion.

`Expense` also exposes tiny factories (`equalAmong`, `percentAmong`) that keep the demo readable — sugar, not design.

### `ExpenseService` — where rounding lives

A pure function object: no state, no repository access, trivially unit-testable. `computeShares(expense)` returns an ordered map of participant → paise, **guaranteed to sum back to the expense total exactly**.

The equal-split path is the heart of the problem. `Rs.1000.00 / 3 = 333.33 each`, but `333.33 × 3 = Rs.999.99` — one paisa short, and a ledger that loses a paisa per expense cannot be audited. The rule the code implements (and the demo prints):

- totalShares = sum of all equal-split weights;
- every participant except the **last** gets `floor(total × shares / totalShares)`;
- the **last** split entry gets `total − running` — the remainder, whatever it is.

So `Rs.1000.00` among three comes out `333.33, 333.33, 333.34`: the discrepancy is at most (n−1) paise, invisible in rupee terms, and nothing is dropped — the leftover is *assigned*. The last-absorbs rule is deterministic and requires no configuration; "round half even each and eat the difference" is not, because it can leave the total off by a paisa. (Who absorbs it is arbitrary — real Splitwise doesn't promise either — but it must be *somebody*, deterministically.)

The percent/exact path is simpler because those splits carry their own amounts, already validated to sum correctly at construction. `computeShares` still re-verifies the sum — defence in depth: if the invariant ever breaks, it breaks *here*, loudly, before the ledger is touched.

`deriveTransfers` then turns shares into the per-expense ledger edges: everyone except the payer owes the payer their share — a list of `Transfer`s shaped `debtor → payer`.

### `Transfer` — one value class, two jobs

A directed flow "fromUser owes toUser amount". It serves both the raw per-expense edges out of `ExpenseService` and the settlement suggestions out of `SimplifyDebtService` (`expenseId` is null for the latter). One small immutable value class keeps the whole pipeline — and the demo output — uniform. Zero/negative amounts and self-transfers are rejected in the constructor: a ledger line that books "A owes A 0" is noise that corrupts "pair count" semantics downstream.

### `BalanceService` — the ledger, and its orientation

The data model is the crux; read it twice:

- `groupToPairBalances.get(groupId)` is a `TreeMap<"aId|bId", amount>` where `aId < bId` lexicographically — so an unordered pair has **exactly one key**, no matter which direction each original transfer flowed.
- The amount is **signed**: `+x` means `aId` owes `bId` `x`; `−x` means `bId` owes `aId` `x`; `0` means settled and the entry is removed.

Why signed? Debts are *directed* (A owes B is not B owes A) but they fold against each other: "A owes B 500" plus "B owes A 200" is just "A owes B 300". A single signed entry per canonical pair captures both directions and performs the folding automatically — `applyTransfer` computes the pair key, expresses the directed amount as a signed quantity on that key, and adds. No "find the reverse edge and merge" branching anywhere.

Reads: `getPairwiseBalances` re-hydrates the signed entries into canonical `debtor → creditor` transfers with strictly positive amounts (the "who owes whom" product view, pre-simplification). `getNetBalances` folds those into one number per user (positive = is owed), dropping users whose contributions cancel — so "empty map" reliably means "group fully settled". `userHasOpenBalanceInGroup` powers the removal guard.

`recordSettlement` is the one subtle write: a real payment is an asset transfer in the *opposite* bookkeeping direction of a debt, so "u2 paid u1 500" is booked as "u1 owes u2 500" — the sign flip is what makes balances *shrink* when people pay. Partial and over-payments just work: the pair shrinks or flips direction, exactly like real bookkeeping.

**Concurrency, honestly scoped.** Writes funnel through one `ReentrantLock`. Why not just `ConcurrentHashMap`? Because booking one expense is a read-modify-write across *several* pair keys; CHM makes individual operations atomic, not multi-key sequences. A coarse lock is a deliberate simplification: obviously correct, and the stated next step is per-group lock striping (say that out loud — the honest answer beats a hand-waved "it's thread-safe").

### `SimplifyDebtService` — the star feature

Given pairwise debts, the raw view has more transactions than necessary. Debt is transferable within a group, so the *same* net positions can be settled with fewer payments. The algorithm is greedy min-cash-flow:

1. Collapse everything to one net number per user (positive = is owed, negative = owes). Zero-net users drop out naturally.
2. Split into a **max-heap of creditors** and a **min-heap of debtors** (most extreme first).
3. Repeatedly poll both tops, transfer `min(creditor.net, −debtor.net)`, push back whichever side still has a remainder.
4. Stop when both heaps are empty; the accumulated transfers are the suggestion.

**Why this is safe — the honest argument.** Every transfer moves real money between real net positions, so the invariant "sum of nets == 0" (validated up front, with a specific error if the caller's ledger is corrupt) guarantees the suggestion preserves everyone's net exactly. Executing it leaves every net at zero: the group is genuinely settled. And each iteration settles *at least one* user fully (whichever of the two tops is smaller hits zero), so the loop runs at most `n−1` times, where `n` is the number of users with non-zero net — the classic upper bound for "minimum transactions to settle debts", and the bound the demo hits (4 netted users → 3 payments settle what 6 pairwise debts would).

**What it does not guarantee** — and saying so earns points: `n−1` is an upper bound, not always the theoretical minimum. Consider nets `A +100, B +100, C −50, D −150`: greedy matches D−150 against A+100 (A retired, D left at −50), then D against B — two payments, which happens to be optimal. But nets `A +100, B +100, C −200` *can* be settled in two payments (`C → A 100`, `C → B 100`) and greedy also produces two (n−1 = 2) — the gap appears when a *subset* of debtors exactly matches a subset of creditors (`A +100, B −50, C −50, D... `): pairing whole subsets at once can beat pairwise greedy by a payment. Finding the absolute minimum is a subset-sum-style NP-hard search over which subsets of debtors exactly match which subsets of creditors. Greedy delivers `≤ n−1` in `O(n log n)`; the exact optimum costs exponential time for a saving of at most a payment or two. In an interview, present greedy, state the bound, name the NP-hard optimum — done.

Complexity of one `simplify` call: `O(n log n)` — each user enters a heap once per time they're re-pushed, and every iteration retires at least one user.

### `SplitwiseService` — facade, orchestration only

What a controller would talk to. `addExpense` is the whole pipeline in four lines: `requireGroup` → `group.addExpense` (membership + split integrity) → `expenseService.deriveTransfers` → `balanceService.applyTransfers`. The repos do persistence, the group does membership validation, the expense does self-validation, the services do computation — the facade only sequences them. Both constructors accept injected collaborators (a convenience constructor wires defaults), which is what makes the whole thing unit-testable without mocks of any sophistication.

`settleUpGroup` shows the last subtlety: it books every suggested payment (via `recordSettlement`, so the ledger actually moves), *then* calls `BalanceService.settleUpGroup` — and the order matters. See below.

### Repositories — the persistence seam

`UserRepository` / `GroupRepository` are two-method contracts (`save`, `findById`); the in-memory implementations are `ConcurrentHashMap`-backed with `putIfAbsent`-style duplicate rejection. Reads are lock-free and single-map operations are atomic, which is *all* the atomicity these repositories need (unlike the ledger's multi-key writes). The service layer depends on the interfaces, so "in production this is Postgres-backed" is a drop-in swap — the standard LLD move.

## The Settle-Up Nuance the Demo Shows (Step 9)

This is the most instructive behaviour in the whole problem. The raw pairwise view (demo Step 6) is six pairs: u1 owes u2 `Rs.1250.00`, u3 owes u1 `Rs.416.67`, u4 owes u1 `Rs.550.00`, u3 owes u2 `Rs.666.67`, u4 owes u2 `Rs.259.50`, u3 owes u4 `Rs.200.00`. Per-user nets: u2 is owed `Rs.2176.17`; u3 owes `Rs.1283.34`, u4 owes `Rs.609.50`, u1 owes `Rs.283.33`. The greedy suggestion (Step 7) is three payments: **u3→u2 `Rs.1283.34`, u4→u2 `Rs.609.50`, u1→u2 `Rs.283.33`** — everyone pays Rahul directly. The flatmates pay exactly those (Step 8). Each payment is individually correct — it shrinks or flips *its own pair*. But those routes **cross** the original debt routes: u3 owed u1 `Rs.416.67` and u4 owed u1 `Rs.550.00`, yet both paid u2. The pairs `u1|u3`, `u1|u4` and `u3|u4` are never touched by any payment, while `u2|u3` and `u2|u4` each *flip direction* (u3 owed u2 666.67 → u2 now owes u3 616.67; u4 owed u2 259.50 → u2 now owes u4 350.00) and `u1|u2` shrinks (1250.00 → 966.67).

The result, which the demo prints explicitly (Step 9): **every net balance is zero — the group is economically settled — yet all 6 pairwise entries remain non-zero** (u1 owes u2 966.67, u3 owes u1 416.67, u4 owes u1 550.00, u2 owes u3 616.67, u2 owes u4 350.00, u3 owes u4 200.00). Offsetting cycles survive in bookkeeping: A owes B, B owes C, C owes A, summing to nothing. Each entry is real in the sense that it records an unresolved *pairwise* claim, but the claims are unenforceable in aggregate — nobody can extract money from a cycle whose net is zero.

`BalanceService.settleUpGroup` encodes the policy: if every net is zero, the pairwise residue is noise — clearing the group's pair map is a financial no-op. The method *requires* that precondition (it refuses to clear real debt; `recordSettlement` first) and then drops the residue. That is why `SplitwiseService.settleUpGroup` books the suggested payments *before* clearing — clear first and you would erase genuine debt.

The design lesson: **nets and pairs are different objects.** Nets answer "is the group square?"; pairs answer "who owes whom specifically?". Payments move pairs; settlements' *purpose* is to zero nets; and the gap between the two views is where this nuance lives.

## Pattern Choices and Rejected Alternatives

| Choice | Rejected alternative | Why |
|---|---|---|
| `long` paise in the core, `BigDecimal` only at API edges | `double` everywhere | 0.1+0.2 ≠ 0.3; a drifting ledger cannot be audited |
| `BigDecimal` at the edges (constructors, `formatRupees`) | raw `long` paise in public APIs | Callers write `new BigDecimal("1000.00")`, not `100000`; the edges stay human, the core stays exact |
| Integer basis points for percents | `double` percents | 33.33 is not representable; the 100% check becomes an epsilon comparison instead of `==` |
| Floor + last-absorbs-remainder | Round each share independently | Independent rounding can leave the total off by a paisa; the remainder must be *assigned*, not dropped |
| Signed canonical-key pairwise map | Two entries per pair (`A→B`, `B→A`) | Folding becomes merge-and-delete logic across two keys; one signed entry folds by addition |
| Closed `Split` hierarchy | `SplitStrategy` with `computeShare(amount)` | An `EqualSplit` cannot answer without seeing the whole list; a forced strategy interface either lies or throws — as ours honestly does |
| Per-group ledgers | One global per-user balance | "Who owes whom" is per-group; cross-group netting silently mixes social contexts (see IOU extension) |
| Coarse `ReentrantLock` on the ledger | `ConcurrentHashMap` alone | Booking an expense is a multi-key read-modify-write; CHM atomicity is per-key, not per-transaction |
| Simplification as a derived view | Simplify in place, overwrite the ledger | The raw pairwise view is a product surface and the audit trail; suggestions must never destroy truth |

## Trade-offs

- **Exactness vs convenience**: paise arithmetic is exact but every display point needs `formatRupees`; `BigDecimal` at the edges keeps callers human. Worth it — money is the one domain where you never get to be approximately right.
- **Coarse lock vs striping**: one lock serializes all groups' ledger writes. Obvious correctness now, named next step (per-group lock objects) later — better than a subtle broken concurrency story.
- **Last-absorbs-remainder vs "round half even"**: the last participant can be off by up to (n−1) paise from their "true" share. Deterministic, totals exact, invisible at rupee granularity; real Splitwise makes the same class of choice.
- **Rejecting mixed split lists vs supporting them**: fewer features, but every accepted expense satisfies a checkable invariant. Mixed lists would need a fourth semantics ("exact first, remainder equally") — an extension, not a default.

## Complexity

| Operation | Time | Space |
|---|---|---|
| Add an expense (compute shares + book k transfers) | O(k) amortized, k = participants | O(k) ledger entries touched |
| Equal split computation | O(k) | O(k) |
| `getNetBalances` / `getPairwiseBalances` | O(p log p), p = open pairs (TreeMap) | O(p) |
| `suggestSettlement` (simplify) | O(n log n), n = users with non-zero net | O(n) |
| Ledger space | O(p) total open pairs, sparse — settled pairs are removed | — |

## Classic Edge Cases

1. **Rs.1000.00 among three** — 333.33 / 333.33 / 333.34; the last split entry absorbs the paisa (demo Step 2, printed explicitly).
2. **Percents summing to 99%** — rejected at construction with the actual sum in the message (demo Step 3).
3. **Exact splits not summing to the total** — rejected, both amounts printed (construction-time check).
4. **Non-member participant** — registered user, not in the group: rejected by `Group.addExpense` (demo Step 5).
5. **Settle along simplified routes** — nets all zero, pairs all non-zero: offsetting cycles survive (demo Step 9, printed with the residue listed).
6. **Over-payment** — a settlement larger than the pair's debt flips the pair's direction instead of corrupting it; money is conserved.
7. **Removal with open balances** — `IllegalStateException` naming the user and group; after `settleUpGroup`, removal succeeds (demo Step 10).
8. **Corrupt net map fed to simplify** — non-zero sum is rejected with "too much credit"/"too much debt" — the algorithm refuses to invent or destroy money.

## Testing Strategy

- **Golden demo**: the entire ten-section log is deterministic — run it, diff it.
- **Rounding**: parametrized cases `1000/3`, `100/3`, `1/3`, weighted equal splits (`shares=2`), asserting shares sum to the total *exactly* every time.
- **Validation**: every rejection branch gets a test asserting the exact message (99% percents, exact-sum mismatch, duplicate participant, non-member, mixed list).
- **Ledger invariants**: after every operation, assert sum of nets == 0 and pair count matches expectation — property-style, over random expense sequences.
- **Simplification**: nets preserved exactly; count ≤ n−1; degenerate cases (all zero, single creditor, single debtor) behave.
- **Settle-up residue**: book simplified payments, assert nets empty while pairs non-empty, then assert `settleUpGroup` clears them.
