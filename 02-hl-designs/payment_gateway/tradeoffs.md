# Payment Gateway — Design Decisions & Trade-offs

Each decision states the choice, the alternatives, and why. In a payments
interview, the reasoning matters more than the boxes — anyone can draw a
gateway; the signal is knowing what correctness each choice buys.

---

## 1. Idempotency: keys vs. hope

**Decision:** mandatory idempotency keys on every mutating request, enforced by
a conditional insert.

**Alternatives:**
- *No keys, rely on clients not retrying:* naive; networks force retries, so
  double charges are inevitable.
- *Dedupe by request body hash:* breaks when two legitimate identical payments
  are made (same amount, same time) — they'd collapse into one. The client-
  supplied key disambiguates "retry" from "genuinely two payments".
- *Exactly-once transport:* impossible over unreliable networks; every real
  system is at-least-once + dedupe.

Idempotency keys are the industry answer (Stripe, Razorpay both require them).
The conditional insert (`SET NX`) is what makes concurrent duplicates safe.

---

## 2. Money representation: integer minor units vs. decimal vs. float

**Decision:** `bigint` in the smallest currency unit (paise/cents).

- *Float:* never — `0.1 + 0.2 != 0.3` in binary floating point; money bugs.
- *Decimal:* correct but slower and requires care with rounding rules across
  currencies.
- *Integer minor units:* exact, fast, unambiguous. ₹500.00 = `50000`. The only
  care needed is currency-specific exponents (JPY has no minor unit; some
  currencies have 3 digits) — handled by storing the currency alongside.

---

## 3. Ledger: append-only double-entry vs. mutable balances

**Decision:** append-only double-entry ledger; balances are projections.

- *Mutable balance column:* simple but dangerous — a lost update or a partial
  write silently corrupts the balance, and there's no audit trail. Under
  concurrency it needs locks.
- *Append-only double-entry:* money is provably conserved (debits = credits),
  immutable (auditable), and lock-free under concurrency (no shared mutable
  balance to race on). Corrections are compensating entries.

The cost is that "what's this merchant's balance?" is a projection (a scan or a
cached snapshot), not a column read. That's the right trade for financial
correctness.

---

## 4. Payment state: explicit state machine vs. status flags

**Decision:** an explicit state machine with legal transitions.

- *Boolean flags* (`is_authorized`, `is_captured`, `is_refunded`): combinatorial
  nonsense — 2ⁿ illegal states, and "authorized AND refunded" can be set by a
  bug.
- *Single status field with enforced transitions:* a payment is in exactly one
  state; illegal transitions throw. Prevents "half-paid" corruption and makes
  the system's behavior provable.

---

## 5. Authorization: synchronous vs. asynchronous

**Decision:** the merchant API returns fast (payment created); authorization
runs asynchronously with the outcome delivered via webhook.

- *Synchronous end-to-end:* the merchant's latency equals the rail's latency
  (seconds for netbanking/3DS), which is unacceptable and couples your
  availability to the rail's.
- *Asynchronous (chosen):* your API is fast and stable; the slow, flaky rail is
  isolated behind a worker and a queue. The merchant learns the outcome via a
  webhook. The cost is that merchants must handle async outcomes — a real
  integration burden, documented loudly.

For fast rails (UPI), the response can include the outcome inline; the
architecture supports both, but the async path is the backbone.

---

## 6. Webhooks: at-least-once vs. exactly-once

**Decision:** at-least-once with retries; merchants dedupe by event id.

- *Exactly-once delivery:* impossible (two-generals again). Any claim of it is
  at-least-once + dedupe.
- *At-most-once (no retry):* loses events when the merchant is briefly down —
  unacceptable for payment notifications.
- *At-least-once (chosen):* retries until acked; the merchant dedupes by event
  id. The trade is that merchants *must* be idempotent — the single most common
  integration mistake, so the gateway documents it and even offers a replay API.

---

## 7. Reconciliation: continuous vs. daily batch

**Decision:** daily batch as the authoritative backstop, with optional streaming
for early detection.

- *Continuous only:* catches issues fast but can't handle the bank's batch
  settlement files (which arrive daily); no final backstop.
- *Daily batch only:* correct but detects issues up to a day late.
- *Both (chosen):* streaming gives early warning; the daily batch against the
  bank's file is the authoritative reconciliation. Since the bank is the source
  of truth and operates on a daily cadence, the daily batch is non-negotiable.

---

## 8. Card data: store vs. tokenize

**Decision:** never store PANs; tokenize at the edge.

- *Store PANs:* maximum PCI-DSS scope (the most expensive compliance regime);
  a breach is catastrophic.
- *Tokenize (chosen):* store only an opaque token + last4 + expiry. The vault
  (or the processor) holds the PAN. Drastically reduces PCI scope and breach
  blast radius.

This is a compliance-driven architectural decision — the kind interviewers at
fintechs specifically probe.

---

## 9. Processor integration: single vs. multi-acquirer routing

**Decision:** multi-acquirer with a routing layer.

- *Single acquirer:* simple, but that acquirer's downtime is your outage, and
  you can't optimize cost/success rate.
- *Multi-acquirer routing (chosen):* route by method, cost, and live success
  rate; fail over on acquirer trouble. The cost is a uniform adapter interface
  and reconciliation across multiple processors — worth it for resilience and
  economics.

---

## 10. Consistency: uniform vs. layered

**Decision:** strong where money correctness demands it; eventual + reconciled
elsewhere.

| Data | Consistency | Why |
|---|---|---|
| Payment state | Strong | One authoritative state |
| Idempotency | Linearizable | The no-double-charge guarantee |
| Ledger | Strong, immutable | Financial truth |
| Settlement | Eventual, reconciled | Bank is authoritative; we converge |
| Webhooks | At-least-once | Merchant dedupes |

You cannot make a bank strongly consistent with you. Pretending to would be a
lie; reconciling is the honest, correct answer.

---

## Summary table

| # | Decision | Chosen | Chief alternative | Why |
|---|---|---|---|---|
| 1 | Idempotency | Mandatory keys + conditional insert | Body-hash dedupe | Disambiguates retry from a real second payment |
| 2 | Money | Integer minor units | Decimal / float | Exactness |
| 3 | Ledger | Append-only double-entry | Mutable balances | Conservation + audit + lock-free |
| 4 | State | Explicit state machine | Boolean flags | No illegal states |
| 5 | Authorization | Async + webhook | Sync end-to-end | Decouple from rail latency |
| 6 | Webhooks | At-least-once | Exactly-once (impossible) | Reliability + merchant dedupe |
| 7 | Reconciliation | Daily batch (+ streaming) | Continuous only | Bank file is the backstop |
| 8 | Card data | Tokenize | Store PAN | PCI scope |
| 9 | Processor | Multi-acquirer routing | Single acquirer | Resilience + cost |
| 10 | Consistency | Layered | Uniform | Match cost to correctness need |
