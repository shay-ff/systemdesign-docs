# HLD Quick Reference — One-Pagers for Common Interview Systems

> 🧭 **Navigation**: [← HLD Designs](README.md) | [📍 Full Navigation](../NAVIGATION.md)

This file is the *breadth* companion to the deep-dive folders in `02-hl-designs/`.
Each section is a self-contained one-pager you can revise in five minutes the
night before an interview: the scale estimate, the core components, the one
interesting problem, and the trade-offs an interviewer will probe.

The six systems with their own folders (Twitter, YouTube, Netflix, Uber, URL
Shortener, LLM Serving) plus the two full folders added alongside this file
(Chat System, Payment Gateway) get the exhaustive treatment. Everything else
you're likely to be asked lives here.

**How to use this file:** don't memorise it. Read a one-pager, close it, and try
to reconstruct the architecture on a blank page. The value is in the
reconstruction, not the reading.

---

## 1. Typeahead / Autocomplete

**Ask:** "Suggest the top-k completions for a prefix as the user types."

**Scale:** 10M searches/day → ~500 QPS average, 5k QPS peak. Suggestions must
return in < 100ms (the user is typing).

**Core idea:** a **trie** (prefix tree) where each node stores the top-k
completions for the prefix ending there. Precompute the top-k per node offline
from historical query logs; serve the trie from memory.

**Architecture:**
- **Offline:** aggregate query logs → count frequencies → build/update the trie
  (batch job, e.g. hourly). Rebuild a fresh trie and atomically swap it in.
