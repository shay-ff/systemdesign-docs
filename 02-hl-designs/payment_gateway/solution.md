# Payment Gateway — Complete Solution Walkthrough

The end-to-end narrative: how one card payment travels from merchant to settled
funds, then how each component scales. Read it as the answer you'd give in the
interview.

---

## Step 1 — Clarify and scope

Questions that change the design (full list in `requirements.md`):

1. **Which methods?** Cards, UPI, netbanking, wallets — each is a different
   rail with different latency and failure modes. *Support all; abstract behind
   a processor adapter.*
2. **Sync or async authorization?** *Async* — the rail is slow and flaky; the
   merchant API must be fast.
3. **Idempotency?** *Mandatory* — retries must never double-charge.
4. **Settlement?** *Yes* — batch to the merchant's bank, T+1/T+2.
5. **Refunds and chargebacks?** *Yes* — both.
6. **Reconciliation?** *Yes* — daily against the bank.

Scope: initiate → authorize → capture → refund → settle, with webhooks,
idempotency, a ledger, and reconciliation.

---

## Step 2 — Back-of-the-envelope scale

| Metric | Estimate | Derivation |
|---|---|---|
| Transactions/day | 100M | Given |
| TPS | ~1,200 avg, ~5,000 peak | 100M/86400, ×4 peak |
| Ledger rows/day | ~600M | ~6 entries per payment |
| Initiation latency | < 300ms | Client-facing budget |
| Authorization latency | 2–30s | The rail's clock, not ours |
| Reconciliation | daily, billions of rows | Full ledger vs. bank file |

The headline: **TPS is modest; correctness and the async rail are the hard
parts.** Say this explicitly — it signals you understand payments aren't a
throughput problem.

---

## Step 3 — API design

Full detail in `api-design.md`. The load-bearing decisions:

- **`Idempotency-Key` on every mutating request** — retries return the original
  result.
- **Amounts as integer minor units** — no floats.
- **Async outcomes via webhooks** — signed, retried, at-least-once.
- **Error model with a retry-safe 500** — retry with the *same* key.

---

## Step 4 — Data model

Two stores dominate (full detail in `database-schema.md`):

- **Payments** (Postgres, sharded by merchant): the state machine, with
  `UNIQUE(merchant_id, idempotency_key)` enforcing idempotency and a `version`
  for optimistic concurrency.
- **Ledger** (append-only, double-entry): the financial truth; balances are
  projections.

Plus: an idempotency store (Redis + durable), settlements, webhook deliveries,
and card tokens (never PANs).

---

## Step 5 — High-level architecture

```
Merchant ─REST─> API Gateway (auth, rate limit, idempotency)
                    │
                    ▼
              Payment Service (state machine)
                 │        │          │
             Risk/Rules  Router    Ledger Service
                          │            │
              ┌───────────┼────────────┴──────────┐
              ▼           ▼                        ▼
          Card Adapter  UPI/Netbank/Wallet     (append-only
              │         Adapters                double-entry)
              ▼              ▼
          Acquirer        Bank/NPCI
                    │
                    ▼
              Kafka (events) ──> Webhook Service ──> Merchant endpoint
                    │
                    ├──> Settlement Service ──> Bank (settlement file)
                    └──> Reconciliation Service <── Bank (settlement file)
```

---

## Step 6 — The critical path: one card payment, end to end

Trace a ₹500 card payment:

1. **Merchant** calls `POST /v1/payments` with amount `50000`, method `card`,
   and `Idempotency-Key: K`.
2. **API Gateway** authenticates, rate-limits, and does a conditional insert on
   K. It wins (K unseen) → proceeds. *(A retry with K would short-circuit here
   and return the stored result — no second charge.)*
3. **Payment Service** creates the payment (`status = initiated`), runs a
   **risk check** (velocity/rules), and asks the **Router** which acquirer to
   use for this method.
4. **API returns 201** to the merchant in < 300ms, with `status = initiated` and
   a `next_action` redirect for 3DS. *The merchant's latency is now decoupled
   from the rail's.*
5. **Asynchronously**, the **Card Adapter** calls the acquirer to authorize
   (carrying its own idempotency key). The customer completes 3DS.
6. On success, the payment transitions to **`authorized`**. An event is emitted
   to **Kafka**; the **Webhook Service** notifies the merchant
   (`payment.authorized`).
