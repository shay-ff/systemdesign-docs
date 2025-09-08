# Cheatsheet & Quick Rules

- **Latency vs Throughput**: optimize for target SLO; batching increases throughput but can increase latency.
- **Cache hit ratio**: benefit ~= (hit_ratio * read_latency_saved) - eviction/consistency cost.
- **Sizing**: estimate requests/sec, average payload, concurrency -> compute bandwidth & storage.
- **Load balancer**: stateless LB + sticky sessions only if necessary.

## Back-of-the-envelope
- **Bandwidth**: `requests_per_sec * payload_bytes` → bytes/sec (divide by 8 for bits/sec).
- **Daily storage**: `writes_per_sec * avg_record_bytes * 86400`.
- **Peak vs P95**: design for p95 or p99, not averages. Peak RPS ≈ 2–5x avg.
- **Concurrency**: Little’s Law `L = λ * W` → concurrent_requests = arrival_rate * average_latency.

## Caching
- **Where**: client → CDN → edge cache → service cache (Redis/Memcached).
- **Patterns**: cache-aside (most common), write-through (reads consistent), write-back (higher write throughput, risk on crash).
- **TTL**: set per use-case; watch stampede (use jitter, request coalescing).

## Datastores
- **RDBMS**: strong consistency, joins, transactions; vertical scaling then read replicas, sharding later.
- **NoSQL**: key-value/doc/columnar; denormalize; eventual consistency; great horizontal scale.
- **Indexes**: reads↑, writes↓, storage↑. Add only when a query needs it.

## Consistency & Availability
- **CAP**: during partition, choose C or A (P is given). Most large systems lean AP with bounded staleness.
- **Replication**: sync (lower RPO, higher latency) vs async (higher availability, possible lag).
- **Leader election**: use managed services or algorithms like Raft; avoid DIY.

## Messaging
- **When to queue**: smooth spikes, decouple services, avoid user-facing latency.
- **Ordering**: per-key ordering with partition keys; global ordering is expensive.
- **At-least-once**: design idempotent consumers; dedupe with keys or versioning.

## Rate Limiting
- **Algorithms**: token bucket (bursts allowed), leaky bucket (constant drain), fixed/sliding window.
- **Placement**: at gateway, per user/IP/api-key; distinct limits for read/write.

## Observability
- **Metrics**: RED (Rate, Errors, Duration) + USE (Utilization, Saturation, Errors).
- **Tracing**: propagate correlation IDs; sample intelligently.
- **Logging**: structured JSON logs with levels; centralize.

## Interview Flow (HLD)
1. Clarify requirements (functional, non-functional, SLAs).
2. Capacity estimates (RPS, storage, bandwidth, QPS per component).
3. High-level diagram (clients, LB, services, DBs, caches, queues, CDN).
4. Deep-dives: data model, caching, consistency, failures, backfills, migrations.
5. Trade-offs and evolution roadmap.
