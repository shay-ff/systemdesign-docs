# Payment Gateway — Requirements

## System Overview

Design a payment gateway like Razorpay/Stripe: merchants integrate an API, their
customers pay via multiple methods (cards, UPI, netbanking, wallets), and the
gateway orchestrates authorization, capture, settlement, refunds, and webhooks —
while guaranteeing correctness under failure. **Money is the domain where bugs
are unacceptable**, so the entire design bends toward idempotency, auditability,
and reconciliation.

The hard part is not moving data; it is **exactly-once semantics over
at-least-once rails**, where the downstream (banks, card networks) is slow,
unreliable, and only eventually consistent.

## Functional Requirements

### Payment Lifecycle
1. **Payment initiation** — a merchant creates a payment/order for an amount.
2. **Payment methods** — cards, UPI, netbanking, wallets, EMI; routed to the
   right processor per method.
3. **Authorization** — request authorization from the issuer/acquirer; hold
   funds.
4. **Capture** — capture an authorized payment (immediate or delayed).
5. **Status tracking** — a payment moves through a well-defined state machine.
6. **Webhooks** — notify the merchant of asynchronous status changes
   (success/failure/refund) reliably.

### Money Movement
7. **Refunds** — full and partial refunds, idempotent.
8. **Settlement** — batch-settle captured funds to the merchant's bank account
   (T+1/T+2), with a settlement report.
9. **Payouts** — send money out (to merchants, vendors) — adjacent, noted.
10. **Multi-currency** — handle different currencies and their minor units.

### Correctness & Operations
11. **Idempotency** — a retried request must never double-charge.
12. **Reconciliation** — daily reconciliation against the bank/network ledger.
13. **Disputes/chargebacks** — handle a chargeback lifecycle.
14. **Audit trail** — every state change is recorded immutably.

### Merchant & Platform
15. **Merchant onboarding** — KYC, API keys, configuration.
16. **Routing** — choose the acquirer/processor (cost, success rate, method).
17. **Rate limiting & quotas** — per merchant.

## Non-Functional Requirements

### Scale Requirements
- **Volume**: ~100M transactions/day → ~1,200 TPS average, ~5,000 TPS peak
  (festival sales spike).
- **Merchants**: 1M+; **methods**: card/UPI/netbanking/wallet.
- **Settlements**: daily batches for all merchants.

### Performance Requirements
- **Payment API latency**: < 300ms for initiation (the async authorization
  completes later).
- **Authorization latency**: 2–30s depending on the rail (UPI is fast, cards
  vary, netbanking is slow/redirect-based).
- **Webhook delivery**: < 10s P95 from status change.

### Availability & Reliability
- **Uptime**: 99.99% — a payment outage directly loses revenue.
- **No double charges, ever** — idempotency is a hard requirement.
- **No lost payments** — every initiated payment reaches a terminal state.
- **Durability**: 11 nines; every state change is persisted before ack.
- **DR**: RTO < 15 min, RPO = 0 (no committed payment may be lost).

### Consistency Requirements
- **Payment state**: strong consistency (a payment has one authoritative state).
- **Ledger**: strong consistency + immutability (append-only double-entry).
- **Settlement**: eventual consistency with the bank, reconciled daily.
- **Webhooks**: at-least-once, ordered per payment.

### Security & Compliance
- **PCI-DSS** — never store raw card numbers; tokenize.
- **Encryption** — TLS everywhere; data encrypted at rest.
- **Authentication** — API keys/HMAC for merchants; webhook signature.
- **Fraud** — velocity checks, rules, risk scoring.
- **Compliance** — RBI guidelines, 2FA for cards (3DS), audit logging.

## Scale Estimation (worked)

**TPS.** 100M/day ≈ 1,200 TPS average; festival peaks hit ~5,000 TPS. Payment
TPS is *low* compared to chat — but each transaction is worth money, so
correctness dominates over throughput.

**Latency budget.** The initiation API returns in < 300ms (it just creates the
payment and kicks off async work). Authorization runs on the rail's clock
(seconds), not ours. This asymmetry is central: **the client-facing path is
fast; the money path is slow and async.**

**Ledger writes.** Every payment produces several double-entry rows
(authorization, capture, fee, settlement, refund). 100M payments × ~6 rows ≈
600M ledger rows/day — the highest-volume store, and it must be
append-only/immutable.

**Reconciliation.** Daily: compare our ledger against the acquirer/bank
settlement files. At 100M transactions, this is a batch job over billions of
rows; it must be shardable and idempotent.

## What's Out of Scope

- Card network internals (Visa/Mastercard routing) — modelled as an external
  processor.
- Full fraud-detection ML — modelled as a risk check step.
- Actual PCI-DSS certification — modelled as a requirement (tokenization).
- Payouts in depth — noted as adjacent.

## Success Metrics

- Payment success rate (by method, by acquirer).
- Double-charge incidents (**must be zero**).
- Webhook delivery success rate.
- Reconciliation break count (must trend to zero).
- Settlement timeliness.
