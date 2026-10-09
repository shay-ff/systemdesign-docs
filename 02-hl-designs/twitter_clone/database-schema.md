# Twitter Clone - Database Schema

## Database Architecture Overview

The Twitter clone uses a polyglot persistence approach with different databases optimized for specific use cases:

- **PostgreSQL**: User data, tweets, and relationships (ACID compliance needed)
- **Redis**: Timeline caches, session storage, and real-time data
- **Elasticsearch**: Search indexing for tweets and users
- **Cassandra**: Analytics and time-series data
- **S3/Object Storage**: Media files (images, videos)

## PostgreSQL Schema

### Users Table
```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    bio TEXT,
    profile_image_url VARCHAR(500),
    cover_image_url VARCHAR(500),
    location VARCHAR(100),
    website VARCHAR(200),
    verified BOOLEAN DEFAULT FALSE,
    private_account BOOLEAN DEFAULT FALSE,
    followers_count INTEGER DEFAULT 0,
    following_count INTEGER DEFAULT 0,
    tweets_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'active' -- active, suspended, deleted
);

-- Indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_last_active ON users(last_active_at);
```

### Tweets Table
```sql
CREATE TABLE tweets (
    tweet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    content TEXT NOT NULL,
    media_urls TEXT[], -- Array of media URLs
    hashtags TEXT[], -- Extracted hashtags
    mentions UUID[], -- Array of mentioned user IDs
    reply_to_tweet_id UUID REFERENCES tweets(tweet_id),
    quote_tweet_id UUID REFERENCES tweets(tweet_id),
    like_count INTEGER DEFAULT 0,
    retweet_count INTEGER DEFAULT 0,
    reply_count INTEGER DEFAULT 0,
    impression_count BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    visibility VARCHAR(20) DEFAULT 'public' -- public, private, deleted
);

-- Indexes
CREATE INDEX idx_tweets_user_id ON tweets(user_id);
CREATE INDEX idx_tweets_created_at ON tweets(created_at DESC);
CREATE INDEX idx_tweets_reply_to ON tweets(reply_to_tweet_id);
CREATE INDEX idx_tweets_hashtags ON tweets USING GIN(hashtags);
CREATE INDEX idx_tweets_mentions ON tweets USING GIN(mentions);
CREATE INDEX idx_tweets_visibility ON tweets(visibility);

-- Composite indexes for timeline queries
CREATE INDEX idx_tweets_user_created ON tweets(user_id, created_at DESC);
CREATE INDEX idx_tweets_public_created ON tweets(created_at DESC) WHERE visibility = 'public';
```

### Follows Table
```sql
CREATE TABLE follows (
    follower_id UUID NOT NULL REFERENCES users(user_id),
    following_id UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT no_self_follow CHECK (follower_id != following_id)
);

-- Indexes
CREATE INDEX idx_follows_follower ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);
CREATE INDEX idx_follows_created_at ON follows(created_at);
```

### Likes Table
```sql
CREATE TABLE likes (
    user_id UUID NOT NULL REFERENCES users(user_id),
    tweet_id UUID NOT NULL REFERENCES tweets(tweet_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (user_id, tweet_id)
);

-- Indexes
CREATE INDEX idx_likes_user_id ON likes(user_id);
CREATE INDEX idx_likes_tweet_id ON likes(tweet_id);
CREATE INDEX idx_likes_created_at ON likes(created_at);
```

### Retweets Table
```sql
CREATE TABLE retweets (
    user_id UUID NOT NULL REFERENCES users(user_id),
    tweet_id UUID NOT NULL REFERENCES tweets(tweet_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (user_id, tweet_id)
);

-- Indexes
CREATE INDEX idx_retweets_user_id ON retweets(user_id);
CREATE INDEX idx_retweets_tweet_id ON retweets(tweet_id);
CREATE INDEX idx_retweets_created_at ON retweets(created_at);
```

### Notifications Table
```sql
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(user_id),
    actor_id UUID NOT NULL REFERENCES users(user_id),
    type VARCHAR(50) NOT NULL, -- like, retweet, follow, mention, reply
    target_tweet_id UUID REFERENCES tweets(tweet_id),
    message TEXT,
    read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(recipient_id, read, created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
```

### Blocks Table
```sql
CREATE TABLE blocks (
    blocker_id UUID NOT NULL REFERENCES users(user_id),
    blocked_id UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

-- Indexes
CREATE INDEX idx_blocks_blocker ON blocks(blocker_id);
CREATE INDEX idx_blocks_blocked ON blocks(blocked_id);
```

## Redis Schema

### Timeline Caches
```
Key Pattern: timeline:home:{user_id}
Type: Sorted Set
Value: Tweet IDs with timestamp scores
TTL: 1 hour

Example:
ZADD timeline:home:user_123 1642248600 tweet_456
ZADD timeline:home:user_123 1642248700 tweet_789
```