- **Serving:** trie sharded across nodes by prefix; each node holds a subset.
  A query hits one shard (the prefix's owner). Replicate each shard for
  availability.
- **Cache:** Redis in front of the trie for the hottest prefixes.
- **Client:** debounce keystrokes (~50ms), cancel stale requests, cache recent
  prefixes in the browser.

**The interesting problem:** the trie is huge and read-mostly but must be
*updatable*. Two options: (a) rebuild-and-swap (simple, slight staleness — the
usual answer), or (b) a write-ahead log for incremental inserts (fresh but
complex). State the trade-off.

**Trade-offs to name:**
- *Trie vs. hash map of all prefixes:* the map is O(1) lookup but memory blows
  up storing every prefix's top-k; the trie shares prefixes.
- *Personalisation:* global top-k is cheap; per-user suggestions need a second
  ranking pass over the user's history — much more expensive, usually a
  documented extension.
- *Consistency:* suggestions are eventually consistent with the query log;
  nobody notices a suggestion being a few minutes stale.

---

## 2. Web Crawler

**Ask:** "Crawl the web, index pages, respect politeness and avoid re-crawling
forever."

**Scale:** billions of pages; crawl rate bounded by politeness (not by your
bandwidth).

**Core components:**
- **URL Frontier** — the to-do queue, prioritised (PageRank-ish priority + a
  politeness component per host). Two levels: a front queue (priority) and a
  back queue per host (politeness, one request at a time per host).
- **DNS resolver** — cache DNS; DNS is a surprising bottleneck.
- **Fetcher** — HTTP client pool, robots.txt respect, timeouts.
- **Content parser** — extract links + content; dedupe.
- **Deduplication** — **Bloom filter** or content hashing to avoid re-processing
  the same content reached via different URLs.
- **Storage** — raw HTML (blob store) + extracted content (index).
- **Scheduler** — re-crawl policy (pages change at different rates).

**The interesting problems:**
1. **Politeness / politeness** — never hammer one host; a per-host queue with a
   delay. This is the #1 design point.
2. **Cycle avoidance** — the web is full of loops; track visited URLs (Bloom
   filter for memory efficiency, accepting false positives = missed pages).
3. **Freshness vs. coverage** — you can't crawl everything often; prioritise by
   importance and change frequency.

**Trade-offs to name:**
- *BFS vs. priority crawl:* BFS is simple but crawls junk; priority needs a
  scoring function.
- *Bloom filter false positives:* a false positive means skipping a page you
  haven't seen — acceptable for a crawler, catastrophic for a payment system.
  Match the data structure to the cost of being wrong.

---

## 3. Distributed File Storage (Dropbox / Google Drive)

**Ask:** "Sync files across a user's devices, with versioning and sharing."

**Scale:** 500M users, ~200GB average → exabytes. Sync must be incremental.

**Core idea:** store **file metadata** separately from **file content**. Content
is chunked and stored in a blob store; metadata (chunk list, versions, ACLs) in
a database.

**Architecture:**
- **Client** — watches the filesystem, chunks files (e.g. 4MB blocks),
  computes a content hash per chunk, syncs only changed chunks.
- **Block server / blob store** — stores chunks, content-addressed (hash →
  chunk). Identical chunks dedupe across files and users.
- **Metadata service** — file tree, versions, permissions, chunk manifests.
- **Sync service** — long-poll or WebSocket notifications to clients; a client
  learns "something changed" and pulls the delta.
- **Notification** — the sync trigger. Polling is too slow/wasteful; use
  long-polling or push.

**The interesting problems:**
1. **Incremental sync** — chunking + content hashing means only changed chunks
   transfer. This is the whole game.
2. **Conflict resolution** — two devices edit offline; on reconnect, who wins?
   Dropbox creates a "conflicted copy". Last-writer-wins loses data; vector
   clocks / CRDTs are the rigorous answer.
3. **Consistency of the metadata tree** — the file tree must be strongly
   consistent per user; content can be eventually consistent.

**Trade-offs to name:**
- *Chunk size:* small chunks → finer deltas but more metadata/overhead; large
  chunks → less overhead but coarser sync.
- *Content-addressed storage:* dedupe is free, but deletion needs reference
  counting (a chunk shared by two files can't be deleted until both go).

---

## 4. Notification Service

**Ask:** "Send push/SMS/email to users at scale, with retries and preferences."

**Scale:** millions of notifications/hour across channels.

**Core components:**
- **API + validation** — accept a notification request, validate.
- **Queue** — decouple ingestion from delivery (Kafka/SQS). One topic per
  channel or a unified topic with channel routing.
- **Workers** — per-channel sender pools; retry with exponential backoff.
- **Third-party integrations** — APNs (iOS push), FCM (Android), Twilio (SMS),
  SendGrid (email). Each has its own rate limits and failure modes.
- **Preference store** — user opt-outs per channel/category (mandatory for
  compliance).
- **Dead-letter queue** — after N retries, park the message; alert.

**The interesting problems:**
1. **Retries and idempotency** — a notification must not be sent twice on retry.
   Use a notification ID + dedupe at the provider or in your own ledger.
2. **Rate limiting per provider** — providers throttle; a token bucket per
   channel smooths the burst.
3. **Priority** — OTPs must not queue behind marketing blasts; separate queues
   or priority levels.

**Trade-offs to name:**
- *At-least-once vs. exactly-once:* exactly-once is essentially impossible
  across third-party providers; at-least-once + idempotent dedupe is the real
  answer.
- *Sync vs. async send:* never send synchronously in the request path — the
  provider latency becomes your latency.

---

## 5. Video Conferencing (Zoom)

**Ask:** "N-way real-time audio/video with screen share and recording."

**Scale:** millions of concurrent meetings; latency budget is brutal (< 150ms
mouth-to-ear).

**Core idea:** **WebRTC** for media transport; a **SFU** (Selective Forwarding
Unit) for group calls rather than full mesh or a mixing MCU.

**Architecture:**
- **Signaling service** — sets up the call (WebSocket); exchanges SDP
  offers/answers and ICE candidates. Not in the media path.
- **SFU** — each participant sends one upstream stream; the SFU forwards to
  others (selective: only the streams a receiver is subscribed to, e.g. only
  the active speaker at high quality). Scales far better than mesh (N²
  connections).
- **TURN/STUN** — NAT traversal; TURN relays when direct P2P fails.
- **Recording** — a server-side participant that subscribes to streams and
  writes to blob storage.
- **Geo-distributed SFUs** — pick the nearest SFU; bridge SFUs across regions.

**The interesting problems:**
1. **Mesh vs. SFU vs. MCU:** mesh is N² uploads (fails past ~4 people); MCU
   mixes streams server-side (CPU-heavy, adds latency); SFU forwards (the
   sweet spot — what Zoom/Meet use).
2. **Simulcast** — senders publish multiple quality layers; the SFU picks the
   layer per receiver based on their bandwidth. This is how quality adapts.
3. **Active speaker detection** — audio-level analysis; drives who gets the
   high-quality stream.

**Trade-offs to name:**
- *Latency vs. quality:* real-time (WebRTC, UDP, lossy) vs. buffered (HLS,
  TCP, smooth). Conferencing chooses latency.
- *SFU forwarding cost:* bandwidth-heavy on the server; you trade server
  bandwidth for client CPU.

---

## 6. Search Engine (Elasticsearch-like)

**Ask:** "Index documents and return ranked results for a query in < 200ms."

**Scale:** billions of documents; sub-second search.

**Core idea:** an **inverted index** (term → posting list of doc IDs), sharded
and replicated.

**Architecture:**
- **Ingestion pipeline** — documents → tokenise → analyse (stemming, stop
  words) → build inverted index. Batch + near-real-time (small refresh
  interval).
- **Index sharding** — documents split across shards (by hash or range).
- **Replication** — each shard has replicas for availability + read throughput.
- **Query path** — a query hits all shards (scatter), each returns its top-k,
  a coordinator merges (gather) and returns the global top-k. This is the
  **scatter-gather** pattern.
- **Ranking** — TF-IDF / BM25 by default; learning-to-rank models on top.

**The interesting problems:**
1. **Scatter-gather** — every query touches every shard; tail latency is set by
   the slowest shard. Mitigate with replica selection and timeouts.
2. **Near-real-time indexing** — a refresh interval (e.g. 1s) means freshly
   indexed docs aren't instantly searchable; the trade-off is index-write
   throughput.
3. **Relevance tuning** — the hard part is ranking, not infrastructure.

**Trade-offs to name:**
- *Index size vs. query speed:* richer analysis = larger index = slower.
- *Shard count:* too few limits parallelism; too many adds coordination
  overhead. Right-size once, over-shard slightly for growth.

---

## 7. Distributed Cache (Redis-like)

**Ask:** "An in-memory key-value store with eviction, replication, and
horizontal scale."

**Scale:** millions of ops/sec, sub-millisecond latency.

**Core components:**
- **Hash table** — the core store, in memory.
- **Eviction** — LRU/LFU when memory fills (see `01-ll-designs/lru_cache/`).
- **Replication** — leader-follower; the leader serves writes, followers serve
  reads and provide failover.
- **Sharding** — **consistent hashing** across nodes (see
  `01-ll-designs/consistent_hashing/`).
- **Persistence** — optional: RDB snapshots (fast, lossy) or AOF (durable,
  slower).

**The interesting problems:**
1. **Cache stampede** — a hot key expires and thousands of requests hit the DB
   simultaneously. Mitigate with a lock/lease on rebuild, or probabilistic
   early expiry.
2. **Consistency with the DB** — cache-aside (read-through) is the default; the
   invalidation problem is genuinely hard.
3. **Hot keys** — one key on one shard; replicate it or split it.

**Trade-offs to name:**
- *Eviction policy:* LRU for recency, LFU for popularity; wrong choice tanks
  hit rate.
- *Persistence vs. speed:* durability costs latency; most caches accept
  lossiness because the DB is the source of truth.

---

## 8. Distributed Job Scheduler

**Ask:** "Run millions of jobs at their scheduled time, exactly once, across a
cluster."

**Core components:**
- **Job store** — persistent job definitions (next-run time, cron/interval,
  payload, status).
- **Scheduler** — polls for due jobs and enqueues them.
- **Worker pool** — pulls and executes; reports success/failure.
- **Locking** — a distributed lock (see `03-implementations/distributed-lock/`)
  ensures a job runs once even with multiple schedulers.

**The interesting problems:**
1. **Exactly-once execution** — impossible in general; at-least-once + idempotent
   jobs is the honest answer. The lock prevents duplicate *scheduling*; the job
   must be idempotent to survive duplicate *execution*.
2. **Missed jobs** — if the scheduler is down at the due time, catch up on
   restart (scan for overdue jobs).
3. **Time accuracy at scale** — you can't poll a billion jobs every second;
   bucket jobs by time (e.g. per-minute buckets) and only scan the current
   bucket.

**Trade-offs to name:**
- *Precision vs. cost:* per-second precision needs fine buckets; per-minute is
  cheaper and usually fine.
- *Centralised vs. sharded scheduler:* one scheduler is a bottleneck and SPOF;
  shard jobs by hash and run multiple schedulers with locking.

---

## 9. News Feed (Timeline)

**Ask:** "Build a personalised feed of posts from people you follow."

**Core idea:** the **fan-out** decision — push, pull, or hybrid.

- **Fan-out on write (push):** when a user posts, write the post into every
  follower's precomputed feed. Reads are O(1). Writes are expensive for
  celebrities (millions of followers).
- **Fan-out on read (pull):** compute the feed at read time by merging
  followees' posts. Cheap writes, expensive reads.
- **Hybrid (the real answer):** push for normal users, pull for celebrities
  (a threshold of followers). Merge at read time.

**The interesting problems:**
1. **Celebrity problem** — the fan-out-on-write killer; hybrid solves it.
2. **Ranking** — chronological is easy; engagement-ranked needs a model.
3. **Feed caching** — precompute and cache each user's feed; invalidate on new
   relevant posts.

**Trade-offs to name:**
- *Latency vs. cost:* push gives fast reads at high write cost; the hybrid
  balances it.
- *Freshness:* a cached feed can be slightly stale; acceptable for most feeds.

*(Twitter/`twitter_clone/` covers this in full.)*

---

## 10. Proximity Service (Yelp / "find nearby")

**Ask:** "Find the k nearest places to a location, updated as the user moves."

**Core idea:** **geospatial indexing** — a **geohash** or **quadtree** turns 2D
proximity into 1D prefix lookups.

**Architecture:**
- **Geohash / quadtree index** — encode lat/long into a string/hierarchy;
  nearby points share prefixes.
- **Read path** — compute the user's geohash cell + neighbours, query the index,
  rank by distance.
- **Write path** — businesses update rarely; the index is mostly read.
- **Caching** — cache hot cells.

**The interesting problems:**
1. **Cell granularity** — a coarse cell returns too many candidates; a fine cell
   misses neighbours at the boundary. Query the cell + its 8 neighbours, then
   filter by true distance.
2. **Dynamic movement** — for moving users (Uber drivers), you can't reindex
   constantly; keep the location in a fast store and recompute.

**Trade-offs to name:**
- *Geohash vs. quadtree:* geohash is a simple string index; quadtree adapts to
  density but is more complex.
- *Precision vs. index size:* finer cells = more cells to index but fewer
  false candidates.

*(Uber/`uber_system/` covers the moving-object variant in full.)*

---

## 11. Metrics & Monitoring (Datadog / Prometheus-like)

**Ask:** "Ingest millions of metric points per second; query and alert on them."

**Core components:**
- **Ingestion** — agents push metrics to a collector; high write throughput.
- **Time-series database** — metrics stored as (metric, timestamp, value) with
  tags. Optimised for time-range queries and aggregation.
- **Downsampling / rollups** — keep raw data for a short window (e.g. 24h),
  aggregate older data into coarser rollups (1m, 5m, 1h) to bound storage.
- **Query engine** — range queries + aggregation (avg, p99).
- **Alerting** — evaluate rules on a schedule; fire when a threshold is crossed.

**The interesting problems:**
1. **Write volume** — metrics are write-heavy; batch and compress (delta +
   timestamp compression) to survive the firehose.
2. **Cardinality explosion** — high-cardinality tags (e.g. user ID) blow up the
   index. Bound cardinality; that's the #1 operational lesson.
3. **Alert evaluation at scale** — can't evaluate every rule every second;
   shard rules and evaluate on the rollup granularity.

**Trade-offs to name:**
- *Resolution vs. retention:* raw data is expensive; rollups trade resolution
  for retention.
- *Push vs. pull:* push (StatsD) scales ingestion; pull (Prometheus) gives
  discovery and simpler targets.

---

## 12. Ad Click Aggregator

**Ask:** "Count ad clicks in near-real-time for billing and reporting."

**Core idea:** a streaming pipeline that aggregates clicks, with a separate
batch path for accurate billing (lambda-style).

**Architecture:**
- **Click ingestion** — a click hits an API/redirect, emits an event to Kafka.
- **Stream processing** — windowed aggregation (e.g. per-minute counts per ad);
  write results to a fast store for the dashboard.
- **Batch reconciliation** — a nightly job reprocesses the raw events for
  accurate billing (streaming can drop/duplicate).
- **Idempotency** — dedupe clicks (a click ID) so retries don't double-count.

**The interesting problems:**
1. **Streaming vs. billing accuracy** — streaming is fast but approximate;
   billing needs exactness, so reconcile offline. Never bill off the stream.
2. **Late-arriving events** — windowing with allowed lateness (watermarks).
3. **Duplicate clicks / fraud** — dedupe + fraud detection before counting.

**Trade-offs to name:**
- *Lambda vs. kappa architecture:* lambda (batch + stream) is simpler to get
  correct; kappa (stream only) is cleaner but harder to make exact.

---

## Cross-cutting patterns cheat-sheet

These recur across almost every HLD question — have them ready:

| Problem | Go-to solution |
|---|---|
| Read-heavy, hot data | Cache (Redis) + CDN |
| Write-heavy ingestion | Queue (Kafka) + async workers |
| Horizontal scale of stateful data | Sharding + consistent hashing |
| "Find nearby" | Geohash / quadtree |
| "Count/search over text" | Inverted index |
| "Top-k of a prefix" | Trie with precomputed top-k |
| Exactly-once work | Distributed lock + idempotent jobs |
| Feed/timeline | Fan-out on write + hybrid for celebrities |
| Prevent duplicate work | Bloom filter / content hashing |
| Real-time media | WebRTC + SFU |
| Time-series at scale | Downsampling + rollups |
| Approximate counting | HyperLogLog / count-min sketch |

## How to answer any HLD question (the 6-step spine)

1. **Clarify scope** — functional + non-functional; ask what's out of scope.
2. **Estimate scale** — QPS, storage, bandwidth. Numbers drive every choice.
3. **API design** — the few endpoints that matter.
4. **Data model** — entities + storage choices (SQL vs. NoSQL, why).
5. **High-level architecture** — draw boxes: client → LB → services → data.
6. **Deep-dive + scale** — pick the hard part, solve it, then discuss
   bottlenecks, failure modes, and trade-offs.

The follow-up ("how would you scale this?") is where interviews are won — see
[`../04-interview-prep/frameworks.md`](../04-interview-prep/frameworks.md) for
the full method.

---

*For the deep dives, see the full folders: [Twitter](twitter_clone/README.md),
[YouTube](youtube/README.md), [Netflix](netflix_streaming/README.md), [Uber](uber_system/README.md),
[URL Shortener](url_shortener/README.md), [LLM Serving](llm_serving_platform/README.md),
[Chat System](chat_system/README.md), [Payment Gateway](payment_gateway/README.md).*
