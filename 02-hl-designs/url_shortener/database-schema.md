# URL Shortener - Database Schema

## Database Architecture Overview

The URL shortener uses a multi-database approach optimized for different access patterns:

- **PostgreSQL**: URL mappings, user data, and transactional operations
- **Redis**: High-performance caching for URL lookups and session storage
- **Cassandra**: Time-series analytics data and click tracking
- **Object Storage (S3)**: QR codes and static assets

## PostgreSQL Schema

### URLs Table
```sql
CREATE TABLE urls (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(50) UNIQUE NOT NULL,
    long_url TEXT NOT NULL,
    user_id BIGINT REFERENCES users(id),
    custom_alias VARCHAR(100) UNIQUE,
    title VARCHAR(500),
    description TEXT,
    tags TEXT[], -- Array of tags
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    click_count BIGINT DEFAULT 0,
    unique_click_count BIGINT DEFAULT 0,
    last_clicked_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'active', -- active, expired, deleted, suspended
    is_public BOOLEAN DEFAULT TRUE,
    password_hash VARCHAR(255), -- For password-protected URLs
    safe_browsing_status VARCHAR(20) DEFAULT 'unknown', -- safe, unsafe, unknown
    safe_browsing_checked_at TIMESTAMP WITH TIME ZONE,
    
    -- Constraints
    CONSTRAINT valid_status CHECK (status IN ('active', 'expired', 'deleted', 'suspended')),
    CONSTRAINT valid_safe_browsing CHECK (safe_browsing_status IN ('safe', 'unsafe', 'unknown')),
    CONSTRAINT short_code_format CHECK (short_code ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT custom_alias_format CHECK (custom_alias IS NULL OR custom_alias ~ '^[a-zA-Z0-9_-]+$')
);

-- Indexes
CREATE UNIQUE INDEX idx_urls_short_code ON urls(short_code);
CREATE UNIQUE INDEX idx_urls_custom_alias ON urls(custom_alias) WHERE custom_alias IS NOT NULL;
CREATE INDEX idx_urls_user_id ON urls(user_id);
CREATE INDEX idx_urls_created_at ON urls(created_at DESC);
CREATE INDEX idx_urls_status ON urls(status);
CREATE INDEX idx_urls_expires_at ON urls(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_urls_tags ON urls USING GIN(tags);
CREATE INDEX idx_urls_user_status ON urls(user_id, status) WHERE user_id IS NOT NULL;

-- Composite index for user URL listing
CREATE INDEX idx_urls_user_created ON urls(user_id, created_at DESC) WHERE user_id IS NOT NULL;
```

### Users Table
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    plan VARCHAR(20) DEFAULT 'free', -- free, premium, enterprise
    api_key_hash VARCHAR(255) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE,
    email_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Usage tracking
    urls_created_count BIGINT DEFAULT 0,
    total_clicks_count BIGINT DEFAULT 0,
    monthly_url_quota INTEGER DEFAULT 100,
    monthly_urls_created INTEGER DEFAULT 0,
    quota_reset_date DATE DEFAULT CURRENT_DATE + INTERVAL '1 month',
    
    -- Constraints
    CONSTRAINT valid_plan CHECK (plan IN ('free', 'premium', 'enterprise')),
    CONSTRAINT valid_email CHECK (email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- Indexes
CREATE UNIQUE INDEX idx_users_username ON users(username);
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE UNIQUE INDEX idx_users_api_key ON users(api_key_hash) WHERE api_key_hash IS NOT NULL;
CREATE INDEX idx_users_plan ON users(plan);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_quota_reset ON users(quota_reset_date);
```

### API Keys Table
```sql
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(20) NOT NULL, -- First few characters for identification
    name VARCHAR(100) NOT NULL,
    permissions TEXT[] DEFAULT ARRAY['read'], -- read, write, admin
    rate_limit_per_hour INTEGER DEFAULT 1000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Usage tracking
    requests_count BIGINT DEFAULT 0,
    last_request_ip INET
);

-- Indexes
CREATE UNIQUE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX idx_api_keys_active ON api_keys(is_active, expires_at);
```

### Click Events Table (Hot Data)
```sql
-- This table stores recent click events for real-time analytics
-- Older data is moved to Cassandra for long-term storage
CREATE TABLE click_events (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(50) NOT NULL,
    url_id BIGINT REFERENCES urls(id),
    clicked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ip_address INET,
    user_agent TEXT,
    referrer TEXT,
    country_code CHAR(2),
    region VARCHAR(100),
    city VARCHAR(100),
    device_type VARCHAR(20), -- desktop, mobile, tablet, bot
    browser VARCHAR(50),
    os VARCHAR(50),
    is_unique_click BOOLEAN DEFAULT FALSE,
    session_id VARCHAR(100)
);

-- Indexes
CREATE INDEX idx_click_events_short_code ON click_events(short_code);
CREATE INDEX idx_click_events_url_id ON click_events(url_id);
CREATE INDEX idx_click_events_clicked_at ON click_events(clicked_at DESC);
CREATE INDEX idx_click_events_country ON click_events(country_code);
CREATE INDEX idx_click_events_device ON click_events(device_type);

