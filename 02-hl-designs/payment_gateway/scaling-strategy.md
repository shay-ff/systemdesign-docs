# Payment Gateway — Scaling Strategy

Payment scale is unusual: ~1,200 TPS average is *low* (chat does 1.2M/sec), so
the challenge is not raw throughput — it is **maintaining correctness,
idempotency, and reconciliation as the system grows**, while surviving festival
spikes of ~5,000 TPS and never losing or duplicating a rupee.

## The bottleneck hierarchy

1. **Correctness under concurrency** — the first wall (double charges, lost
   updates).
2. **Ledger write volume** — ~600M rows/day.
3. **External rail latency and flakiness** — you don't control it.
4. **Reconciliation at scale** — billions of rows to compare daily.
5. **Peak spikes** — 4–5× normal load during sales.

---

## 1. Correctness under concurrency — the real scaling problem

At higher TPS, more requests race on the same resources (a payment, an
idempotency key, an account balance). The design defends each race explicitly:

**Idempotency races.** Two requests with the same key: a conditional insert
(`SET NX` / `INSERT ... ON CONFLICT DO NOTHING`) lets exactly one win. The
loser returns the winner's result or `409`. This is linearizable and does not
degrade with load — it is the same primitive at 10 TPS and 10,000 TPS.

**Payment state races.** Two captures on one payment: optimistic concurrency
(`UPDATE ... WHERE version = ?`) means one succeeds, the other retries against
the new version and sees it's already captured. No lost updates, no locks on the
hot path.

**Ledger balance races.** The ledger is append-only, so there is *no balance to
race on*. Balance is computed as a projection. Appends are sequential writes to
time-partitioned tables — no contention. This is a deliberate design choice:
**immutability removes the need for locking.**

**The lesson:** scaling a payment system is not "add more servers"; it is
"remove shared mutable state so concurrency stops being dangerous". The
append-only ledger is the biggest such move.

---

## 2. Ledger write volume

600M rows/day, growing. The ledger is:

- **Append-only** → sequential writes, no updates, no locking.
- **Time-partitioned** → each day/month is a partition; old partitions are
  compressed and archived.
- **Write-optimised store** → Postgres with partitioning handles millions of
  inserts/day; beyond that, a wide-column store (Cassandra) or a purpose-built
  ledger (or Kafka + a columnar sink) takes over.
- **Balance projections cached** → computing a balance by scanning the ledger is
  fine per account, but hot merchant balances are materialised (a running
  balance table updated transactionally with each posting, or a periodically
  refreshed snapshot + tail). The snapshot is a *cache* of the truth, never the
  truth itself.

---

## 3. External rail latency and flakiness

You do not control the acquirer/bank. They time out, return errors, and are
sometimes slow for seconds. The design isolates this:

- **Async authorization** — the merchant-facing API returns in < 300ms (payment
  created, state = `initiated`); the rail call runs on a worker. The merchant
  learns the outcome via webhook. This decouples your latency from the rail's.
- **Timeouts + retries with idempotency** — a rail timeout is ambiguous ("did it
  succeed?"). Retries to the rail carry *their* idempotency key so a retry
  cannot double-authorize.
- **Circuit breakers** — if an acquirer is failing, the router fails over to
  another acquirer (multi-acquirer routing is both a resilience and a cost
  lever).
- **Bulkheads** — one slow rail must not exhaust threads for other rails;
  separate thread pools / queues per processor.

---

## 4. Reconciliation at scale

Daily reconciliation compares our ledger against the acquirer/bank settlement
files — billions of rows.

- **Shardable batch job** — partition by merchant or by day; each shard
  reconciles independently and in parallel.
- **Idempotent and re-runnable** — reconciliation can be re-run for any day
  without side effects; it only *reports* discrepancies and opens cases.
- **Break classification** — discrepancies are categorised (timing difference,
  missing in bank, missing in ledger, amount mismatch) so operations can triage
  rather than drown.
- **Streaming reconciliation** — for near-real-time detection, a streaming job
  matches events as they arrive, with the batch job as the daily backstop.

Reconciliation is the safety net that makes eventual consistency with the bank
acceptable: we *will* converge, and we detect when we haven't.

---

## 5. Peak spikes

Festival sales spike to ~5,000 TPS (4–5× normal) in seconds.

- **Horizontal autoscaling** of stateless services (API gateway, payment
  service, webhook workers) on queue depth and CPU.
- **Queue-based smoothing** — asynchronous work (authorization, webhooks,
  settlement) absorbs bursts in queues; the synchronous path stays short.
- **Pre-provisioning** — known sale events are pre-scaled (autoscaling lags a
  sudden spike).
- **Backpressure and load shedding** — under extreme load, shed non-critical
  work (e.g. analytics events) before shedding payments. Priority: never drop a
  payment; it's fine to delay a metrics event.

---

## Geographic and availability design

- **Multi-AZ** within a region — every store replicated across availability
  zones; a zone failure is transparent.
- **Multi-region** — active-passive or active-active for the API; the ledger
  has a home region per merchant to keep writes strongly consistent. Cross-
  region failover is tested, not assumed.
- **RPO = 0 for committed payments** — a payment acknowledged to the merchant
  must survive any single failure. Synchronous replication to at least one other
  node before ack.

---

## Failure modes and mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| API node dies | Requests fail on that node | Stateless; LB retries to another node; idempotency makes retry safe |
| Redis (idempotency) down | Can't dedupe fast | Durable backing (DB) for keys; degrade to DB-based dedupe |
| Payment DB primary fails | Writes fail | Failover to replica; synchronous replication for RPO=0 |
| Acquirer down | That method fails | Router fails over to another acquirer; circuit breaker |
| Ledger write fails | Payment can't complete | Retry; the payment stays in a non-terminal state until the ledger posts (money and ledger must move together) |
| Webhook endpoint down | Merchant misses events | Retry with backoff; dead-letter; merchant can replay from the API |
| Reconciliation job fails | Delayed detection | Re-runnable; alert on missing run |

**The invariant:** a payment acknowledged to the merchant is durable and will
reach a terminal state; no failure double-charges or loses a payment.

---

## Capacity summary

| Dimension | Estimate | Provisioning |
|---|---|---|
| Transactions/day | 100M | ~1,200 TPS avg, ~5,000 peak |
| Ledger rows/day | ~600M | Time-partitioned append-only store |
| Payment store | sharded by merchant | Postgres + read replicas |
| Idempotency | Redis cluster | Durable backing for keys |
| Webhook throughput | ~1 event/payment | Queue + autoscaled workers |
| Reconciliation | daily, billions of rows | Sharded idempotent batch |

## What to say if asked "what's the hardest part?"

Keeping **idempotency correct under concurrency and failure** — specifically the
window where a request may have reached the rail but we don't know the outcome.
The answer is layered: idempotency keys end-to-end (our API *and* the rail's),
a conditional-insert dedupe that lets exactly one request win, an explicit
payment state machine so a payment has one authoritative state, and daily
reconciliation against the bank as the final backstop. Every other scaling
concern is secondary to never charging twice and never losing a payment.
