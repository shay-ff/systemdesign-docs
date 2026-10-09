# Chat System — Data Model & Storage

The storage design is driven by two facts: **messages are append-only and
ordered per conversation**, and **the read pattern is "the last N messages of a
conversation"**. Everything below follows from those.

## Storage choices at a glance

| Data | Store | Why |
|---|---|---|
| Messages | Cassandra (wide-column, partitioned by conversation) | Append-heavy, ordered, huge volume, no cross-conversation queries |
| Users / groups / membership | Postgres (relational) | Small, relational, needs transactions and joins |
| Sessions (user → connections) | Redis | Ephemeral, high-frequency, TTL-based |
| Offline inbox | Cassandra or a queue-backed table | Per-recipient pending deliveries |
| Receipts | Cassandra | High write volume, keyed by message |
| Media metadata | Postgres | Small, relational |
| Media bytes | Object store (S3-like) | Cheap, huge, served via CDN |

The key decision: **messages do not live in the relational database.** At 100B
messages/day, a single relational DB cannot absorb the write volume, and
messages are never joined against other tables. A wide-column store partitioned
by conversation is the right shape.

---

## Messages table (Cassandra)

```sql
CREATE TABLE messages (
    conversation_id  uuid,
    sequence         bigint,      -- per-conversation monotonic order
    message_id       uuid,
    sender_id        uuid,
    content_type     text,        -- text | image | video | file | system
    content          text,        -- text body, or media_id reference
    reply_to         uuid,
    server_ts        timestamp,
    PRIMARY KEY ((conversation_id), sequence)
) WITH CLUSTERING ORDER BY (sequence DESC);
```

**Partition key `conversation_id`.** All messages of one conversation live on
one partition (one node), which is what makes per-conversation ordering free:
the partition is an ordered log. This is the single most important modelling
decision.

**Clustering key `sequence`, ordered DESC.** The dominant query is "the latest
messages of this conversation", which is now a single-partition, ordered,
range-limited read — the cheapest possible access pattern:

```sql
SELECT * FROM messages
WHERE conversation_id = ? AND sequence < ?
ORDER BY sequence DESC LIMIT 50;
```

That is the history-pagination query, and it never touches more than one node.

**The sequence number.** Each conversation's owner node assigns `sequence`
monotonically (a lightweight counter per partition). Two messages to the same
conversation get distinct, increasing sequences → total order within the
conversation. There is **no global sequence** and none is needed: ordering is
per-conversation, so a global counter would be a scaling bottleneck for no
benefit.

**Wide partitions.** A very active conversation could grow a huge partition.
Mitigations: cap messages per partition by **time-bucketing** (partition key
becomes `(conversation_id, month)` for long-lived chats), or TTL old messages
to cold storage. State this as a known concern.

---

## Conversation index (Cassandra)

To list a user's conversations quickly, maintain a per-user table:

```sql
CREATE TABLE user_conversations (
    user_id          uuid,
    last_message_ts  timestamp,
    conversation_id  uuid,
    last_sequence    bigint,
    unread_count     int,
    PRIMARY KEY ((user_id), last_message_ts, conversation_id)
) WITH CLUSTERING ORDER BY (last_message_ts DESC);
```

`SELECT ... WHERE user_id = ? LIMIT 20` returns the user's most recent
conversations — the chat-list screen. Updated on every message (or by a
per-user materialised view / background job).

---

## Users and groups (Postgres)

```sql
CREATE TABLE users (
    user_id       uuid PRIMARY KEY,
    phone         text UNIQUE NOT NULL,
    display_name  text,
    avatar_url    text,
    last_seen     timestamp,
    created_at    timestamp NOT NULL DEFAULT now()
);

CREATE TABLE groups (
    group_id      uuid PRIMARY KEY,
    title         text,
    icon_url      text,
    created_by    uuid REFERENCES users(user_id),
    created_at    timestamp NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    group_id      uuid REFERENCES groups(group_id),
    user_id       uuid REFERENCES users(user_id),
    role          text NOT NULL DEFAULT 'member',  -- member | admin
    joined_at     timestamp NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);
```

Group membership is relational and transactional (adding a member must be
atomic with a permission check) — Postgres is the right tool. Group size is
bounded (≤1024), so the member list is small and cacheable.

**Group fan-out lookup:** resolving a group message to its members is
`SELECT user_id FROM group_members WHERE group_id = ?` — cached in Redis per
group, invalidated on membership change.

---

## Sessions (Redis)

```
KEY   session:{userId}            -> SET of "deviceId:gatewayNode:connId"
TTL   60s (refreshed by heartbeat)

KEY   presence:{userId}           -> "online|offline"
TTL   60s
```

The session registry is the answer to "where is this user connected right
now?". It is ephemeral by nature — if a gateway dies, its sessions expire via
TTL, and the user reconnects elsewhere. **Never make this a durable table**;
liveness is a TTL concept.

---

## Offline inbox (per-recipient pending queue)

When the recipient is offline, the message goes here and is delivered on
reconnect:

```sql
CREATE TABLE inbox (
    user_id          uuid,
    sequence         bigint,      -- monotonic per recipient
    message_id       uuid,
    conversation_id  uuid,
    enqueued_at      timestamp,
    PRIMARY KEY ((user_id), sequence)
);
```

On reconnect, the client sends its **last known per-conversation sequence**
(or the gateway reads the inbox) and the pending messages flow. Once delivered
and acked, the inbox entry is deleted.

**Alternative:** a durable queue (Kafka/SQS) per recipient shard. The table
form is simpler to reason about and gives replay; the queue form scales the
"hot" delivery path better. Both are defensible — name the trade-off.

---

## Receipts (Cassandra)

```sql
CREATE TABLE receipts (
    message_id  uuid,
    user_id     uuid,
    status      text,      -- delivered | read
    ts          timestamp,
    PRIMARY KEY ((message_id), user_id)
);
```

Written when a recipient acks delivery/read. Read back in batch when the sender
fetches a conversation (to render ✓✓). High write volume, keyed by message —
the same shape as messages, so the same store.

---

## Media metadata (Postgres)

```sql
CREATE TABLE media (
    media_id     uuid PRIMARY KEY,
    uploader_id  uuid REFERENCES users(user_id),
    content_type text,
    size_bytes   bigint,
    storage_key  text NOT NULL,     -- key in the object store
    status       text NOT NULL,     -- pending | ready | failed
    created_at   timestamp NOT NULL DEFAULT now()
);
```

Bytes live in the object store under `storage_key`; only metadata is relational.
Served to clients via CDN URLs derived from `storage_key`.

---

## Sharding and scaling the stores

- **Messages**: partitioned by `conversation_id` → naturally sharded across
  Cassandra nodes; a hot conversation is a hot partition (monitor and
  time-bucket if needed).
- **Users/groups**: small enough to stay on a single Postgres primary with
  read replicas for a long time; shard by `user_id` only if forced.
- **Sessions**: Redis Cluster, sharded by `userId`.
- **Media**: object store scales horizontally by design; CDN absorbs reads.

## Consistency summary

| Data | Consistency | Why |
|---|---|---|
| Messages (per conversation) | Strong ordering | Sequence assigned on one node |
| Message durability | Strong (write before ack) | Sender ack means "durable" |
| Cross-conversation | None needed | No global order |
| Receipts | Eventual | A late ✓✓ is invisible |
| Presence | Eventual (TTL) | 2s staleness is fine |
| Group membership | Strong (Postgres txn) | Permission correctness |

The design deliberately mixes consistency levels: pay for strong consistency
only where the user would notice its absence (ordering, durability, membership),
and use eventual consistency everywhere else.
