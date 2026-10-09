# Mock Interview — LLD: Splitwise (Mid-Level)

**Format:** 45-minute low-level design round
**Level:** SDE-1 / SDE-2 (~2 years experience)
**Problem:** Design Splitwise — an expense-sharing system
**What's being tested:** requirements clarification, entity modelling, pattern
selection, the money-arithmetic edge case, and (the differentiator) concurrency.

This is a full mock: the problem, the interviewer's prompts, a strong candidate's
responses, and the rubric. Work it as a candidate first (set a 45-min timer),
then read the model answer.

---

## The prompt (as the interviewer delivers it)

> "Let's do a low-level design. Design Splitwise: users create groups, add
> expenses, and the system tracks who owes whom and can settle up. Walk me
> through your design. I'll ask questions as we go."

---

## Phase 1 — Clarify (minutes 0–8)

**Candidate asks (a strong set):**

1. "What split types? Equal, by percentage, or by exact amounts — or all three?"
   → *Interviewer: "All three."*
2. "Single currency? And how precise — do I need to handle a split that doesn't
   divide evenly, like ₹1000 across 3 people?"
   → *Interviewer: "Single currency, and yes, handle the rounding."* **(the key
   hook)**
3. "Do users see raw pairwise balances, or a simplified 'who pays whom'?"
   → *Interviewer: "Both — a raw view and a simplification."*
4. "Should removing a member with outstanding balances be allowed?"
   → *Interviewer: "Good question — what do you think?"* → *"I'd block it until
   they settle; otherwise their debt becomes unenforceable."* → *"Agreed."*
5. "Any concurrency to worry about — two people adding expenses simultaneously?"
   → *Interviewer: "Assume it could happen."*

**Why this is strong:** each question maps to a design decision (a `Split`
hierarchy; integer paise + a remainder rule; a simplification service; a
validation guard; thread-safety). The candidate surfaced the shaping decisions
in under 8 minutes.

---

## Phase 2 — Entities (minutes 8–13)

**Candidate:**

> "Core entities: **User**, **Group**, **Expense**, and a **Split** hierarchy
> (EqualSplit, PercentSplit, ExactSplit). Then services: an **ExpenseService**
> to compute shares, a **BalanceService** to track who owes whom, and a
> **SimplifyDebtService** to minimise the number of payments. And a
> **SplitwiseService** as the facade.

> Responsibilities: `Expense` knows its amount, payer, and splits. `Split`
> knows how to compute one participant's share. `BalanceService` is the ledger.
> `SimplifyDebtService` takes net balances and produces the minimal payment set."

**Interviewer:** "Why is `Split` its own hierarchy rather than a field on
Expense?"

**Candidate:** "Because the split *rule* varies and each rule computes a share
differently — equal divides, percent applies a ratio, exact takes a fixed
amount. That's a Strategy-shaped seam: a new split type is a new class, not a
branch in Expense."

---

## Phase 3 — Design (minutes 13–28)

**Candidate draws and narrates:**

```
User ───< Group ───< Expense ───< Split (abstract)
                                    ├── EqualSplit
                                    ├── PercentSplit
                                    └── ExactSplit
SplitwiseService (facade)
   ├── ExpenseService   (compute shares)
   ├── BalanceService   (signed pairwise ledger)
   └── SimplifyDebtService (greedy min-cash-flow)
```

**The money-arithmetic answer (the hook):**

> "The subtle part is the uneven split. ₹1000 across 3 people is 333.33 each,
> which sums to 999.99 — a paisa short. So I can't use floating point. I'll
> store everything as **integer paise** (₹1000.00 = 100000 paise), compute each
> share with integer division, and give the **remainder to the last participant**
> — 33333, 33333, 33334 — so the shares always sum exactly to the total. Same
> for percentages: validate they sum to 100%, and distribute the remainder the
> same way."

**Interviewer:** "Why not `double` or `BigDecimal`?"

**Candidate:** "`double` loses precision — 0.1 + 0.2 isn't 0.3, and in money that
compounds into real errors. `BigDecimal` is correct but heavier and you still
have to choose a rounding mode. Integer paise is exact, fast, and the rounding
rule is explicit — which is what an interviewer (and an auditor) wants to see."

**The balances answer:**

> "`BalanceService` keeps a signed map keyed by an unordered pair: `(a,b)` with a
> canonical order, and the value is signed — positive means a owes b. When an
> expense is added, I derive debtor→payer edges and fold them into the map.
> Balances are always a projection over the expenses; I never store a mutable
> balance that could drift."

**The simplification answer:**