-- Partition by month for better performance
CREATE TABLE click_events_y2024m01 PARTITION OF click_events
FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE click_events_y2024m02 PARTITION OF click_events
FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
-- ... continue for each month
```

### Domains Table (For Custom Domains)
```sql
CREATE TABLE domains (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    domain_name VARCHAR(255) UNIQUE NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    verification_token VARCHAR(100),
    ssl_certificate_status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    verified_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT valid_domain CHECK (domain_name ~ '^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')
);

-- Indexes
CREATE UNIQUE INDEX idx_domains_name ON domains(domain_name);
CREATE INDEX idx_domains_user_id ON domains(user_id);
CREATE INDEX idx_domains_verified ON domains(is_verified);
```

## Redis Schema

### URL Cache
```
Key Pattern: url:{short_code}
Type: Hash
Fields: 
  - long_url: Target URL
  - title: Page title
  - click_count: Current click count
  - expires_at: Expiration timestamp
  - status: URL status
TTL: 1 hour for active URLs, 5 minutes for expired/deleted

Example:
HSET url:abc123 long_url "https://example.com/page"
HSET url:abc123 title "Example Page"
HSET url:abc123 click_count "1250"
HSET url:abc123 status "active"
EXPIRE url:abc123 3600
```

### User Session Cache
```
Key Pattern: session:{session_id}
Type: Hash
Fields: User session data
TTL: 24 hours

Example:
HSET session:sess_123 user_id "456"
HSET session:sess_123 username "johndoe"
HSET session:sess_123 plan "premium"
EXPIRE session:sess_123 86400
```

### Rate Limiting
```
Key Pattern: rate_limit:{api_key}:{hour}
Type: String (counter)
TTL: 1 hour

Example:
INCR rate_limit:key_abc123:2024011510
EXPIRE rate_limit:key_abc123:2024011510 3600
```

### Popular URLs Cache
```
Key Pattern: popular_urls:{period}
Type: Sorted Set
Value: Short codes with click count scores
TTL: 15 minutes

Example:
ZADD popular_urls:1h 1250 "abc123"
ZADD popular_urls:1h 890 "def456"
EXPIRE popular_urls:1h 900
```

### Analytics Cache
```
Key Pattern: analytics:{short_code}:{period}:{granularity}
Type: Hash
Fields: Aggregated analytics data
TTL: 5 minutes

Example:
HSET analytics:abc123:7d:day "2024-01-15" "180"
HSET analytics:abc123:7d:day "2024-01-14" "220"
EXPIRE analytics:abc123:7d:day 300
```

## Cassandra Schema (Analytics)

### Click Events (Long-term Storage)
```cql
CREATE TABLE click_events (
    short_code TEXT,
    clicked_date DATE,
    clicked_hour INT,
    event_id TIMEUUID,
    ip_address TEXT,
    country_code TEXT,
    region TEXT,
    city TEXT,
    device_type TEXT,
    browser TEXT,
    os TEXT,
    referrer TEXT,
    user_agent TEXT,
    is_unique_click BOOLEAN,
    PRIMARY KEY ((short_code, clicked_date), clicked_hour, event_id)
) WITH CLUSTERING ORDER BY (clicked_hour ASC, event_id ASC);
```

### Daily URL Statistics
```cql
CREATE TABLE daily_url_stats (
    short_code TEXT,
    stat_date DATE,
    total_clicks COUNTER,
    unique_clicks COUNTER,
    PRIMARY KEY (short_code, stat_date)
) WITH CLUSTERING ORDER BY (stat_date DESC);
```

### Hourly URL Statistics
```cql
CREATE TABLE hourly_url_stats (
    short_code TEXT,
    stat_date DATE,
    stat_hour INT,
    clicks COUNTER,
    unique_clicks COUNTER,
    PRIMARY KEY ((short_code, stat_date), stat_hour)
) WITH CLUSTERING ORDER BY (stat_hour ASC);
```

### Geographic Statistics
```cql
CREATE TABLE geographic_stats (
    short_code TEXT,
    stat_date DATE,
    country_code TEXT,
    region TEXT,
    city TEXT,
    clicks COUNTER,
    PRIMARY KEY ((short_code, stat_date), country_code, region, city)
);
```

### Referrer Statistics
```cql
CREATE TABLE referrer_stats (
    short_code TEXT,
    stat_date DATE,
    referrer_domain TEXT,
    clicks COUNTER,
    PRIMARY KEY ((short_code, stat_date), referrer_domain)
);
```

### Device Statistics
```cql
CREATE TABLE device_stats (
    short_code TEXT,
    stat_date DATE,
    device_type TEXT,
    browser TEXT,
    os TEXT,
    clicks COUNTER,
    PRIMARY KEY ((short_code, stat_date), device_type, browser, os)
);
```

## Sharding Strategy

### PostgreSQL Sharding

#### URL Sharding
```python
def get_url_shard(short_code):
    """Shard URLs based on short_code hash"""
    shard_count = 16
    shard_id = hash(short_code) % shard_count
    return f"urls_shard_{shard_id}"

