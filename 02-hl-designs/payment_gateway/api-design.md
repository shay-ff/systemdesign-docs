# Payment Gateway — API & Webhook Design

The API is where the idempotency contract lives. Every mutating endpoint takes
an idempotency key, and the design of the request/response is shaped by "a
retry must be safe".

## Conventions

- **Auth**: `Authorization: Bearer <api_key>` plus an HMAC signature for
  sensitive ops. Merchant keys are per-merchant, rotatable.
- **Idempotency**: every `POST` (and any mutating `PATCH`) **requires**
  `Idempotency-Key: <uuid>`. The server stores `(merchant, key) → result` and
  replays the original result on repeat.
- **Amounts**: always in the **smallest currency unit as an integer** (paise,
  cents). Never floats — floating-point money is a bug. Currency is explicit
  (`"INR"`, `"USD"`).
- **Errors**: a stable error object with `code`, `description`, and a `reason`
  for declines.

---

## Core endpoints

### Create a payment

```
POST /v1/payments
Headers:
  Authorization: Bearer sk_live_...
  Idempotency-Key: 8f3a-...-uuid
Body:
{
  "amount": 50000,                 // ₹500.00 in paise
  "currency": "INR",
  "method": "card",                // card | upi | netbanking | wallet
  "description": "Order #1042",
  "customer": { "id": "cust_123" },
  "callback_url": "https://merchant.example/callback",
  "notes": { "order_id": "1042" }
}
→ 201 Created
{
  "id": "pay_9f2b...",
  "status": "initiated",           // initiated | authorized | captured | failed | refunded
  "amount": 50000,
  "currency": "INR",
  "created_at": "2026-10-03T10:00:00Z",
  "next_action": {                 // present when the customer must do something
    "type": "redirect",
    "url": "https://gateway.example/3ds/pay_9f2b..."
  }
}
```

**Repeat with the same `Idempotency-Key`** → `200` with the *original* payment
object (not a new one). This is the whole point: a client that times out and
retries gets the same payment back.

### Fetch a payment

```
GET /v1/payments/{payment_id}
→ 200 { "id": "pay_9f2b...", "status": "captured", "amount": 50000, ... }
```

Reads are naturally idempotent — no key needed.

### Capture a payment (delayed capture)

```
POST /v1/payments/{payment_id}/capture
Headers: Idempotency-Key: <uuid>
Body: { "amount": 50000 }          // may capture ≤ authorized amount
→ 200 { "id": "pay_9f2b...", "status": "captured", "captured_at": "..." }
```

### Refund

```
POST /v1/payments/{payment_id}/refunds
Headers: Idempotency-Key: <uuid>
Body: { "amount": 20000, "reason": "customer_request" }
→ 201 {
  "id": "rfnd_77a...",
  "payment_id": "pay_9f2b...",
  "amount": 20000,
  "status": "processed",          // pending | processed | failed
  "created_at": "..."
}
```

Refunds are idempotent on the key; a partial refund for less than the full
amount is allowed, and multiple partial refunds are allowed up to the captured
total.

---

## The idempotency contract (detail)

```
Client                          Gateway
  |  POST /payments                |
  |  Idempotency-Key: K           |
  |------------------------------>|
  |                    (K unseen) | store K -> IN_PROGRESS
  |                               | process...
  |                               | store K -> RESULT
  |<------------------------------| 201 {payment}
  |
  |  ...network timeout, client unsure...
  |
  |  POST /payments  (retry)      |
  |  Idempotency-Key: K           |
  |------------------------------>|
  |                    (K seen)   | return stored RESULT
  |<------------------------------| 200 {same payment}
```

**Edge cases the design must handle:**
- **Concurrent duplicate:** two requests with key K arrive simultaneously. The
  first sets `IN_PROGRESS` atomically (a conditional insert); the second sees
  `IN_PROGRESS` and returns `409 Conflict` (or waits). Only one proceeds.
- **Crash mid-process:** K is `IN_PROGRESS` but no result. The retry must
  *resume or safely fail*, not create a new charge. The processing itself must
  be idempotent against the rail (the rail also gets an idempotency key).
- **Key reuse with a different body:** return `422` — the same key with a
  different payload is a client bug.

---

## Webhooks

Webhooks are how the gateway tells the merchant about asynchronous changes
(authorization completed, payment captured, refund processed, dispute opened).

### Event payload

```
POST https://merchant.example/webhooks/razorpay   (merchant's endpoint)
Headers:
  X-Gateway-Signature: sha256=<hmac of body with merchant webhook secret>
  X-Gateway-Event-Id: evt_55c...
  X-Gateway-Event-Type: payment.captured
Body:
{
  "id": "evt_55c...",
  "type": "payment.captured",
  "created_at": "2026-10-03T10:00:05Z",
  "data": {
    "payment": { "id": "pay_9f2b...", "status": "captured", "amount": 50000, ... }
  }
}
```

### Delivery semantics

- **At-least-once.** The gateway retries until the merchant returns `2xx`.
- **Exponential backoff** with jitter; retries over hours (e.g. 5s, 30s, 5m,
  30m, 2h, ...), then a **dead-letter** after N attempts and a merchant-facing
  log of failed deliveries.
- **Idempotent on the merchant side.** Because delivery is at-least-once, the
  merchant must dedupe by `X-Gateway-Event-Id`. The gateway documents this
  loudly — it is the single most common integration bug.
- **Ordering:** not guaranteed across events; use `created_at` + the payment
  state machine to ignore out-of-order/stale events (e.g. a `captured` event
  arriving after a `refunded` event is stale and must be ignored).

### Signature verification

The merchant recomputes `HMAC-SHA256(raw_body, webhook_secret)` and compares to
`X-Gateway-Signature`. This proves the webhook came from the gateway and was not
tampered with. The raw body must be used (not a re-serialized JSON) — a classic
footgun.

---

## Error model

| HTTP | Code | Meaning |
|---|---|---|
| 400 | `bad_request` | Malformed body/amount |
| 401 | `unauthorized` | Bad API key |
| 402 | `payment_failed` | Rail declined (see `reason`) |
| 409 | `idempotency_in_progress` | Duplicate key still processing |
| 422 | `idempotency_key_reuse` | Same key, different body |
| 429 | `rate_limited` | Merchant quota exceeded |
| 500 | `server_error` | Retry with the **same** idempotency key |

The `500` row is the important one: **retry with the same key**. A new key on a
retry of a request that may have succeeded is how double charges happen.

---

## Rate limiting

Per-merchant token bucket (see `../../01-ll-designs/rate_limiter/`), with
separate buckets for reads and writes, and a burst allowance for legitimate
spikes (checkout bursts). Limits are per API key, configurable per merchant
tier.

---

## Versioning

URL-versioned (`/v1/...`). Breaking changes ship a new version; the old version
is supported for a documented deprecation window. Webhook payloads carry their
own version field so merchants can migrate independently.
