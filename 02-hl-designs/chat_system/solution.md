# Chat System — Complete Solution Walkthrough

This is the end-to-end narrative: how a single message travels through the
system, then how each component scales. Read it as the answer you would give in
the interview, from requirements to a scaled architecture.

---

## Step 1 — Clarify and scope

Before drawing anything, pin down the problem (see `requirements.md` for the
full list). The questions that actually change the design:

1. **One-to-one only, or groups?** Groups introduce fan-out, the hardest part.
   *Assume both, groups up to 1024.*
2. **Ordering guarantees?** Per-conversation ordering is enough; no global
   order. *This decision removes a whole class of complexity.*
3. **Offline delivery?** Messages must survive the recipient being offline.
   *Yes — this forces a durable inbox.*
4. **Multi-device?** *Yes — forces per-device sessions.*
5. **Media?** *Yes — but by reference, not inline.*
6. **Receipts?** *Yes — eventual consistency acceptable.*

Scope fixed: 1:1 + group messaging, per-conversation ordering, at-least-once
delivery with offline support, multi-device, media by reference, receipts.

---

## Step 2 — Back-of-the-envelope scale

| Metric | Estimate | Derivation |
|---|---|---|
| Messages/day | 100B | Given |
| Messages/sec | ~1.2M avg, ~3M peak | 100B / 86400, ×2.5 peak |
| Concurrent connections | 100M | Given |
| Gateways needed | ~150–200 | 1M conns/node, +headroom |
| Message storage | ~20 TB/day | 100B × ~200 bytes |
| Media storage | tens of PB/year | Separated into object store |

These numbers drive: WebSocket (not polling), a sharded wide-column store (not
one DB), and a connection-gateway fleet sized in the hundreds.

---

## Step 3 — API design

Two transports (full detail in `api-design.md`):

- **WebSocket** for send/receive, presence, typing, receipts.
- **REST** for auth, history, media, group management.

The single most important API decision: **the send acknowledgement returns a
per-conversation `sequence` number.** That number is the client's ordering key
and its gap detector.

---

## Step 4 — Data model

Message storage keyed by `conversation_id` with `sequence` clustering
(full detail in `database-schema.md`):

```sql
CREATE TABLE messages (
    conversation_id  uuid,
    sequence         bigint,
    message_id       uuid,
    sender_id        uuid,
    content_type     text,
    content          text,
    server_ts        timestamp,
    PRIMARY KEY ((conversation_id), sequence)
) WITH CLUSTERING ORDER BY (sequence DESC);
```

Plus: `user_conversations` (chat list), Postgres for users/groups/membership,
Redis for sessions/presence, an `inbox` table for offline delivery, and object
storage for media bytes.

---

## Step 5 — High-level architecture

```
Clients ──WebSocket──> L4 LB ──> Connection Gateways (x~150)
   │                                   │
   └──REST──> API Gateway ─────────────┤
                                       ▼
                         ┌──────────────────────────┐
                         │   Messaging Core         │
                         │  Message Service         │
                         │  Group Service           │
                         │  Delivery Service        │
                         │  Receipt Service         │
                         └──────────────────────────┘
                            │        │         │
                     Session Registry │   Offline Inbox
                        (Redis)       │    (Cassandra)
                                      ▼
                            Message Store (Cassandra,
                            partitioned by conversation)
```

---

## Step 6 — The critical path: one message, end to end

Trace "A sends 'hi' to B", where B is online on two devices:

1. **A's client** sends a `message.send` frame over its WebSocket, with a
   client-generated id `c-8f3a`.
2. **A's gateway** receives the frame, does a cheap rate-limit check, and
   forwards to the **Message Service**.
3. **Message Service** validates, then **persists** the message to the
   conversation's partition in Cassandra, which assigns the next `sequence`
   (say 40217). *The message is now durable.*
4. **Message Service acks A** with `message.ack {clientId: c-8f3a, messageId:
   srv-991, sequence: 40217}`. A's client shows a single ✓ ("sent"). **The ack
   happens after step 3, not after delivery** — "sent" means "durable", not
   "delivered".
5. **Delivery Service** looks up B in the **Session Registry** and finds two
   sessions (phone, laptop), on possibly different gateways.
