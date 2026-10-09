# Payment Gateway — System Design

## Overview

Design a payment gateway like Razorpay or Stripe: merchants integrate an API,
their customers pay via cards/UPI/netbanking/wallets, and the gateway
orchestrates authorization, capture, refunds, settlement, and webhooks with
**exactly-once semantics over at-least-once rails**. At ~100M transactions/day,
the interesting problem is not throughput — it is correctness when the
downstream (banks, card networks) is slow, unreliable, and only eventually
consistent.

## The One Idea That Matters

**A payment system is an idempotency and reconciliation problem wearing an API
costume.** The network will duplicate and drop messages; the rail will time out
without telling you whether it succeeded. The design must guarantee that a
retried request never double-charges, and that every initiated payment reaches a
terminal state that is reconciled against the bank's ledger daily.

## Key Features

- **Payment lifecycle** — initiate → authorize → capture → settle, as an
  explicit state machine
- **Multiple methods** — cards, UPI, netbanking, wallets, EMI, routed to the
  right processor
- **Idempotency** — client-supplied idempotency keys on every mutating call
- **Immutable double-entry ledger** — the source of financial truth
- **Webhooks** — reliable, signed, at-least-once notifications to merchants
- **Refunds** — full/partial, idempotent
- **Settlement** — batch settlement to merchant accounts
- **Reconciliation** — daily comparison against the acquirer/bank ledger
- **Routing** — choose the acquirer by method, cost, and success rate

## System Requirements

### Functional Requirements
1. Initiate a payment; track it through a state machine
2. Route to the right processor per method
3. Authorize and capture; support delayed capture
4. Refunds (full and partial), idempotent
5. Reliable signed webhooks
6. Batch settlement + reports
7. Daily reconciliation
8. Chargeback handling
9. Merchant onboarding and routing config

### Non-Functional Requirements
1. **Scale**: ~100M txn/day (~1,200 TPS avg, ~5,000 peak)
2. **Latency**: < 300ms initiation; authorization async on the rail's clock
3. **Availability**: 99.99% uptime
4. **Correctness**: no double charges, ever; no lost payments
5. **Consistency**: strong for payment state and ledger; eventual for settlement
6. **Security**: PCI-DSS (tokenization), encryption, HMAC, fraud checks

## Architecture Components

This design includes:

- **API Gateway** — merchant-facing REST; auth, rate limiting, idempotency
- **Payment Service** — owns the payment state machine; orchestrates the flow
- **Idempotency Store** — dedupes mutating requests by key
- **Method Router / Orchestrator** — picks the processor per method
- **Processor Adapters** — per-rail integrations (card, UPI, netbanking,
  wallet) behind a uniform interface
- **Ledger Service** — append-only double-entry ledger (the source of truth)
- **Webhook Service** — reliable, signed, retrying delivery
- **Settlement Service** — batch settlement and reports
- **Reconciliation Service** — daily bank/network reconciliation
- **Risk/Fraud Service** — velocity and rule checks

## The Core Flow (one card payment)

```
Merchant → API Gateway → Payment Service (create payment, state=INITIATED)
                            │ 1. risk check
                            │ 2. route to acquirer
                            │ 3. call processor (async) → authorize
                            ▼
                        State = AUTHORIZED (or FAILED)
                            │ 4. capture (immediate or delayed)
                            │ 5. post to ledger (double-entry)
                            ▼
                        State = CAPTURED → webhook to merchant
                            │ 6. settlement batch → merchant bank
```

## Files in this Design

- `requirements.md` — Detailed functional and non-functional requirements with scale estimates
- `architecture.puml` — System architecture diagram
- `api-design.md` — REST API + webhook + idempotency specification
- `database-schema.md` — Data model: payments, ledger, settlements, idempotency
- `scaling-strategy.md` — Horizontal scaling, sharding, reconciliation at scale
- `tradeoffs.md` — Key design decisions and alternatives
- `solution.md` — Complete end-to-end walkthrough

## Key Design Decisions (preview)

1. **Idempotency keys on every mutating request** — the single most important
   requirement. A retried `POST /payments` with the same key returns the
   original result; it never creates a second charge.
2. **Immutable double-entry ledger** — money is never "updated"; it is appended.
   Balance is a projection of the ledger. This is what makes reconciliation and
   audit possible.
3. **State machine, not flags** — a payment's status is an explicit state with
   legal transitions; illegal transitions are rejected. Prevents "half-paid"
   corruption.
4. **Async authorization** — the client-facing path returns fast; the slow rail
   call runs asynchronously and updates state via events.
5. **Webhooks are at-least-once and must be idempotent on the merchant side** —
   we retry until acked; the merchant dedupes by event id.
6. **Daily reconciliation is non-negotiable** — the bank is the ultimate source
   of truth; our ledger must be proven against it.

See `solution.md` for the full walkthrough and `tradeoffs.md` for alternatives.
