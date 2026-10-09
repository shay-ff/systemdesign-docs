# FAQs — Practical Answers to Common System Design Questions

## Database and Storage

**Q: How do I choose between SQL and NoSQL databases?**

A: Start with your data model and requirements:

**Choose SQL when you need**:
- Complex relationships and joins between entities
- ACID transactions and strong consistency
- Mature ecosystem and standardized query language
- Well-understood data structure that fits relational model
- Examples: User profiles with relationships, financial transactions, inventory management

**Choose NoSQL when you need**:
- Horizontal scaling across multiple servers
- Flexible schema that evolves frequently
- High write throughput with eventual consistency
- Simple key-value or document-based access patterns
- Examples: User sessions, product catalogs, real-time analytics, IoT data

**Hybrid approach**: Many systems use both - SQL for transactional data, NoSQL for analytics or caching.

**Q: When should I consider database sharding?**

A: Consider sharding when:
- Single database can't handle write load (>10K writes/second typically)
- Data size exceeds single server capacity (>1TB typically)
- You need geographic distribution for latency
- Read replicas aren't sufficient for your read load

**Sharding strategies**:
- **Range-based**: Shard by ID ranges (1-1M, 1M-2M, etc.)
- **Hash-based**: Use hash function on shard key
- **Directory-based**: Lookup service maps keys to shards
- **Geographic**: Shard by user location

**Q: How do I handle database migrations in a distributed system?**

A: Use these strategies:
1. **Backward-compatible changes first**: Add new columns, don't remove old ones
2. **Dual-write pattern**: Write to both old and new schema during transition
3. **Feature flags**: Control which code paths use new vs old schema
4. **Gradual rollout**: Migrate data in batches, not all at once
5. **Rollback plan**: Always have a way to revert changes

## Caching Strategies

**Q: When should I add a cache layer?**

A: Add caching when:
- Read-to-write ratio is high (>3:1 typically)
- Latency requirements are tight (<100ms)
- Database queries are expensive or slow
- You have hot data that's accessed frequently

**Cache hierarchy**:
1. **Browser cache**: Static assets (CSS, JS, images)
2. **CDN**: Global static content distribution
3. **Application cache**: In-memory cache (Redis, Memcached)
4. **Database query cache**: Cache query results

**Q: How do I avoid cache stampede and thundering herd problems?**

A: Use these techniques:
- **Request coalescing**: Multiple requests for same key wait for single fetch
- **Mutex/lock on cache miss**: Only one thread fetches, others wait
- **Probabilistic early expiration**: Refresh cache before TTL expires
- **Jittered TTLs**: Add randomness to expiration times
- **Stale-while-revalidate**: Serve stale data while refreshing asynchronously

**Example implementation**:
```python
def get_with_lock(key):
    value = cache.get(key)
    if value is None:
        with lock(f"refresh:{key}"):
            value = cache.get(key)  # Double-check
            if value is None:
                value = fetch_from_db(key)
                cache.set(key, value, ttl=300)
    return value
```

**Q: What's the best cache invalidation strategy?**

A: It depends on your consistency requirements:

**TTL-based**: Simple but may serve stale data
- Good for: Analytics data, recommendations, non-critical content

**Event-driven**: Invalidate on data changes
- Good for: User profiles, product information, critical business data

**Write-through/Write-behind**: Update cache on writes
- Good for: Frequently updated data with strong consistency needs

**Cache-aside with versioning**: Include version in cache key
- Good for: Complex data with multiple dependencies

## Scaling and Performance

**Q: How do I estimate capacity for system design interviews?**

A: Follow this systematic approach:

**1. Clarify the scale**:
- Daily Active Users (DAU)
- Requests per user per day
- Data size per request
- Storage requirements

**2. Calculate traffic**:
```
Example: Social media platform
- 100M DAU
- 10 posts viewed per user per day
- 1B requests per day
- Peak traffic = 3x average = 36K RPS
```

**3. Estimate storage**:
```
- 1M new posts per day
- 1KB per post metadata
- 1MB per image (50% of posts have images)
- Daily storage = 1M × 1KB + 500K × 1MB = 501GB/day
- Annual storage = 501GB × 365 = ~180TB/year
```

**4. Calculate bandwidth**:
```
- 36K RPS × 10KB average response = 360MB/s
- Add 50% safety margin = 540MB/s
```

**Q: When should I move from monolith to microservices?**

