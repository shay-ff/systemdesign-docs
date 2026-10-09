# System Design Cheatsheet — Formulas, Rules & Quick References

This is your go-to reference for calculations, rules-of-thumb, and decision trees during system design interviews and real-world architecture decisions.

## Performance Fundamentals

**Key Principle**: Design for P95 or P99 latency, not averages. Tail latency matters more than average latency.

**Latency vs Throughput Trade-off**:
- Batching increases throughput but may increase latency
- Caching reduces latency but may impact consistency
- Horizontal scaling improves throughput, vertical scaling may reduce latency

## Back-of-the-Envelope Calculations

### Traffic Estimation
```
Daily Active Users (DAU) → Requests Per Second (RPS)
RPS = (DAU × requests_per_user_per_day) / 86,400
Peak RPS = Average RPS × 3 (typical peak factor)

Example:
100M DAU × 10 requests/day = 1B requests/day
Average RPS = 1B / 86,400 ≈ 12K RPS
Peak RPS = 12K × 3 = 36K RPS
```

### Bandwidth Calculation
```
Bandwidth = RPS × Average_Payload_Size
Total Bandwidth = Read_Bandwidth + Write_Bandwidth

Example:
36K RPS × 5KB average response = 180 MB/s
Add 50% safety margin = 270 MB/s
```

### Storage Estimation
```
Daily Storage Growth = Daily_Writes × Average_Record_Size
Annual Storage = Daily_Storage × 365
With Replication = Annual_Storage × Replication_Factor

Example:
1M posts/day × 2KB/post = 2GB/day
Annual = 2GB × 365 = 730GB/year
With 3x replication = 2.2TB/year
```

### Concurrency (Little's Law)
```
Concurrent Users = Throughput × Average_Session_Duration
Required Connections = Concurrent_Users / Connection_Pool_Size

Example:
1000 RPS × 30 seconds average = 30,000 concurrent users
Database connections needed = 30,000 / 100 = 300 connections
```

## Capacity Planning Rules of Thumb

### Server Capacity
- **Web servers**: 1000-5000 concurrent connections per server
- **Database connections**: 100-500 connections per database server
- **Memory**: Keep 20-30% free for OS and buffers
- **CPU**: Target 70% utilization for auto-scaling triggers

### Network Limits
- **Single server NIC**: 1-10 Gbps typical
- **Cross-AZ latency**: 1-5ms
- **Cross-region latency**: 50-200ms
- **CDN edge latency**: 10-50ms

### Storage Performance
- **SSD IOPS**: 3,000-20,000 IOPS per drive
- **HDD IOPS**: 100-200 IOPS per drive
- **Network storage**: 1,000-10,000 IOPS typical
- **Database**: 10,000-100,000 IOPS for high-performance setups

## Caching Strategy Decision Tree

### Cache Hierarchy (Outside → Inside)
```
Client Browser → CDN → Load Balancer → Application Cache → Database Query Cache
```

### When to Cache
- **Read:Write ratio > 3:1**: Good candidate for caching
- **Data access pattern**: 80/20 rule (80% of requests for 20% of data)
- **Latency requirements**: <100ms response time needs
- **Expensive operations**: Complex queries, API calls, computations

### Cache Patterns
```
Cache-Aside (Lazy Loading):
- App manages cache explicitly
- Good for: Read-heavy workloads
- Cache miss penalty: 2x latency

Write-Through:
- Write to cache and DB simultaneously  
- Good for: Strong consistency needs
- Write penalty: Higher write latency

Write-Behind (Write-Back):
- Write to cache, async to DB
- Good for: Write-heavy workloads
- Risk: Potential data loss
```

### Cache Sizing
```
Cache Hit Ratio = Cache Hits / Total Requests
Target: >90% hit ratio for effective caching

Cache Size ≈ Working Set Size × 1.2 (20% buffer)
TTL = Data_Freshness_Requirement / 2
```

## Database Scaling Decision Matrix

### SQL vs NoSQL Decision Tree
```
Need ACID transactions? → SQL
Need complex joins? → SQL
Need flexible schema? → NoSQL  
Need horizontal scaling? → NoSQL
Need strong consistency? → SQL
Need eventual consistency OK? → NoSQL
```

### Scaling Strategies
```
Read Scaling:
- Read replicas: 5-10x read capacity
- Read-only queries route to replicas
- Eventual consistency acceptable

Write Scaling:
- Vertical scaling: 2-10x improvement
- Sharding: Nearly unlimited scaling
- Complexity increases significantly

Storage Scaling:
- Vertical: Limited by hardware
- Horizontal (sharding): Unlimited
- Partitioning strategies: Range, Hash, Directory
```

### Sharding Decision Points
```
Consider sharding when:
- Single DB write load > 10K writes/sec
- Data size > 1TB per server
- Query performance degrades despite optimization
- Need geographic distribution

Sharding strategies:
- Range-based: Easy range queries, risk of hotspots
- Hash-based: Even distribution, no range queries
- Directory-based: Flexible, adds lookup overhead
```

## Rate Limiting Algorithms

### Token Bucket
```
Allows bursts up to bucket size
Refill rate = sustained rate limit
Bucket size = maximum burst size

Good for: APIs that need burst capability
Example: 1000 requests/hour, burst of 100
```

### Leaky Bucket
```
Smooths traffic to fixed rate
No bursts allowed
Queue size = maximum delay tolerance

Good for: Protecting downstream services
Example: Exactly 10 requests/second, no bursts
```

### Sliding Window
```
More accurate than fixed windows
Higher memory usage
Prevents edge case exploitation

Good for: Precise rate limiting
Example: Exactly 1000 requests per hour
```