# Shard distribution
Shard 0: hash(short_code) % 16 = 0
Shard 1: hash(short_code) % 16 = 1
...
Shard 15: hash(short_code) % 16 = 15
```

#### User Sharding
```python
def get_user_shard(user_id):
    """Shard users based on user_id"""
    shard_count = 8
    shard_id = user_id % shard_count
    return f"users_shard_{shard_id}"
```

### Redis Sharding
```
Redis Cluster Configuration:
- 6 master nodes
- 6 replica nodes (1 replica per master)
- Hash slot distribution: 16384 slots total
- Consistent hashing for key distribution
```

### Cassandra Sharding
```
Partition Strategy:
- NetworkTopologyStrategy
- Replication factor: 3
- Data centers: 2 (primary and backup)
- Partition key: (short_code, date) for time-based queries
```

## Data Consistency Patterns

### Strong Consistency
- URL creation and updates
- User account operations
- API key management
- Payment and billing operations

### Eventual Consistency
- Click count updates
- Analytics aggregations
- Cache invalidation
- Search index updates

## Performance Optimizations

### Database Optimizations
```sql
-- Materialized view for popular URLs
CREATE MATERIALIZED VIEW popular_urls AS
SELECT short_code, click_count, created_at
FROM urls
WHERE status = 'active' AND click_count > 100
ORDER BY click_count DESC;

-- Refresh materialized view periodically
REFRESH MATERIALIZED VIEW CONCURRENTLY popular_urls;

-- Partial indexes for better performance
CREATE INDEX idx_urls_active_recent ON urls(created_at DESC)
WHERE status = 'active' AND created_at > NOW() - INTERVAL '30 days';

-- Function-based index for case-insensitive searches
CREATE INDEX idx_urls_custom_alias_lower ON urls(LOWER(custom_alias))
WHERE custom_alias IS NOT NULL;
```

### Connection Pooling
```python
# PostgreSQL connection pool configuration
DATABASE_POOL_CONFIG = {
    'min_connections': 10,
    'max_connections': 100,
    'connection_timeout': 30,
    'idle_timeout': 300,
    'max_lifetime': 3600
}

# Redis connection pool
REDIS_POOL_CONFIG = {
    'max_connections': 50,
    'retry_on_timeout': True,
    'health_check_interval': 30
}
```

## Backup and Recovery

### PostgreSQL Backup Strategy
```bash
# Daily full backup
pg_dump -h localhost -U postgres -d urlshortener > backup_$(date +%Y%m%d).sql

# Continuous WAL archiving
archive_command = 'cp %p /backup/wal_archive/%f'

# Point-in-time recovery setup
restore_command = 'cp /backup/wal_archive/%f %p'
```

### Redis Backup Strategy
```
# RDB snapshots
save 900 1      # Save if at least 1 key changed in 900 seconds
save 300 10     # Save if at least 10 keys changed in 300 seconds
save 60 10000   # Save if at least 10000 keys changed in 60 seconds

# AOF for durability
appendonly yes
appendfsync everysec
```

### Cassandra Backup Strategy
```bash
# Daily snapshots
nodetool snapshot urlshortener

# Incremental backups
nodetool backup

# Cross-datacenter replication for disaster recovery
```

## Monitoring and Alerting

### Key Metrics to Monitor
```yaml
Database Metrics:
  - Connection pool utilization
  - Query execution time (p95, p99)
  - Slow query count
  - Replication lag
  - Disk space usage
  - Cache hit ratios

Application Metrics:
  - URL creation rate
  - Redirection latency
  - Error rates by endpoint
  - API key usage
  - Rate limit violations

Business Metrics:
  - Daily active URLs
  - Click-through rates
  - User growth rate
  - Revenue per user
```

### Automated Maintenance
```python
# Cleanup expired URLs
def cleanup_expired_urls():
    """Remove expired URLs and their associated data"""
    expired_urls = db.query("""
        UPDATE urls SET status = 'expired' 
        WHERE expires_at < NOW() AND status = 'active'
        RETURNING short_code
    """)
    
    # Remove from cache
    for url in expired_urls:
        cache.delete(f"url:{url.short_code}")

# Archive old click events
def archive_old_click_events():
    """Move old click events to Cassandra"""
    cutoff_date = datetime.now() - timedelta(days=30)
    
    old_events = db.query("""
        SELECT * FROM click_events 
        WHERE clicked_at < %s
    """, cutoff_date)
    
    # Insert into Cassandra
    for event in old_events:
        cassandra.insert_click_event(event)
    
    # Delete from PostgreSQL
    db.execute("""
        DELETE FROM click_events 
        WHERE clicked_at < %s
    """, cutoff_date)
```

This database schema provides a robust foundation for a URL shortener service that can handle massive scale while maintaining performance, consistency, and reliability.