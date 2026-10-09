# Payment Gateway — Data Model & Storage

Two stores dominate: the **payment store** (transactional, strongly consistent,
sharded) and the **ledger** (append-only, immutable, double-entry). Everything
else is supporting. The design principle throughout: **money is never mutated,
only appended; balance is a projection.**

## Storage choices at a glance

| Data | Store | Why |
|---|---|---|
| Payments | Postgres (sharded by merchant) | Transactional state machine, strong consistency |
| Idempotency keys | Redis + durable backing | Fast dedupe on the hot path; durable across restarts |
| Ledger | Append-only Postgres (or Cassandra) | Immutable double-entry; huge volume; audit |
| Settlements | Postgres | Batched, relational, moderate volume |
| Webhook events / delivery | Postgres + queue | Reliable retry with state |
| Events (integration) | Kafka | Event backbone; replay; decoupling |
| Card tokens | Vault / token service | PCI: never store PANs |

---

## Payments table (Postgres, sharded by merchant_id)

```sql
CREATE TABLE payments (
    payment_id       uuid PRIMARY KEY,
    merchant_id      uuid NOT NULL,
    idempotency_key  text NOT NULL,
    amount           bigint NOT NULL,       -- smallest unit (paise)
    currency         char(3) NOT NULL,
    method           text NOT NULL,         -- card | upi | netbanking | wallet
    status           text NOT NULL,         -- initiated | authorized | captured | failed | refunded
    processor_ref    text,                  -- the rail's reference id
    authorized_amount bigint,
    captured_amount  bigint,
    customer_id      uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint NOT NULL DEFAULT 0,   -- optimistic concurrency
    UNIQUE (merchant_id, idempotency_key)
);
```

Key design points:

- **`UNIQUE (merchant_id, idempotency_key)`** — the database itself enforces
  idempotency. A duplicate insert fails; the API layer catches it and returns
  the original. Never rely on application logic alone for this.
- **Amounts as `bigint` in minor units** — no floats, no `decimal` rounding
  surprises. ₹500.00 is `50000`.
- **`version` for optimistic concurrency** — two concurrent captures on the
  same payment: the second's `UPDATE ... WHERE version = ?` fails, and it
  retries/rejects. This prevents lost updates without row locks on the hot path.
- **`status` is a state machine**, not a free field. Legal transitions are
  enforced in the service layer; the DB stores the authoritative value.
- **Sharded by `merchant_id`** — a merchant's payments live together (good for
  merchant-scoped queries), and merchants spread across shards.

**Status transitions (enforced in code):**

```
initiated ──> authorized ──> captured ──> (settled, via settlement)
    │              │              │
    └──> failed    └──> failed    └──> refunded (full or partial)
```

Any transition not on this graph is rejected. A payment is in exactly one
state.

---

## Idempotency store

```
KEY   idem:{merchant_id}:{idempotency_key}  -> {status: IN_PROGRESS|DONE, result_ref, created_at}
TTL   24–48h (long enough to cover any retry window)
```

Backed by Redis for speed, with a durable copy (or a DB row) so a Redis restart
doesn't lose the dedupe. On the hot path, the API gateway does a `SET NX` — if
it wins, proceed; if it loses, the key exists → return the stored result (or
`409` if still in progress).

The **conditional insert** (`SET NX` / `INSERT ... ON CONFLICT DO NOTHING`) is
what makes concurrent duplicate handling correct: exactly one request wins.

---

## Ledger (append-only, double-entry)

The ledger is the **source of financial truth**. It is append-only and
double-entry: every money movement writes balanced debit and credit rows that
sum to zero. Balances are *projections* over the ledger, never stored as
mutable columns.

```sql
CREATE TABLE ledger_entries (
    entry_id      bigserial PRIMARY KEY,
    txn_id        uuid NOT NULL,          -- groups the balanced entries of one movement
    account_id    uuid NOT NULL,          -- merchant, gateway, fees, bank, etc.
    direction     char(1) NOT NULL,       -- 'D' debit | 'C' credit
    amount        bigint NOT NULL,
    currency      char(3) NOT NULL,
    payment_id    uuid,                   -- link back to the payment
    entry_type    text NOT NULL,          -- authorization | capture | fee | settlement | refund
    created_at    timestamptz NOT NULL DEFAULT now()
);
-- Invariant: for each txn_id, SUM(D) = SUM(C). Enforced by the posting service
-- and verified by a nightly integrity job.
```

