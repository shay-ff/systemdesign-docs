# Common design patterns — what, when and trade-offs

Short descriptions and practical notes.

- Cache-aside
  - What: app reads cache, on miss reads DB and populates cache.
  - When: read-heavy, can tolerate some staleness.
  - Trade-off: simpler, but cache stampede and invalidation complexity.

- Write-through / Write-behind
  - Write-through: writes go through cache to DB (stronger read-your-writes guarantees).
  - Write-behind: writes return quickly and update DB asynchronously (higher throughput, risk on crash).

- Circuit Breaker
  - Quickly fail calls to an unhealthy downstream service to protect capacity.

- Bulkhead
  - Isolate resources (threads, pools) per subsystem to avoid noisy neighbors.

- Token Bucket / Leaky Bucket
  - Token bucket: allows bursts up to capacity; refill at steady rate.
  - Leaky bucket: enforces constant output rate.

- Producer-Consumer (Message Queue)
  - Decouple producers and consumers; smooth spikes; support retries and DLQs.

- Leader election / Consensus (Raft)
  - When you need a single source of truth or coordinated writes. Use managed systems (etcd/Consul) when possible.