> "To minimise payments, I collapse everything to one net number per user, then
> greedily match the largest creditor with the largest debtor using two heaps,
> transferring the smaller amount and re-inserting the remainder. That gives at
> most n−1 payments. It's the standard min-cash-flow approximation — provably
> minimal *number of transfers* is NP-hard, but n−1 is what people care about."

**Interviewer (the concurrency probe):** "Two expenses added to the same group
at the same time — what happens?"

**Candidate:**

> "The balance update is a read-modify-write across several pair keys, so a
> naive map update races. I'd guard writes with a lock — a `ReentrantLock` in
> BalanceService is the simple, obviously-correct choice. `ConcurrentHashMap`
> alone isn't enough because booking one expense touches multiple keys and needs
> to be atomic as a unit. The upgrade path for throughput is per-group lock
> striping, but for an LLD the coarse lock is the right call — correctness first."

---

## Phase 4 — Deep-dive (minutes 28–38)

**Interviewer:** "Walk me through 'A pays ₹1000 for dinner split equally among
A, B, C'."

**Candidate:**

> "`ExpenseService.computeShares` sees an EqualSplit among 3, computes
> 100000/3 = 33333 each with remainder 1 → last participant gets 33334. Since A
> is the payer, the transfers are: B owes A 33333, C owes A 33334. Those fold
> into the signed pairwise map. A's net is +66667, B's is −33333, C's is −33334,
> summing to zero."

**Interviewer:** "Now B and C settle with A. What happens?"

**Candidate:**

> "`recordSettlement(B→A, 33333)` and `(C→A, 33334)`. Settlements are credited
> in the opposite bookkeeping direction — paying a debt shrinks it. After both,
> every pair is zero and the group is square."

**Interviewer:** "And the edge case where the settlement routes differ from the
raw debts?"

**Candidate:** *(this is a genuinely subtle one)*

> "Right — if people pay along the *simplified* routes rather than the raw
> pairwise debts, each payment is correct but you can leave offsetting cycles:
> every net is zero, yet some pairs are still non-zero. Economically the group
> is settled; bookkeeping-wise there's residue. I'd handle it with an explicit
> 'settle up' that, once all nets are zero, clears the residual pairs — it's a
> no-op financially. It's the kind of bug that shows up only in production, so
> calling it out explicitly is worth it."

---

## Phase 5 — Extend (minutes 38–45)

**Interviewer:** "Add multi-currency support."

**Candidate:**

> "An `Expense` carries a currency. The complication is that balances across
> currencies can't be summed — you'd need either per-currency balance maps, or
> an FX conversion at a point in time. Simplest correct version: keep balances
> **per currency** and never mix; a 'total' view converts at the current rate
> and is explicitly approximate. The `Split` hierarchy doesn't change — this is
> a storage/aggregation concern, not a split-rule one."

**Interviewer:** "Good. That's time."

---

## Rubric (score each 1–5)

| Criterion | What a 5 looks like |
|---|---|
| **Clarification** | Asked about split types, currency/precision, balances view, removal semantics, concurrency — before designing |
| **Entity modelling** | Clean SRP: Split hierarchy, Expense, services separated; no God class |
| **Patterns** | Strategy for splits (justified), facade, repository — used deliberately, not decoratively |
| **Money arithmetic** | Recognised the integer-paise requirement *unprompted*; explained the remainder rule and rejected float |
| **Simplification** | Explained greedy min-cash-flow; knew the n−1 bound and the NP-hard caveat |
| **Concurrency** | Named the read-modify-write race and proposed a lock with a throughput trade-off |
| **Extensibility** | Absorbed multi-currency without touching the split hierarchy |
| **Communication** | Narrated reasoning; engaged with pushback instead of defending |

**A strong pass:** 5s on clarification, entities, money arithmetic, and
simplification; at least a 4 on concurrency and extensibility.

**The differentiators:** the *unprompted* integer-paise insight and the
*concurrency* answer. Most candidates miss both.

---

## What to take away

1. **Clarify before designing** — the money-precision question is the whole
   problem hiding in the requirements.
2. **Integer money, always** — say "paise, not floats" before you're asked.
3. **Find the varying seam** — the split rule → Strategy.
4. **Name the concurrency race** — read-modify-write on shared state is the
   classic gap for ~2 YOE candidates.
5. **Know your algorithm's limits** — greedy gives n−1, exact-min is NP-hard;
   saying so is senior.

Compare your attempt against the full solution in
[`../../../01-ll-designs/splitwise/`](../../../01-ll-designs/splitwise/README.md).

---

*Related: [LLD Round Guide](../../lld-round-guide.md) ·
[LLD Patterns Cheatsheet](../../lld-patterns-cheatsheet.md)*
