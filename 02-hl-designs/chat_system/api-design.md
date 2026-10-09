# Chat System — API & Protocol Design

The system has two interfaces: a **WebSocket protocol** for real-time messaging
and presence, and a **REST API** for everything that is request/response
(history, media, auth, group management). Knowing which interaction belongs on
which transport is part of the design.

## Why two transports

| Concern | Transport | Reason |
|---|---|---|
| Send/receive messages | WebSocket | Server must *push* to the client; polling can't meet < 500ms |
| Presence, typing | WebSocket | High-frequency, transient, tiny payloads |
| Receipts | WebSocket | Real-time signal to the sender |
| Auth | REST | Standard request/response, needs to run before the socket opens |
| History | REST | Request/response, paginated |
| Media upload/download | REST + signed URLs | Large payloads; don't tie up the message socket |
| Group management | REST | Infrequent, request/response |

**Rule of thumb:** if the *server* needs to initiate, use WebSocket. If the
*client* asks and the server answers, use REST.

---

## WebSocket Protocol

### Connection

```
wss://chat.example.com/ws?token=<auth-token>&deviceId=<id>
```

The token is obtained from `POST /auth/login`. On connect, the gateway
registers the session in the Session Registry and starts heartbeats.

### Envelope

Every frame is JSON with a `type` discriminator:

```json
{ "type": "<frame-type>", "id": "<client-msg-id>", "ts": 1696118400000, "payload": { ... } }
```

`id` is a client-generated idempotency key (UUID). The server echoes it in the
ack so the client can match request to response and dedupe retries.

### Client → Server frames

**Send a message**
```json
{
  "type": "message.send",
  "id": "c-8f3a...",
  "payload": {
    "conversationId": "conv-123",
    "contentType": "text",
    "content": "hey, are we still on for 6?",
    "replyTo": null
  }
}
```

**Typing indicator**
```json
{ "type": "typing", "payload": { "conversationId": "conv-123", "state": "started" } }
```

**Receipt acknowledgement**
```json
{ "type": "receipt", "payload": { "messageId": "srv-991", "status": "read" } }
```

**Heartbeat**
```json
{ "type": "ping", "payload": {} }
```

### Server → Client frames

**Send acknowledgement** (sender knows the message is durably stored)
```json
{
  "type": "message.ack",
  "payload": {
    "clientId": "c-8f3a...",
    "messageId": "srv-991",
    "conversationId": "conv-123",
    "sequence": 40217,
    "serverTs": 1696118400123
  }
}
```
`sequence` is the per-conversation monotonic number. The client uses it to
order and to detect gaps (a gap means a missed message → fetch history).

**Incoming message** (delivered to recipient)
```json
{
  "type": "message.new",
  "payload": {
    "messageId": "srv-991",
    "conversationId": "conv-123",
    "senderId": "user-42",
    "contentType": "text",
    "content": "hey, are we still on for 6?",
    "sequence": 40217,
    "serverTs": 1696118400123
  }
}
```

**Delivery / read receipt** (propagated to the sender)
```json
{ "type": "receipt.update",
  "payload": { "messageId": "srv-991", "userId": "user-7", "status": "delivered" } }
```

**Presence update**
```json
{ "type": "presence", "payload": { "userId": "user-7", "status": "online", "lastSeen": null } }
```

**Typing indicator**
```json
{ "type": "typing", "payload": { "conversationId": "conv-123", "userId": "user-7", "state": "started" } }
```

**Pong**
```json
{ "type": "pong", "payload": {} }
```

### Delivery semantics on the socket

- **At-least-once.** If the server doesn't receive an ack within a timeout, it
  retries. The client dedupes by `messageId`.
- **Ordering.** The client orders by `sequence` per conversation; a gap triggers
  a history fetch from the last known sequence.
- **Backpressure.** If a slow client's send buffer fills, the server drops the
  connection and the client reconnects and catches up from `sequence` — it does
  not buffer unboundedly on the server.

---

## REST API

### Authentication

```
POST /auth/login
  { "phone": "+91...", "otp": "123456", "deviceId": "..." }
  → 200 { "token": "...", "userId": "user-42", "expiresIn": 3600 }

POST /auth/refresh
  { "refreshToken": "..." } → 200 { "token": "..." }
```

### Conversations & history

```
GET /conversations
  → 200 [ { "id": "conv-123", "type": "direct|group", "title": "...", "lastMessageTs": ... } ]

GET /conversations/{id}/messages?before=<sequence>&limit=50
  → 200 { "messages": [ ... ], "nextBefore": 40167 }

POST /conversations
  { "type": "group", "memberIds": ["u1","u2","u3"], "title": "Trip" }
  → 201 { "id": "conv-456", ... }
```

Pagination is **cursor-based on `sequence`** (not offset) — offsets break when
new messages arrive between pages, and sequence is the natural order key.

### Media

```
POST /media/upload-url
  { "contentType": "image/jpeg", "sizeBytes": 204800 }
  → 200 { "mediaId": "m-77", "uploadUrl": "https://blob.../signed", "expiresIn": 300 }

# Client uploads bytes directly to uploadUrl (offloads the app servers)

POST /media/{mediaId}/complete
  → 200 { "mediaId": "m-77", "status": "ready", "url": "https://cdn.../m-77" }
```

Then a message references the media: `{ "contentType": "image", "mediaId": "m-77" }`.
Media is uploaded **once** and referenced by every recipient — never embedded in
the message or fanned out per recipient.

### Group management

```
POST   /groups/{id}/members       { "userIds": ["u9"] } → 200
DELETE /groups/{id}/members/{uid}                       → 204
PATCH  /groups/{id}               { "title": "New name" } → 200
```

### Receipts (batch, for catch-up)

```
GET /conversations/{id}/receipts?after=<sequence>
  → 200 { "receipts": [ { "messageId": "srv-991", "userId": "u7", "status": "read" } ] }
```

---

## Error model

| Code | Meaning | Client action |
|---|---|---|
| 400 | Malformed request | Fix and retry |
| 401 | Token expired | Refresh, reconnect |
| 403 | Not a group member | Surface error |
| 409 | Duplicate (same client id) | Treat as success (idempotent) |
| 429 | Rate limited | Back off (respect Retry-After) |
| 5xx | Server error | Retry with exponential backoff |

The **409 duplicate** path is important: a client that retries a send after a
timeout must not create a second message. The server dedupes on the client
`id` and returns the original result — idempotent sends.

---

## Rate limiting

- **Per-user send rate** — e.g. 60 messages/minute, enforced with a token bucket
  at the gateway (see `../../01-ll-designs/rate_limiter/`).
- **Per-connection frame rate** — caps typing/presence spam.
- **Per-IP connection rate** — prevents connection floods.

Rate limit at the **gateway**, before the message reaches the core services —
reject cheaply at the edge.

---

## Versioning

The WebSocket envelope carries no version field; instead the server supports a
**capability handshake** on connect (client declares supported frame types), so
new frame types roll out without breaking old clients. REST uses URL versioning
(`/v2/...`) only when a breaking change is unavoidable.
