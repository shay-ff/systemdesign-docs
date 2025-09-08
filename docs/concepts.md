# Core Concepts (HLD overview + LLD intro)

## High-Level Design (HLD)
- Focus: components, interactions, scaling, trade-offs.
- Key ideas: load balancing, caching layers, databases, message queues, CDNs, replication & sharding.
- Non-functional requirements: latency, throughput, availability, consistency, durability.

### Typical HLD building blocks
- Clients → CDN → Edge → Load Balancer → API Gateway → Services → Caches/DBs/Queues.
- Data path vs control path; synchronous vs asynchronous flows.
- Read vs write heavy paths; fan-out vs aggregation.

### Scaling patterns
- Vertical first, then horizontal: stateless services behind LBs, add read replicas, shard when needed.
- Partition by key (user_id, content_id); avoid cross-partition joins on hot paths.
- Use queues to absorb bursts; backpressure and retries with dead-letter queues.

## Low-Level Design (LLD)
- Focus: classes, interfaces, data models, algorithms.
- Use C++ for concrete examples: class diagrams, headers, implementations, unit-tests.

### LLD steps (for interviews)
1. Identify entities and relationships; define interfaces.
2. Choose data structures and algorithms with complexity bounds.
3. Draw class diagrams and key sequences.
4. Implement a minimal, testable slice.

## Quick glossary
- CAP theorem — Consistency, Availability, Partition tolerance.
- ACID vs BASE — strong vs eventual consistency patterns.
- CQRS — separate read/write models.
- Event sourcing — sequence of immutable events as source of truth.

Additional terms:
- Idempotency — multiple identical requests have the same effect as one.
- Backpressure — preventing overload by signaling upstream to slow down.
- Circuit breaker — fail fast to avoid cascading failures.

For a concise cheatsheet, see `docs/cheatsheet.md`.