**Why double-entry?** Because it makes the money provably conserved. A capture
of ₹500 with a ₹10 fee posts: debit merchant-receivable ₹490, debit
gateway-fee-income ₹10, credit customer-clearing ₹500 — sums to zero. If the
books don't balance, there's a bug, and the integrity job catches it
immediately. Single-entry ("merchant balance += 490") loses this invariant and
is how financial systems silently drift.

**Why append-only?** Auditors and regulators require an immutable history.
Corrections are *new* compensating entries, never edits. This also makes the
ledger trivially reconcilable against the bank.

**Volume:** ~600M rows/day. Partition by month; older partitions moved to cold
storage. Never delete; archive.

---

## Settlements (Postgres)

```sql
CREATE TABLE settlements (
    settlement_id  uuid PRIMARY KEY,
    merchant_id    uuid NOT NULL,
    period_start   timestamptz NOT NULL,
    period_end     timestamptz NOT NULL,
    gross_amount   bigint NOT NULL,
    fee_amount     bigint NOT NULL,
    net_amount     bigint NOT NULL,
    currency       char(3) NOT NULL,
    status         text NOT NULL,          -- pending | processing | paid | failed
    bank_ref       text,
    created_at     timestamptz NOT NULL DEFAULT now()
);
```

A settlement aggregates captured payments minus fees for a period. The
settlement service computes it from the ledger (the truth), transfers funds,
and records the bank reference. `net_amount` must equal the ledger projection
for that period — that equality *is* the reconciliation check at the settlement
level.

---

## Webhook delivery (Postgres + queue)

```sql
CREATE TABLE webhook_deliveries (
    delivery_id    uuid PRIMARY KEY,
    event_id       uuid NOT NULL,
    merchant_id    uuid NOT NULL,
    endpoint_url   text NOT NULL,
    payload        jsonb NOT NULL,
    attempt        int NOT NULL DEFAULT 0,
    status         text NOT NULL,          -- pending | delivered | failed | dead_letter
    next_attempt_at timestamptz,
    last_error     text,
    created_at     timestamptz NOT NULL DEFAULT now()
);
```

Each event fans out to delivery rows; a worker retries with backoff until `2xx`
or dead-letters. The table is both the queue and the audit of what the merchant
received.

---

## Card tokens (never PANs)

Raw card numbers (PANs) are **never** stored. The card is tokenized at the
edge (via the processor or a vault); we store only the token, the last 4 digits,
and the expiry for display:

```sql
CREATE TABLE card_tokens (
    token_id     uuid PRIMARY KEY,
    merchant_id  uuid NOT NULL,
    customer_id  uuid,
    token        text NOT NULL,        -- opaque, from the vault
    last4        char(4),
    brand        text,                 -- visa | mastercard | rupay
    exp_month    smallint,
    exp_year     smallint
);
```

This keeps the gateway out of PCI-DSS scope for card data (SAQ-A style) — a
compliance decision with real architectural weight.

---

## Sharding and scaling the stores

- **Payments**: sharded by `merchant_id`. A merchant's data stays together; a
  hot merchant is a hot shard (monitor; a mega-merchant may get a dedicated
  shard).
- **Ledger**: append-only, partitioned by time; writes are sequential
  (high-throughput), reads are range scans per account.
- **Idempotency**: Redis cluster, sharded by key.
- **Webhook**: queue-backed; workers autoscale with backlog.
- **Settlements/reconciliation**: batch jobs, shardable by merchant.

## Consistency summary

| Data | Consistency | Why |
|---|---|---|
| Payment state | Strong | One authoritative state; correctness critical |
| Idempotency | Strong (linearizable dedupe) | The whole no-double-charge guarantee rests here |
| Ledger | Strong + immutable | Financial truth; audit |
| Settlement | Eventual → reconciled daily | Bank is authoritative; we converge |
| Webhooks | At-least-once, per-payment order not guaranteed | Merchant dedupes |

The design pays for strong consistency exactly where money correctness demands
it (state, idempotency, ledger) and uses eventual consistency plus daily
reconciliation for the parts that interact with the outside world (settlement,
bank files) — because you cannot make a bank strongly consistent with you; you
can only reconcile.