6. It **pushes `message.new`** to both of B's gateways, which forward to B's
   devices.
7. Each of B's devices acks; the gateway emits a receipt; the **Receipt
   Service** writes `delivered` and propagates `receipt.update` to A. A's
   client shows ✓✓.
8. When B opens the chat, B's devices send `read` receipts; A sees blue ✓✓.

**If B were offline:** step 5 finds no session → the message goes to B's
**inbox** (durable) and a **push notification** fires via APNs/FCM. When B
reconnects, the gateway drains B's inbox in sequence order, and B acks. Same
end state, delayed.

**If it were a group:** step 5 becomes "resolve the group's members (cached)",
then fan out to each member's session or inbox — asynchronously, so A's ack
isn't held hostage by the slowest member.

---

## Step 7 — Where the hard problems live

### Per-conversation ordering
Solved by the sequence number. Every message to a conversation gets a strictly
increasing sequence from that conversation's partition. Clients order by
sequence. **No global ordering, so no global bottleneck.**

### Gap detection
If B's client receives sequence 40218 but last saw 40217, it's fine. If it
receives 40220, there's a gap → B fetches history from 40217. The sequence
number is both the order key and the integrity check.

### Idempotent sends
If A's client times out and retries, the server sees the same client id
`c-8f3a` and returns the original result instead of creating a duplicate
message. Idempotency is keyed on the client-generated id.

### Multi-device
Each device is an independent session with its own delivery/read state. The
message fans out to all; each device acks independently. A message read on the
phone marks it read on the laptop too (a receipt event the other device
consumes).

### Presence without a firehose
Presence is stored as a coarse TTL in Redis; only *transitions* are pushed, and
only to users who have the chat open (subscribe-on-open). Steady heartbeats are
silent.

---

## Step 8 — Scaling (the follow-up question)

Full detail in `scaling-strategy.md`; the summary:

1. **Connections** — horizontal gateway fleet, stateless-except-sockets; shared
   state in Redis/Cassandra; jittered client reconnect to avoid storms.
2. **Delivery** — Session Registry lookup + cross-gateway push; offline path
   via durable inbox.
3. **Storage** — Cassandra shards writes by conversation; recent data on SSD,
   cold data tiered.
4. **Presence** — aggregate heartbeats, push only transitions.
5. **Hot groups** — async fan-out; read-fan-out for very large groups.

---

## Step 9 — Failure analysis

| Failure | Behavior | Why it's safe |
|---|---|---|
| Gateway dies | Clients reconnect elsewhere | Gateways hold no business state |
| Redis (sessions) down | Can't route to live sessions | Fall back to inbox; messages still persist |
| Cassandra node down | Its partitions' writes fail | Replication factor ≥ 3 |
| Delivery backlog | Higher latency | Messages persist; inbox guarantees eventual delivery |
| APNs down | No push notification | Inbox delivers on next app open |

**The invariant: no failure loses a message.** Messages are durable before ack
and delivered at-least-once. Worst case is delayed delivery.

---

## Step 10 — What I'd say if asked to go deeper

Pick one and go deep — the interviewer is testing depth, not breadth:

- **Ordering + gaps:** the sequence number as order key and integrity check;
  how the client resyncs.
- **Offline delivery:** inbox vs. history-resync; how the inbox is drained and
  cleared.
- **Multi-device:** per-device sessions; how read state propagates across a
  user's devices.
- **Presence at scale:** why broadcasting is a firehose and how
  subscribe-on-open fixes it.
- **Group fan-out:** the push/pull hybrid and where the threshold sits.

---

## The 60-second summary

A chat system is a **store-then-route** pipeline. Persist the message to a
per-conversation ordered log (Cassandra, partitioned by conversation, sequenced
for ordering), ack the sender (durability, not delivery), then route to the
recipient's live sessions via a Redis session registry or, if offline, to a
durable inbox plus a push notification. Ordering is per-conversation; delivery
is at-least-once with client dedupe; media is by reference; presence is
subscribe-on-open. Every component failure delays delivery but never loses a
message. The scaling levers are the connection-gateway fleet (connections), the
sharded message store (writes), and the offline inbox (guaranteed delivery).
