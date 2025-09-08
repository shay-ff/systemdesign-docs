# Common Patterns

- Cache-aside, write-through, write-behind
- Circuit Breaker, Bulkhead
- Token Bucket / Leaky Bucket (rate limiting)
- Producer-Consumer with message queue
- Leader election + consensus (Raft summary included)

## When to use which
- **Cache-aside**: reads dominate, tolerate slight staleness. App loads from DB, populates cache on miss.
- **Write-through**: need read-your-writes. Writes go through cache to DB; higher write latency.
- **Write-behind**: high write throughput; risk of data loss on crash; use durable queues.

- **Circuit Breaker**: failing downstreams cause timeouts; trip to fast-fail and auto-recover.
- **Bulkhead**: isolate resources per tenant/feature to avoid noisy neighbor failures.

- **Token Bucket**: allow bursts up to bucket size, refill at rate; great for APIs.
- **Leaky Bucket**: smooths traffic to constant rate; good for egress shaping.

- **Producer-Consumer**: decouple work; handle spikes; require idempotency + retries.

- **Leader Election (Raft/ZK/etcd)**: single-writer coordination; avoid split-brain; prefer managed services.

## Trade-offs cheats
- More caching → lower read latency, higher complexity, potential staleness.
- More replication → higher availability, potential consistency lag.
- More indexing → faster reads, slower writes, more storage.
- More async → better latency for users, more eventual consistency and retries.
