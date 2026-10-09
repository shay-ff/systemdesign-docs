# Chat System (WhatsApp-like) — Requirements

## System Overview

Design a real-time messaging system like WhatsApp or Messenger: one-to-one and
group messaging, presence (online/offline/typing), delivery and read receipts,
media sharing, and multi-device sync. Messages must be delivered reliably and
in order, with end-to-end encryption in the real product (modelled here as a
security requirement, not implemented).

The interesting engineering is not "store a message" — it is **real-time
delivery at scale**: maintaining millions of persistent connections, routing a
message to a recipient who may be on any of several devices, and guaranteeing
ordering and at-least-once delivery while the recipient is offline.

## Functional Requirements

### Core Messaging
1. **One-to-one chat** — send/receive text messages between two users.
2. **Group chat** — groups up to 1024 members; a message fans out to all.
3. **Message ordering** — messages within a conversation appear in a consistent
   order for all participants.
4. **Delivery guarantees** — a sent message is eventually delivered even if the
   recipient is offline at send time.
5. **Delivery & read receipts** — sent ✓, delivered ✓✓, read ✓✓ (blue).
6. **Message history** — fetch past messages, paginated, for a conversation.

### Presence & Real-time Signals
7. **Online/offline status** — show whether a user is currently connected.
8. **Typing indicators** — transient "user is typing…" signals.
9. **Last seen** — timestamp of last activity.

### Media & Rich Content
10. **Media messages** — images, video, documents, voice notes.
11. **Media delivery** — upload once, share a reference; not embedded in the
    message.

### Multi-device
12. **Multi-device sync** — a user's phone, tablet, and web client stay in sync;
    a message sent from one device appears on the others.

### Group Management
13. **Group lifecycle** — create, add/remove members, leave, admin controls.
14. **Group metadata** — name, icon, description.

## Non-Functional Requirements

### Scale Requirements
- **Users**: 2 billion registered, 500 million daily active.
- **Messages**: 100 billion messages/day → ~1.2M messages/second average,
  ~3M/second peak.
- **Connections**: ~100 million concurrent WebSocket connections at peak.
- **Groups**: average 10 members; large groups up to 1024.

### Performance Requirements
- **Message delivery (online recipient)**: < 500ms end-to-end, 99th percentile.
- **Message send acknowledgement**: < 100ms to the sender.
- **History fetch**: < 300ms for a page of 50 messages.
- **Presence update propagation**: < 2 seconds.

### Availability and Reliability
- **Uptime**: 99.99% (52 minutes downtime/year) — messaging is expected to
  always work.
- **Message durability**: a message acknowledged to the sender must not be lost
  (durability 99.999999%).
- **Delivery**: at-least-once, with client-side dedupe to present exactly-once.
- **Disaster recovery**: RTO < 15 min, RPO < 1 min.

### Consistency Requirements
- **Per-conversation ordering**: strong — messages in a chat must not reorder.
- **Cross-conversation**: no global ordering needed.
- **Presence**: eventually consistent (a 2-second staleness is fine).
- **Receipts**: eventually consistent; a slightly late ✓✓ is acceptable.

### Security & Privacy
- **End-to-end encryption**: message content is unreadable by the server.
- **Authentication**: device-level auth; session management.
- **Abuse prevention**: rate limiting, spam/blocking.
- **Data privacy**: GDPR-style deletion; message retention policy.

### Cost & Efficiency
- **Connection efficiency**: persistent connections, not polling — battery and
  bandwidth matter on mobile.
- **Storage tiering**: hot recent messages fast; cold history cheap.

## Scale Estimation (worked)

**Connections.** 100M concurrent connections. A single server can hold ~1M
WebSocket connections with careful tuning (file descriptors, memory per
connection). So you need **≥ 100 connection gateway nodes**, plus headroom →
~150–200.

**Messages.** 1.2M messages/sec average. Each message write is small (~200
bytes) → ~240 MB/s of message writes, ~20 TB/day. Over a year, tens of
petabytes — storage must be tiered and sharded.

**Fan-out.** A group message to 1024 members is 1024 deliveries. Average group
is 10, so average fan-out is ~10; but large groups dominate cost. Design for
the average, special-case the large groups.

**Storage.** Per message: ~200 bytes content + metadata. 100B messages/day ×
200B ≈ 20 TB/day of new message data before replication. At 3× replication,
~60 TB/day. This forces cheap, horizontally sharded storage with aggressive
tiering (recent → SSD, old → object storage).

**Presence.** 500M DAU, each with a heartbeat every ~30s → ~16M presence
updates/sec if naive. Must be batched and aggregated, not one write per
heartbeat.

## What's Out of Scope

- Actual end-to-end encryption implementation (noted as a requirement).
- Voice/video calls (that's the WebRTC conferencing problem).
- Payments, status/stories (adjacent features).
- Full-text search across messages.

## Success Metrics

- P99 message delivery latency (online recipient).
- Delivery success rate (messages acknowledged vs. delivered).
- Connection stability (reconnect rate, dropped connections).
- Infrastructure cost per active user.
