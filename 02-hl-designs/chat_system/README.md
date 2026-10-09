# Chat System (WhatsApp-like) — System Design

## Overview

Design a real-time messaging system like WhatsApp or Messenger: one-to-one and
group messaging, presence, delivery/read receipts, media sharing, and
multi-device sync. The system maintains ~100 million concurrent connections and
delivers ~1.2 million messages per second with per-conversation ordering and
at-least-once delivery.

## The One Idea That Matters

"Store a message" is easy. The hard part is **routing a message to a recipient
who may be on any of several devices, may be offline, and must receive messages
in order** — while the server holds millions of live connections. Almost every
design decision below serves real-time delivery at scale.

## Key Features

- **Real-time messaging** — 1:1 and group, with per-conversation ordering
- **Persistent connections** — WebSocket gateways, not polling
- **Delivery guarantees** — at-least-once, with client-side dedupe
- **Receipts** — sent / delivered / read
- **Presence** — online/offline, typing, last seen
- **Multi-device sync** — one account, several devices in sync
- **Media** — upload once, share a reference
- **Message history** — paginated fetch of past messages

## System Requirements

### Functional Requirements
1. Send/receive messages (1:1 and group up to 1024 members)
2. Consistent per-conversation ordering for all participants
3. At-least-once delivery, even to offline recipients
4. Delivery and read receipts
5. Presence: online/offline, typing, last seen
6. Media messages (upload once, reference everywhere)
7. Multi-device sync
8. Group management and message history

### Non-Functional Requirements
1. **Scale**: 100B messages/day (~1.2M/sec avg), 100M concurrent connections
2. **Latency**: < 500ms P99 delivery to online recipient; < 100ms send ack
3. **Availability**: 99.99% uptime
4. **Durability**: acknowledged messages never lost
5. **Ordering**: strong within a conversation; eventual for presence/receipts
6. **Security**: end-to-end encryption; auth; rate limiting

## Architecture Components

This design includes:

- **Connection gateway** — terminates WebSocket connections; routes to the
  owning service; the scaling unit is "connections per node"
- **Session/presence service** — maps user → active connection(s); heartbeats
- **Message service** — accepts, validates, persists, and routes messages
- **Message store** — sharded, ordered per conversation (Cassandra-style)
- **Delivery service + offline queue** — per-recipient inbox for offline users
- **Group service** — group membership and fan-out
- **Media service + blob store** — upload/download of attachments
- **Notification service** — push to offline devices (APNs/FCM)
- **Receipt service** — delivery/read acknowledgements

## The Core Flow (one message)

```
Sender → Gateway → Message Service
                     │ 1. persist to conversation log (ordering)
                     │ 2. look up recipient session(s)
                     ├─ online  → push via recipient's gateway → ack → delivered ✓✓
                     └─ offline → write to recipient's inbox queue
                                    → push notification (APNs/FCM)
```

## Files in this Design

- `requirements.md` — Detailed functional and non-functional requirements with scale estimates
- `architecture.puml` — System architecture diagram
- `api-design.md` — API and protocol specifications (WebSocket + REST)
- `database-schema.md` — Data model, sharding, and ordering strategy
- `scaling-strategy.md` — Horizontal scaling, connection handling, bottlenecks
- `tradeoffs.md` — Key design decisions and alternatives
- `solution.md` — Complete end-to-end walkthrough

## Key Design Decisions (preview)

1. **WebSocket, not polling** — persistent connections are the only way to
   deliver in < 500ms without melting the server with polls.
2. **Per-conversation ordering via a monotonic sequence** — each conversation is
   a shard; the shard assigns sequence numbers. Ordering is per-conversation,
   not global.
3. **Store-then-route** — persist before acknowledging the sender; the recipient
   can be offline forever and the message survives.
4. **Recipient inbox for offline delivery** — a queue per recipient device
   handles "deliver when they come back".
5. **At-least-once + client dedupe** — exactly-once across a network is a
   fiction; the client dedupes by message ID to present exactly-once.
6. **Multi-device = multiple sessions** — a message fans out to every active
   session of the recipient, each acked independently.

See `solution.md` for the full walkthrough and `tradeoffs.md` for the
alternatives considered.