## Monitoring and Observability

### Golden Signals (Google SRE)
```
Latency: How long requests take
- Monitor: P50, P95, P99 response times
- Alert: P95 > acceptable threshold

Traffic: Demand on your system  
- Monitor: RPS, concurrent users
- Alert: Traffic spikes or drops

Errors: Rate of failed requests
- Monitor: Error rate, error types
- Alert: Error rate > 1%

Saturation: How "full" your service is
- Monitor: CPU, memory, disk, network utilization
- Alert: Utilization > 80%
```

### USE Method (Brendan Gregg)
```
Utilization: % time resource is busy
Saturation: Degree of queuing/waiting
Errors: Count of error events

Apply to: CPU, Memory, Network, Storage
```

### RED Method (Tom Wilkie)
```
Rate: Requests per second
Errors: Number of failed requests
Duration: Time each request takes

Focus on: User-facing services
```

## System Design Interview Framework

### 1. Requirements Clarification (5-10 min)
```
Functional Requirements:
- What features need to be built?
- What are the core user journeys?
- Any special constraints or business rules?

Non-Functional Requirements:
- Scale: How many users? (DAU, MAU)
- Performance: Latency and throughput requirements
- Availability: Uptime requirements (99.9%?)
- Consistency: Strong vs eventual consistency needs
```

### 2. Capacity Estimation (5-10 min)
```
Traffic Estimation:
- DAU → RPS (average and peak)
- Read vs write ratio
- Seasonal patterns

Storage Estimation:
- Data per user/transaction
- Growth rate
- Retention period

Bandwidth Estimation:
- Request/response sizes
- Media content (images, videos)
- Geographic distribution
```

### 3. High-Level Design (15-20 min)
```
Core Components:
□ Load Balancer
□ API Gateway  
□ Application Servers
□ Databases (primary/replica)
□ Caches (Redis/Memcached)
□ Message Queues
□ CDN (if needed)
□ External Services

Data Flow:
- Request path (read operations)
- Write path (data modifications)
- Background processing
```

### 4. Deep Dive (10-15 min)
```
Database Design:
- Schema design
- Indexing strategy
- Sharding approach (if needed)

API Design:
- Key endpoints
- Request/response formats
- Authentication/authorization

Caching Strategy:
- What to cache
- Cache invalidation
- Cache hierarchy
```

### 5. Scale and Optimize (5-10 min)
```
Bottleneck Analysis:
- Database (usually first bottleneck)
- Network bandwidth
- CPU-intensive operations

Scaling Solutions:
- Database: Sharding, read replicas
- Application: Horizontal scaling, microservices
- Caching: Multi-layer caching
- Async processing: Message queues
```

## Technology Selection Quick Guide

### Database Selection
```
Relational (PostgreSQL, MySQL):
✓ Complex relationships and joins
✓ ACID transactions required
✓ Mature ecosystem and tools
✓ Strong consistency needs

Document (MongoDB, CouchDB):
✓ Flexible, evolving schema
✓ JSON-like data structures
✓ Horizontal scaling needs
✓ Rapid development cycles

Key-Value (Redis, DynamoDB):
✓ Simple get/put operations
✓ High performance requirements
✓ Session storage, caching
✓ Real-time applications

Column-Family (Cassandra, HBase):
✓ Time-series data
✓ Write-heavy workloads
✓ Massive scale requirements
✓ Analytics and reporting

Graph (Neo4j, Amazon Neptune):
✓ Complex relationships
✓ Social networks, recommendations
✓ Fraud detection
✓ Knowledge graphs
```

### Message Queue Selection
```
RabbitMQ:
✓ Complex routing needs
✓ Strong consistency requirements
✓ Traditional enterprise environments
✓ AMQP protocol support

Apache Kafka:
✓ High throughput requirements
✓ Event streaming and replay
✓ Real-time analytics
✓ Microservices communication

Amazon SQS:
✓ Managed service preference
✓ Simple pub/sub needs
✓ AWS ecosystem integration
✓ Variable workloads

Redis Pub/Sub:
✓ Low latency requirements
✓ Simple messaging patterns
✓ Already using Redis for caching
✓ Real-time notifications
```

### Caching Selection
```
Redis:
✓ Complex data structures needed
✓ Pub/sub messaging
✓ Persistence requirements
✓ Clustering support

Memcached:
✓ Simple key-value caching
✓ Multi-threaded performance
✓ Memory efficiency priority
✓ Simple use cases

CDN (CloudFlare, AWS CloudFront):
✓ Static content delivery
✓ Global user base
✓ Reduce origin server load
✓ DDoS protection needs

Application-level (Caffeine, Guava):
✓ Low latency requirements
✓ Simple deployment
✓ No network overhead
✓ Small to medium datasets
```

## Common Pitfalls and Solutions

### Avoid These Mistakes
```
❌ Premature optimization
✅ Start simple, measure, then optimize

❌ Ignoring data consistency requirements
✅ Understand business consistency needs

❌ Over-engineering for scale
✅ Design for current scale + 10x

❌ Single points of failure
✅ Eliminate SPOFs with redundancy

❌ Ignoring operational complexity
✅ Consider monitoring, deployment, debugging

❌ Assuming perfect network
✅ Design for network partitions and failures
```

### Quick Wins for Performance
```
1. Add database indexes for slow queries
2. Implement application-level caching
3. Use CDN for static assets
4. Enable gzip compression
5. Implement connection pooling
6. Add read replicas for read-heavy workloads
7. Use async processing for non-critical operations
8. Implement proper monitoring and alerting
```

Remember: **Start simple, measure everything, optimize based on real bottlenecks, not assumptions.**