A: Consider microservices when:
- Team size >8-10 people (Conway's Law)
- Different components have different scaling requirements
- You need independent deployment cycles
- Different parts use different technologies
- Clear service boundaries exist

**Don't use microservices if**:
- Team is small (<5 people)
- System is simple and well-understood
- Network latency is critical
- You lack operational maturity (monitoring, deployment, etc.)

**Migration strategy**:
1. Start with modular monolith
2. Extract services at natural boundaries
3. Use strangler fig pattern for gradual migration
4. Implement proper monitoring and observability first

## System Architecture

**Q: What should I include in a high-level system design diagram?**

A: Include these essential components:

**External facing**:
- Client applications (web, mobile, APIs)
- CDN for static content
- DNS and load balancers

**Application layer**:
- API Gateway for routing and authentication
- Stateless application servers
- Background job processors

**Data layer**:
- Primary databases (read/write)
- Read replicas for scaling reads
- Caches (Redis, Memcached)
- Message queues for asynchronous processing

**Infrastructure**:
- Monitoring and logging systems
- External services and APIs
- Security components (firewalls, rate limiters)

**Label everything**:
- Data flow directions
- Protocols used (HTTP, gRPC, etc.)
- Approximate latency and throughput requirements

**Q: How do I handle failures and ensure high availability?**

A: Implement multiple layers of resilience:

**Application level**:
- Circuit breakers to prevent cascading failures
- Retry logic with exponential backoff
- Timeouts for all external calls
- Graceful degradation when services are unavailable

**Infrastructure level**:
- Multiple availability zones
- Load balancers with health checks
- Auto-scaling groups
- Database replication and failover

**Operational level**:
- Comprehensive monitoring and alerting
- Runbooks for common failure scenarios
- Regular disaster recovery testing
- Chaos engineering to test resilience

## Performance Optimization

**Q: How do I identify and fix performance bottlenecks?**

A: Follow this systematic approach:

**1. Measure first**:
- Application Performance Monitoring (APM)
- Database query analysis
- Network latency monitoring
- Resource utilization (CPU, memory, disk, network)

**2. Common bottlenecks and solutions**:

**Database bottlenecks**:
- Add indexes for slow queries
- Implement read replicas
- Consider database sharding
- Optimize query patterns

**Network bottlenecks**:
- Implement caching layers
- Use CDN for static content
- Compress responses
- Reduce payload sizes

**Application bottlenecks**:
- Profile code to find hot spots
- Implement asynchronous processing
- Use connection pooling
- Optimize algorithms and data structures

**3. Load testing**:
- Test with realistic traffic patterns
- Identify breaking points
- Validate fixes under load

**Q: What are the most important metrics to monitor?**

A: Focus on these key metrics:

**Golden Signals** (Google SRE):
- **Latency**: How long requests take
- **Traffic**: How much demand on your system
- **Errors**: Rate of failed requests
- **Saturation**: How "full" your service is

**Business metrics**:
- User engagement and conversion rates
- Revenue impact of performance issues
- Customer satisfaction scores

**Infrastructure metrics**:
- CPU, memory, disk, and network utilization
- Database connection pool usage
- Cache hit rates
- Queue depths and processing times

**Set up alerts for**:
- Error rates >1%
- Latency P95 >acceptable threshold
- Resource utilization >80%
- Any metric trending in wrong direction

## Security and Reliability

**Q: How do I secure a distributed system?**

A: Implement security at multiple layers:

**Authentication and Authorization**:
- Use OAuth 2.0 or similar standard protocols
- Implement JWT tokens with proper expiration
- Use API keys for service-to-service communication
- Implement role-based access control (RBAC)

**Network security**:
- Use HTTPS/TLS for all communications
- Implement VPCs and security groups
- Use service mesh for internal communication
- Regular security audits and penetration testing

**Data protection**:
- Encrypt data at rest and in transit
- Implement proper key management
- Use database-level encryption for sensitive data
- Regular backup and recovery testing

**Rate limiting and DDoS protection**:
- Implement rate limiting at multiple levels
- Use CDN with DDoS protection
- Implement CAPTCHA for suspicious traffic
- Monitor for unusual traffic patterns

**Q: How do I ensure data consistency across microservices?**

A: Use these patterns based on your consistency requirements:

**Strong consistency** (when needed):
- Two-phase commit (2PC) for critical transactions
- Saga pattern for long-running transactions
- Distributed locks for critical sections

**Eventual consistency** (preferred):
- Event sourcing with event replay
- CQRS (Command Query Responsibility Segregation)
- Compensating transactions for rollbacks
- Idempotent operations to handle duplicates

**Best practices**:
- Design for idempotency from the start
- Use correlation IDs to track requests across services
- Implement proper monitoring and alerting
- Have rollback procedures for failed transactions

Remember: Perfect consistency is expensive and often unnecessary. Choose the right consistency model for each use case.