7. **Capture** (immediate or delayed) transitions to **`captured`**. The
   **Ledger Service** posts balanced double-entry rows: debit
   merchant-receivable ₹490, debit fee-income ₹10, credit customer-clearing
   ₹500. *(Money and ledger move together; the payment isn't `captured` until
   the ledger posts.)*
8. A `payment.captured` webhook goes to the merchant (retried until acked).
9. At settlement time, the **Settlement Service** aggregates captured payments
   minus fees into a settlement, transfers to the merchant's bank, and records
   the bank reference.
10. **Reconciliation** compares our ledger against the bank's settlement file;
    discrepancies open cases.

**If the rail times out at step 5:** the adapter retries with the same rail-side
idempotency key. If it still can't determine the outcome, the payment stays in a
non-terminal state and reconciliation (or a status-poll to the acquirer)
resolves it. **It never double-charges and never silently fails.**

---

## Step 7 — Where the hard problems live

### Idempotency under concurrency
The conditional insert makes exactly one request win; concurrent duplicates get
`409` or the stored result. This is the core guarantee and it's a single
primitive.

### The ambiguous rail outcome
A timeout doesn't mean failure. The design (a) uses rail-side idempotency keys
so a retry can't double-authorize, (b) polls the acquirer for the true status,
and (c) falls back to reconciliation. Three layers, because one isn't enough.

### Money conservation
Double-entry ledger with an enforced debit=credit invariant, plus a nightly
integrity job. If the books don't balance, a bug is caught the same night, not
discovered by an auditor.

### Exactly-once over at-least-once rails
You can't have exactly-once transport, so: at-least-once everywhere + idempotent
processing + an explicit state machine so re-processing is a no-op. The state
machine is what makes "process the same event twice" safe.

---

## Step 8 — Scaling (the follow-up)

Full detail in `scaling-strategy.md`; the summary:

1. **Concurrency correctness** — idempotency (conditional insert), optimistic
   versioning, and an append-only ledger (no shared mutable balance to race on).
2. **Ledger volume** — append-only, time-partitioned, cached balance
   projections.
3. **Rail flakiness** — async authorization, circuit breakers, bulkheads,
   multi-acquirer failover.
4. **Reconciliation** — sharded, idempotent, re-runnable batch; break
   classification.
5. **Peaks** — autoscaling, queue smoothing, pre-provisioning, load shedding
   that never drops payments.

---

## Step 9 — Failure analysis

| Failure | Behavior | Why it's safe |
|---|---|---|
| API node dies | LB retries another node | Stateless + idempotency makes retry safe |
| Redis (idempotency) down | Dedupe degrades | Durable backing for keys |
| Payment DB primary fails | Failover to replica | Sync replication, RPO = 0 |
| Acquirer down | That method fails | Router fails over to another acquirer |
| Ledger write fails | Payment stays non-terminal | Money and ledger move together |
| Webhook endpoint down | Merchant misses events | Retries + dead-letter + replay API |

**Invariant: a payment acknowledged to the merchant is durable and reaches a
terminal state; no failure double-charges or loses a payment.**

---

## Step 10 — What I'd say if asked to go deeper

Pick one and go deep:

- **Idempotency end-to-end:** keys on our API *and* the rail; conditional-insert
  dedupe; the concurrent-duplicate and crash-mid-process cases.
- **The ledger:** why double-entry, the debit=credit invariant, projections vs.
  mutable balances, and how it makes reconciliation possible.
- **The ambiguous timeout:** the three-layer resolution (rail idempotency, status
  poll, reconciliation).
- **Reconciliation:** why it's non-negotiable when the bank is the source of
  truth, and how to run it at billions of rows.
- **The state machine:** legal transitions and why flags are a trap.

---

## The 60-second summary

A payment gateway is an **idempotency and reconciliation problem**. Every
mutating request carries an idempotency key, deduped by a conditional insert so
a retry never double-charges. A payment is an explicit state machine
(initiated → authorized → captured → settled, with failure/refund branches),
persisted strongly and sharded by merchant. Authorization is asynchronous —
the merchant API is fast and the slow, flaky rail is isolated behind a worker
and a webhook. Every money movement posts to an append-only double-entry ledger
(the financial truth; balances are projections), which makes the books provably
conserved and auditable. Settlement batches to the merchant's bank, and daily
reconciliation against the bank's file is the final backstop that makes eventual
consistency with the outside world acceptable. Card data is tokenized, never
stored. The scaling story is mostly about *removing shared mutable state* so
concurrency stops being dangerous — the append-only ledger is the biggest such
move.
