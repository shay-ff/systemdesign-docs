# Chat System — Scaling Strategy

This document answers the question every HLD interview ends with: *"Now scale
it."* It walks the bottlenecks in the order they bite and the fix for each.

## The bottleneck hierarchy

At chat scale, the constraints bite in this order:

1. **Concurrent connections** — 100M live sockets; the first wall.
2. **Message delivery fan-out** — routing each message to the right connection.
3. **Message storage write volume** — 1.2M writes/sec.
4. **Presence/heartbeat traffic** — deceptively large.
5. **Hot partitions** — a single viral group.

Each is addressed below.

---

## 1. Concurrent connections — horizontal connection gateways

A WebSocket connection is stateful: the client holds a socket to a *specific*
server. You cannot put connections behind a plain round-robin load balancer
without losing affinity.

**Design:**
- Each **connection gateway** node holds up to ~1M connections (tuned file
  descriptors, epoll/kqueue, minimal per-connection memory).
- 100M connections → **≥ 100 nodes**, ~150–200 with headroom and rolling
  deploys.
- The **L4 load balancer** distributes *new* connections across gateways (least
  connections). Once established, a connection stays on its gateway.
- Gateways are **stateless except for the connection itself** — all shared
  state (sessions, messages) lives in Redis/Cassandra, so a gateway can die and
  its clients reconnect elsewhere.

**Why not sticky-session a stateful app server?** Because a gateway holds *no
business state* — only sockets. That is what makes the fleet disposable and
horizontally scalable. Statefulness is confined to the connection layer and
reflected in Redis.

**Reconnect storms:** if a gateway dies, 1M clients reconnect at once. Mitigate
with **jittered reconnect backoff** on the client (random 0–30s) so the fleet
isn't DDoSed by its own recovery.

---

## 2. Message delivery — session lookup + push

When a message arrives, the Message Service must find the recipient's live
connection(s):

```
recipientId → Session Registry (Redis) → {gatewayNode, connId} → push
```

The session registry is a Redis lookup — O(1), and it is the crux of delivery.
Two subtleties:

- **Multi-device:** the registry maps a user to a *set* of sessions; fan out to
  all of them.
- **Cross-gateway delivery:** the recipient's gateway is rarely the sender's.
  The Delivery Service routes to the recipient's gateway node (via an internal
  RPC or a message bus keyed by gateway). This is why gateways must be
  addressable — the Session Registry stores which node holds each connection.

**Offline path:** no session found → write to the recipient's inbox → send a
push notification (APNs/FCM). On reconnect, the client drains the inbox. The
offline path is what makes delivery *guaranteed* rather than *best-effort*.

**Fan-out for groups:** a group message resolves members (cached), then fans
out. For a 1024-member group, that's 1024 delivery attempts — done
asynchronously via a queue so the sender's ack isn't blocked by the slowest
member. Small groups (the average of 10) are cheap; large groups are the cost
centre and are special-cased (below).

---

## 3. Message storage — sharded append-only log

Cassandra partitioned by `conversation_id` spreads writes across nodes by
construction. 1.2M writes/sec is absorbed by a multi-node cluster because each
write goes to one partition on one node with no cross-node coordination.

**Tiering:** recent messages (say, 30 days) on fast SSD nodes; older messages
compacted to cheaper storage. The read pattern ("latest N") rarely touches cold
data, so tiering is nearly free.

**Write path optimization:** batch/async replication (Cassandra's quorum write),
and never read-before-write (the sequence counter is partition-local).

---

## 4. Presence — the hidden firehose

500M DAU with a 30s heartbeat = ~16M presence events/sec if every heartbeat is
a write. That would dwarf the message load.

**Mitigations:**
- **Aggregate, don't write per heartbeat.** The gateway tracks liveness in
  memory; Redis presence keys are refreshed on a coarser interval (e.g. 60s
  TTL, refreshed every 45s), not per ping.
- **Only notify on transitions.** Presence changes (online→offline) are
  broadcast; steady-state heartbeats are silent.
- **Fan-out on read for presence.** When you open a chat, you subscribe to the
  participants' presence; the server pushes only changes. Don't broadcast every
  user's presence to everyone.

Presence is the classic "looks cheap, is expensive" component — calling it out
in an interview signals you think about aggregate load, not just per-request
cost.

---

## 5. Hot partitions and the celebrity group

A 1024-member group (or a viral broadcast) makes one conversation partition and
one fan-out hot.

**Mitigations:**
- **Async fan-out via a queue** — the sender's ack doesn't wait for 1024
  deliveries; a worker pool drains the fan-out. Backpressure is explicit.
- **Read fan-out for huge groups** — for very large groups, don't precompute
  per-member delivery; store the message once and let members *pull* it on open
  (the same push-vs-pull trade-off as the news feed). Hybrid: push for small
  groups, pull for large.
- **Time-bucket long-lived conversations** — partition key
  `(conversation_id, month)` bounds partition size.

---

## Geographic distribution

Users are global; the speed of light is a hard constraint.

- **Regional deployments** — gateways and session registries in each region;
  a user connects to the nearest.
- **Message routing across regions** — a sender in India messaging a recipient
  in the US: the message is persisted in the conversation's home region and the
  delivery crosses regions. Keep the conversation's home fixed (for ordering)
  and accept one cross-region hop for delivery.
- **Presence is regional** — a user's presence is known in their region;
  cross-region presence is a replicated, eventually-consistent view.

---

## Failure modes and mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| A connection gateway dies | ~1M clients disconnect | Clients reconnect (jittered) to other gateways; no data lost (state is in Redis/Cassandra) |
| Session registry (Redis) unavailable | Can't route to live connections | Fall back to offline inbox (messages still persist and deliver later); Redis is replicated/clustered |
| Message store node down | Writes to its partitions fail | Replication factor ≥ 3; writes go to a replica |
| Delivery service backlog | Delivery latency rises | Autoscale workers; monitor queue depth; degrade to inbox-only |
| Push provider (APNs) down | Offline users get no notification | Retry with backoff; messages still in inbox, delivered on next app open |
| Hot partition | One conversation slow | Time-bucketing; read fan-out for huge groups |

The recurring theme: **no single component failure loses a message.** Messages
are persisted before ack and delivered at-least-once, so the worst case is
delayed delivery, not loss.

---

## Capacity summary

| Dimension | Estimate | Provisioning |
|---|---|---|
| Concurrent connections | 100M | ~150–200 gateway nodes |
| Messages/sec | 1.2M avg, 3M peak | Cassandra cluster, ~20 TB/day |
| Presence events/sec | ~16M raw → aggregated to ~1M | In-memory + coarse TTL |
| Fan-out/sec | ~12M deliveries/sec | Queue + worker pool, autoscaled |
| Storage/year | tens of PB | Tiered: SSD recent, object store cold |

## What to say if asked "what's the hardest part?"

Delivery to a **mobile user whose connection is flaky and who may be offline for
days**, while guaranteeing ordering and at-least-once semantics, at 100M
connections. The architecture's answer is the combination of (a) store-then-
route with a durable per-conversation log, (b) a session registry for live
delivery, (c) a durable offline inbox for the rest, and (d) client-side
sequencing + dedupe to present exactly-once and in-order to the user. Every
other component exists to make that combination scale.
