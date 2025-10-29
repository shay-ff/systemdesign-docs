# Caching Patterns

## Cache-Aside (Lazy Loading)

```mermaid
sequenceDiagram
    participant App as Application
    participant Cache as Cache
    participant DB as Database
    
    App->>Cache: Get(key)
    Cache-->>App: Cache Miss
    App->>DB: Query(key)
    DB-->>App: Data
    App->>Cache: Set(key, data)
    App->>App: Return data
    
    Note over App,Cache: Subsequent requests hit cache
    App->>Cache: Get(key)
    Cache-->>App: Cache Hit (data)
```

## Write-Through

```mermaid
sequenceDiagram
    participant App as Application
    participant Cache as Cache
    participant DB as Database
    
    App->>Cache: Write(key, data)
    Cache->>DB: Write(key, data)
    DB-->>Cache: Success
    Cache-->>App: Success
    
    Note over Cache,DB: Data always consistent
```

## Write-Behind (Write-Back)

```mermaid
sequenceDiagram
    participant App as Application
    participant Cache as Cache
    participant DB as Database
    
    App->>Cache: Write(key, data)
    Cache-->>App: Success (immediate)
    
    Note over Cache: Async write to DB
    Cache->>DB: Write(key, data) [Async]
    DB-->>Cache: Success
```

## Cache Architecture Patterns

### Single Cache Layer

```mermaid
graph LR
    App[Application] --> Cache[Redis/Memcached]
    Cache --> DB[(Database)]
    
    style App fill:#2563eb,color:#fff
    style Cache fill:#dc2626,color:#fff
    style DB fill:#059669,color:#fff
```

### Multi-Level Cache

```mermaid
graph LR
    App[Application] --> L1[L1 Cache<br/>In-Memory]
    L1 --> L2[L2 Cache<br/>Redis]
    L2 --> DB[(Database)]
    
    style App fill:#2563eb,color:#fff
    style L1 fill:#f59e0b,color:#fff
    style L2 fill:#dc2626,color:#fff
    style DB fill:#059669,color:#fff
```

### Distributed Cache

```mermaid
graph TB
    LB[Load Balancer] --> App1[App Instance 1]
    LB --> App2[App Instance 2]
    LB --> App3[App Instance 3]
    
    App1 --> Cache[Distributed Cache<br/>Redis Cluster]
    App2 --> Cache
    App3 --> Cache
    
    Cache --> DB[(Database)]
    
    style LB fill:#7c3aed,color:#fff
    style App1 fill:#2563eb,color:#fff
    style App2 fill:#2563eb,color:#fff
    style App3 fill:#2563eb,color:#fff
    style Cache fill:#dc2626,color:#fff
    style DB fill:#059669,color:#fff
```

## Cache Invalidation Strategies

### TTL (Time To Live)

```mermaid
graph LR
    A[Set with TTL] --> B[Cache Entry]
    B --> C{TTL Expired?}
    C -->|Yes| D[Remove Entry]
    C -->|No| E[Serve from Cache]
    
    style A fill:#2563eb,color:#fff
    style B fill:#dc2626,color:#fff
    style D fill:#ef4444,color:#fff
    style E fill:#10b981,color:#fff
```

### Event-Based Invalidation

```mermaid
graph TB
    App[Application] --> Cache[Cache]
    App --> DB[(Database)]
    
    DB --> Event[Database Event]
    Event --> Invalidator[Cache Invalidator]
    Invalidator --> Cache
    
    style App fill:#2563eb,color:#fff
    style Cache fill:#dc2626,color:#fff
    style DB fill:#059669,color:#fff
    style Event fill:#ea580c,color:#fff
    style Invalidator fill:#7c3aed,color:#fff
```

## Cache Consistency Models

### Strong Consistency
- Write-through pattern
- Synchronous updates
- Higher latency, guaranteed consistency

### Eventual Consistency
- Write-behind pattern
- Asynchronous updates
- Lower latency, temporary inconsistency

### Weak Consistency
- Cache-aside pattern
- Manual invalidation
- Best performance, manual consistency management

## Common Cache Problems

### Cache Stampede
**Problem**: Multiple requests for same expired key hit database simultaneously

**Solution**: Use locks or probabilistic early expiration

### Hot Keys
**Problem**: Few keys get most of the traffic

**Solution**: Distribute hot keys across multiple cache instances

### Cache Penetration
**Problem**: Requests for non-existent data bypass cache

**Solution**: Cache null values with short TTL