### User Timeline Cache
```
Key Pattern: timeline:user:{user_id}
Type: Sorted Set
Value: User's tweet IDs with timestamp scores
TTL: 30 minutes
```

### Tweet Cache
```
Key Pattern: tweet:{tweet_id}
Type: Hash
Fields: All tweet data as JSON
TTL: 2 hours

Example:
HSET tweet:tweet_456 data '{"tweet_id":"tweet_456","content":"Hello world",...}'
```

### User Cache
```
Key Pattern: user:{user_id}
Type: Hash
Fields: User profile data
TTL: 1 hour
```

### Session Storage
```
Key Pattern: session:{session_id}
Type: Hash
Fields: User session data
TTL: 24 hours
```

### Rate Limiting
```
Key Pattern: rate_limit:{user_id}:{endpoint}
Type: String (counter)
TTL: 15 minutes (sliding window)
```

### Real-time Counters
```
Key Pattern: counters:tweet:{tweet_id}
Type: Hash
Fields: like_count, retweet_count, reply_count
TTL: 1 hour
```

## Elasticsearch Schema

### Tweets Index
```json
{
  "mappings": {
    "properties": {
      "tweet_id": {"type": "keyword"},
      "user_id": {"type": "keyword"},
      "username": {"type": "keyword"},
      "display_name": {"type": "text", "analyzer": "standard"},
      "content": {
        "type": "text",
        "analyzer": "standard",
        "search_analyzer": "standard"
      },
      "hashtags": {"type": "keyword"},
      "mentions": {"type": "keyword"},
      "created_at": {"type": "date"},
      "like_count": {"type": "integer"},
      "retweet_count": {"type": "integer"},
      "reply_count": {"type": "integer"},
      "visibility": {"type": "keyword"}
    }
  }
}
```

### Users Index
```json
{
  "mappings": {
    "properties": {
      "user_id": {"type": "keyword"},
      "username": {"type": "keyword"},
      "display_name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "bio": {"type": "text", "analyzer": "standard"},
      "verified": {"type": "boolean"},
      "followers_count": {"type": "integer"},
      "created_at": {"type": "date"}
    }
  }
}
```

## Cassandra Schema (Analytics)

### Tweet Analytics
```cql
CREATE TABLE tweet_analytics (
    tweet_id UUID,
    date DATE,
    hour INT,
    impressions COUNTER,
    likes COUNTER,
    retweets COUNTER,
    replies COUNTER,
    PRIMARY KEY (tweet_id, date, hour)
);
```

### User Analytics
```cql
CREATE TABLE user_analytics (
    user_id UUID,
    date DATE,
    tweets_posted COUNTER,
    likes_received COUNTER,
    followers_gained COUNTER,
    profile_views COUNTER,
    PRIMARY KEY (user_id, date)
);
```

### Trending Topics
```cql
CREATE TABLE trending_hashtags (
    region TEXT,
    date DATE,
    hour INT,
    hashtag TEXT,
    mention_count COUNTER,
    PRIMARY KEY ((region, date, hour), hashtag)
) WITH CLUSTERING ORDER BY (hashtag ASC);
```

## Sharding Strategy

### PostgreSQL Sharding
- **Users**: Shard by user_id hash (consistent hashing)
- **Tweets**: Shard by user_id to keep user's tweets together
- **Follows**: Shard by follower_id
- **Likes/Retweets**: Shard by user_id

### Shard Distribution
```
Shard 1: user_id hash % 16 = 0-3
Shard 2: user_id hash % 16 = 4-7
Shard 3: user_id hash % 16 = 8-11
Shard 4: user_id hash % 16 = 12-15
```

## Data Consistency Patterns

### Strong Consistency
- User account operations (follow/unfollow)
- Tweet creation and deletion
- Authentication and authorization

### Eventual Consistency
- Timeline generation
- Like/retweet counts
- Follower/following counts
- Search indexes

## Backup and Recovery

### PostgreSQL
- Daily full backups
- Continuous WAL archiving
- Point-in-time recovery capability
- Cross-region replication

### Redis
- RDB snapshots every 6 hours
- AOF for durability
- Redis Cluster for high availability

### Elasticsearch
- Daily index snapshots
- Cross-cluster replication
- Index lifecycle management

## Performance Optimizations

### Database Optimizations
- Connection pooling (PgBouncer)
- Read replicas for timeline queries
- Materialized views for complex aggregations
- Partitioning large tables by date

### Caching Strategy
- Multi-level caching (L1: Application, L2: Redis, L3: CDN)
- Cache warming for popular content
- Cache invalidation strategies
- Bloom filters for negative lookups

### Query Optimizations
- Prepared statements
- Query result caching
- Index optimization
- Query plan analysis and tuning