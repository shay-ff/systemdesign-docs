# Chat System — Design Decisions & Trade-offs

Each section states a decision, the alternatives, and why this design picks what
it picks. In an interview, the *alternatives* are what demonstrate seniority —
anyone can describe one design; the signal is knowing what you gave up.

---

## 1. Transport: WebSocket vs. long-polling vs. SSE

**Decision:** WebSocket for real-time, REST for request/response.

| Option | Pros | Cons |
|---|---|---|
| **WebSocket** (chosen) | Bidirectional, low overhead, server-push, < 500ms | Stateful connections; connection-management complexity |
| Long-polling | Works everywhere, stateless servers | High latency, wasted requests, poor battery |
| Server-Sent Events | Simple server-push, works over HTTP | Unidirectional (client must POST separately); still one connection per client |

WebSocket is the only option that meets the latency budget without wasteful
polling. The cost — stateful connections — is managed by making gateways
stateless-except-sockets and putting shared state in Redis.

---

## 2. Ordering: per-conversation sequence vs. global timestamp

**Decision:** per-conversation monotonic sequence number, assigned by the
conversation's shard.

**Alternatives:**
- *Global timestamp (wall clock):* breaks — clocks skew across servers, and two
  messages can share a millisecond. Ordering would be unreliable.
- *Global sequence (single counter):* correct but a scaling bottleneck (every
  message hits one counter) and unnecessary — nobody needs cross-conversation
  ordering.
- *Vector clocks:* rigorous causal ordering, but heavy; overkill when a single
  per-conversation counter gives total order within the conversation.

Per-conversation sequence is the sweet spot: total order where it matters,
zero cross-conversation coordination.

---

## 3. Delivery: at-least-once vs. exactly-once

**Decision:** at-least-once delivery + client-side dedupe by message ID.

**Why not exactly-once?** Exactly-once across an unreliable network is not
achievable in general — the classic two-generals problem. Any "exactly-once"
system is really at-least-once plus idempotent dedupe. Doing the dedupe on the
client (which knows which message IDs it has already rendered) is simpler and
more reliable than trying to make the server track per-recipient delivery
state perfectly.

**Cost:** the client must store recently-seen message IDs (bounded, e.g. last
1000 per conversation) to dedupe. Cheap and local.

---

## 4. Offline delivery: durable inbox vs. rely on reconnect-sync

**Decision:** durable per-recipient inbox.

**Alternative:** store nothing for offline users; on reconnect, the client
fetches history from its last-known sequence.

**Why the inbox?** History-fetch works but is expensive at scale (every
reconnect triggers a history scan) and racy (the client must know how far back
to fetch). The inbox is a targeted, ordered "what this user hasn't received"
queue — O(pending) instead of O(history). The cost is an extra store and the
bookkeeping of clearing entries after ack.

A hybrid is common: inbox for recent offline messages, history-fetch for the
long tail. Name both.

---

## 5. Group fan-out: push (write) vs. pull (read)

**Decision:** hybrid — push for normal groups, pull for very large groups.

| Approach | Pros | Cons |
|---|---|---|
| **Push (fan-out on write)** | O(1) read; message pre-placed for each member | Expensive for large groups (1024 writes per message) |
| **Pull (fan-out on read)** | Cheap write (store once) | Expensive read; every member scans the group log |

Average group is ~10 members, where push is clearly right. A 1024-member group
is where push's cost explodes, so large groups flip to pull. The threshold is a
tuning knob; the principle (push small, pull large) is the answer interviewers
want.

---

## 6. Message storage: Cassandra vs. relational vs. document

**Decision:** Cassandra (wide-column), partitioned by conversation.

- *Relational:* can't absorb 1.2M writes/sec on one primary; messages aren't
  relational (no joins needed).
- *Document (MongoDB):* workable, but ordering and huge-volume append patterns
  fit wide-column better.
- *Cassandra:* append-optimised, partition-local ordering, linear horizontal
  scale — exactly the message workload.

**Cost:** no transactions across conversations (don't need them), and eventual
consistency at the cluster level (mitigated by quorum reads on the sender's own
writes).

---

## 7. Media: inline vs. reference

**Decision:** upload media to a blob store; the message carries a reference.

**Alternative:** embed media bytes in the message.

**Why reference?** Embedding would (a) bloat every message and every fan-out
copy, (b) make the message store enormous, (c) re-transfer bytes to every
recipient. A reference means media is uploaded once and downloaded once per
recipient, via CDN. This is the single biggest storage/bandwidth win in the
design.

---

## 8. Multi-device: one connection vs. one per device

**Decision:** one session per device; fan out to all active sessions.

**Why not "one active device at a time"?** Users expect their phone and laptop
to both receive messages. Each device is an independent session with its own
delivery state; the message fans out to all. The cost is more sessions (more
fan-out), which is acceptable — multi-device is a core expectation, not a
nice-to-have.

---

## 9. Presence: push every change vs. pull on demand

**Decision:** subscribe-on-open, push changes only; coarse TTL for liveness.

**Why not broadcast all presence?** At 500M users, broadcasting every status
change is a firehose. Users only care about the presence of people in their
open chats — subscribe to those, push transitions only. Steady-state heartbeats
refresh a TTL silently.

---

## 10. Consistency: uniform vs. mixed

**Decision:** mixed — strong where the user notices, eventual everywhere else.

| Data | Consistency | Why |
|---|---|---|
| Message ordering | Strong | Visible reordering is unacceptable |
| Message durability | Strong (write-before-ack) | "Sent" must mean "safe" |
| Group membership | Strong | Permission correctness |
| Receipts | Eventual | A late ✓✓ is invisible |
| Presence | Eventual | 2s staleness is fine |

Paying for strong consistency everywhere would be expensive and pointless;
paying for it nowhere would be visibly broken. Matching the level to the user's
perception is the mature answer.

---

## Summary table

| # | Decision | Chosen | Chief alternative | Why chosen |
|---|---|---|---|---|
| 1 | Transport | WebSocket + REST | Long-poll | Latency + efficiency |
| 2 | Ordering | Per-conversation seq | Global timestamp | Correctness without bottleneck |
| 3 | Delivery | At-least-once + dedupe | Exactly-once | Exactly-once is impossible; dedupe is cheap |
| 4 | Offline | Durable inbox | History resync | O(pending) not O(history) |
| 5 | Group fan-out | Hybrid push/pull | Pure push | Push explodes at 1024 members |
| 6 | Message store | Cassandra | Relational | Write volume + ordering |
| 7 | Media | Reference | Inline | Storage/bandwidth |
| 8 | Multi-device | One session/device | Single device | User expectation |
| 9 | Presence | Subscribe-on-open | Broadcast all | Firehose avoidance |
| 10 | Consistency | Mixed | Uniform strong | Match cost to perception |
