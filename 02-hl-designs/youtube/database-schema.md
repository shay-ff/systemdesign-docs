# YouTube System - Database Schema

## Database Architecture Overview

YouTube uses a polyglot persistence approach with different databases optimized for specific use cases:

- **PostgreSQL**: User accounts, channel data, video metadata
- **Cassandra**: Comments, social interactions, time-series data
- **BigQuery**: Analytics, data warehousing, reporting
- **Redis**: Caching, session management, real-time counters
- **Elasticsearch**: Search indexing, content discovery
- **Google Cloud Storage**: Video files, thumbnails, static assets

## Core Database Schemas

### User Management (PostgreSQL)

#### users
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    country_code VARCHAR(2),
    language VARCHAR(10) DEFAULT 'en',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE,
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    account_status VARCHAR(20) DEFAULT 'active', -- 'active', 'suspended', 'terminated'
    privacy_settings JSONB DEFAULT '{}'
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status ON users(account_status);
```

#### user_sessions
```sql
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    device_type VARCHAR(50),
    device_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    location_country VARCHAR(2),
    location_city VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_user_sessions_token ON user_sessions(session_token);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires ON user_sessions(expires_at);
```

### Channel Management (PostgreSQL)

#### channels
```sql
CREATE TABLE channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel_name VARCHAR(100) NOT NULL,
    handle VARCHAR(50) UNIQUE, -- @channelhandle
    description TEXT,
    avatar_url VARCHAR(500),
    banner_url VARCHAR(500),
    trailer_video_id UUID,
    country_code VARCHAR(2),
    language VARCHAR(10) DEFAULT 'en',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    verified BOOLEAN DEFAULT FALSE,
    monetization_enabled BOOLEAN DEFAULT FALSE,
    subscriber_count BIGINT DEFAULT 0,
    video_count INTEGER DEFAULT 0,
    total_view_count BIGINT DEFAULT 0,
    channel_type VARCHAR(20) DEFAULT 'personal', -- 'personal', 'brand', 'music', 'gaming'
    status VARCHAR(20) DEFAULT 'active' -- 'active', 'suspended', 'terminated'
);

CREATE INDEX idx_channels_user_id ON channels(user_id);
CREATE INDEX idx_channels_handle ON channels(handle);
CREATE INDEX idx_channels_subscriber_count ON channels(subscriber_count);
CREATE INDEX idx_channels_status ON channels(status);
```

#### channel_subscriptions
```sql
CREATE TABLE channel_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    subscribed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    notification_level VARCHAR(20) DEFAULT 'default', -- 'all', 'default', 'none'
    is_public BOOLEAN DEFAULT TRUE,
    UNIQUE(subscriber_user_id, channel_id)
);

CREATE INDEX idx_subscriptions_subscriber ON channel_subscriptions(subscriber_user_id);
CREATE INDEX idx_subscriptions_channel ON channel_subscriptions(channel_id);
CREATE INDEX idx_subscriptions_date ON channel_subscriptions(subscribed_at);
```

### Video Management (PostgreSQL)

#### videos
```sql
CREATE TABLE videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    duration INTEGER, -- in seconds
    upload_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    privacy_status VARCHAR(20) DEFAULT 'private', -- 'public', 'unlisted', 'private'
    category_id INTEGER,
    language VARCHAR(10),
    default_audio_language VARCHAR(10),
    license VARCHAR(20) DEFAULT 'youtube', -- 'youtube', 'creativeCommon'
    made_for_kids BOOLEAN DEFAULT FALSE,
    age_restricted BOOLEAN DEFAULT FALSE,
    view_count BIGINT DEFAULT 0,
    like_count INTEGER DEFAULT 0,
    dislike_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    processing_status VARCHAR(20) DEFAULT 'uploading', -- 'uploading', 'processing', 'succeeded', 'failed'
    upload_status VARCHAR(20) DEFAULT 'uploaded', -- 'uploaded', 'processed', 'failed', 'rejected', 'deleted'
    failure_reason VARCHAR(100),
    rejection_reason VARCHAR(100),
    embeddable BOOLEAN DEFAULT TRUE,
    public_stats_viewable BOOLEAN DEFAULT TRUE,
    comments_disabled BOOLEAN DEFAULT FALSE,
    ratings_disabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_videos_channel_id ON videos(channel_id);
CREATE INDEX idx_videos_published_at ON videos(published_at);
CREATE INDEX idx_videos_privacy_status ON videos(privacy_status);
CREATE INDEX idx_videos_view_count ON videos(view_count);
CREATE INDEX idx_videos_processing_status ON videos(processing_status);
CREATE INDEX idx_videos_category ON videos(category_id);
```

#### video_tags
```sql
CREATE TABLE video_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(video_id, tag)
);

CREATE INDEX idx_video_tags_video_id ON video_tags(video_id);
CREATE INDEX idx_video_tags_tag ON video_tags(tag);
```

#### video_thumbnails
```sql
CREATE TABLE video_thumbnails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    thumbnail_type VARCHAR(20) NOT NULL, -- 'default', 'medium', 'high', 'standard', 'maxres', 'custom'
    url VARCHAR(500) NOT NULL,
    width INTEGER,
    height INTEGER,
    is_custom BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_video_thumbnails_video_id ON video_thumbnails(video_id);
```

### Video Assets (PostgreSQL + Cloud Storage)

#### video_files
```sql
CREATE TABLE video_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    file_type VARCHAR(20) NOT NULL, -- 'original', 'transcoded', 'audio_only'
    quality VARCHAR(20), -- '144p', '240p', '360p', '480p', '720p', '1080p', '1440p', '2160p', '4320p'
    format VARCHAR(20), -- 'mp4', 'webm', 'flv'
    codec VARCHAR(20), -- 'h264', 'h265', 'vp9', 'av1'
    bitrate INTEGER, -- in kbps
    frame_rate DECIMAL(5,2),
    resolution VARCHAR(20), -- '1920x1080'
    file_size BIGINT, -- in bytes
    duration INTEGER, -- in seconds
    storage_path VARCHAR(1000) NOT NULL,
    cdn_url VARCHAR(1000),
    checksum VARCHAR(64),
    processing_status VARCHAR(20) DEFAULT 'pending', -- 'pending', 'processing', 'completed', 'failed'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_video_files_video_id ON video_files(video_id);
CREATE INDEX idx_video_files_quality ON video_files(quality);
CREATE INDEX idx_video_files_processing_status ON video_files(processing_status);
```

#### video_captions
```sql
CREATE TABLE video_captions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    caption_type VARCHAR(20) DEFAULT 'subtitle', -- 'subtitle', 'closed_caption'
    format VARCHAR(10) DEFAULT 'vtt', -- 'vtt', 'srt', 'ass'
    is_auto_generated BOOLEAN DEFAULT FALSE,
    storage_path VARCHAR(1000) NOT NULL,
    cdn_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_video_captions_video_id ON video_captions(video_id);
CREATE INDEX idx_video_captions_language ON video_captions(language);
```

### Comments System (Cassandra)

#### comment_threads
```cql
CREATE TABLE comment_threads (
    video_id UUID,
    thread_id UUID,
    parent_comment_id UUID,
    user_id UUID,
    channel_id UUID,
    comment_text TEXT,
    like_count INT,
    dislike_count INT,
    reply_count INT,
    is_public BOOLEAN,
    can_reply BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (video_id, thread_id)
) WITH CLUSTERING ORDER BY (thread_id DESC);

CREATE INDEX ON comment_threads (user_id);
CREATE INDEX ON comment_threads (created_at);
```

#### comment_replies
```cql
CREATE TABLE comment_replies (
    thread_id UUID,
    reply_id UUID,
    parent_comment_id UUID,
    user_id UUID,
    channel_id UUID,
    comment_text TEXT,
    like_count INT,
    dislike_count INT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    PRIMARY KEY (thread_id, reply_id)
) WITH CLUSTERING ORDER BY (reply_id ASC);
```

#### comment_likes
```cql
CREATE TABLE comment_likes (
    comment_id UUID,
    user_id UUID,
    like_type TEXT, -- 'like', 'dislike'
    created_at TIMESTAMP,
    PRIMARY KEY (comment_id, user_id)
);
```

### User Interactions (Cassandra)

#### video_likes
```cql
CREATE TABLE video_likes (
    video_id UUID,
    user_id UUID,
    like_type TEXT, -- 'like', 'dislike'
    created_at TIMESTAMP,
    PRIMARY KEY (video_id, user_id)
);

CREATE INDEX ON video_likes (user_id);
```

#### watch_history
```cql
CREATE TABLE watch_history (
    user_id UUID,
    video_id UUID,
    channel_id UUID,
    watch_time_seconds INT,
    total_duration_seconds INT,
    completion_percentage DECIMAL,
    last_watched_at TIMESTAMP,
    device_type TEXT,
    quality TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, last_watched_at, video_id)
) WITH CLUSTERING ORDER BY (last_watched_at DESC, video_id ASC);
```

#### playlists
```sql
CREATE TABLE playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    privacy_status VARCHAR(20) DEFAULT 'private', -- 'public', 'unlisted', 'private'
    video_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE playlist_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(playlist_id, video_id)
);
```

## Cache Layer (Redis)

### Session and User Data
```
Key Pattern: session:{session_token}
Value: JSON object with user session data
TTL: 24 hours

Key Pattern: user:{user_id}:profile
Value: JSON object with user profile data
TTL: 1 hour

Key Pattern: channel:{channel_id}:info
Value: JSON object with channel information
TTL: 30 minutes
```

### Video Metadata and Statistics
```
Key Pattern: video:{video_id}:metadata
Value: JSON object with video metadata
TTL: 15 minutes

Key Pattern: video:{video_id}:stats
Value: JSON object with view counts, likes, etc.
TTL: 5 minutes

Key Pattern: trending:videos:{region}:{category}
Value: List of trending video IDs
TTL: 10 minutes
```

### Real-time Counters
```
Key Pattern: video:{video_id}:views:realtime
Value: Current view count
TTL: 1 minute

Key Pattern: channel:{channel_id}:subscribers:realtime
Value: Current subscriber count
TTL: 5 minutes

Key Pattern: live:{video_id}:viewers
Value: Current live viewer count
TTL: 30 seconds
```

## Search Index (Elasticsearch)

### Video Search Index
```json
{
  "mappings": {
    "properties": {
      "video_id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"},
          "suggest": {"type": "completion"}
        }
      },
      "description": {"type": "text"},
      "channel_id": {"type": "keyword"},
      "channel_name": {
        "type": "text",
        "fields": {"keyword": {"type": "keyword"}}
      },
      "tags": {"type": "keyword"},
      "category_id": {"type": "integer"},
      "duration": {"type": "integer"},
      "view_count": {"type": "long"},
      "like_count": {"type": "integer"},
      "published_at": {"type": "date"},
      "language": {"type": "keyword"},
      "privacy_status": {"type": "keyword"},
      "quality_score": {"type": "float"},
      "engagement_score": {"type": "float"},
      "freshness_score": {"type": "float"}
    }
  }
}
```

### Channel Search Index
```json
{
  "mappings": {
    "properties": {
      "channel_id": {"type": "keyword"},
      "channel_name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"},
          "suggest": {"type": "completion"}
        }
      },
      "handle": {"type": "keyword"},
      "description": {"type": "text"},
      "subscriber_count": {"type": "long"},
      "video_count": {"type": "integer"},
      "total_view_count": {"type": "long"},
      "verified": {"type": "boolean"},
      "channel_type": {"type": "keyword"},
      "country_code": {"type": "keyword"},
      "language": {"type": "keyword"},
      "created_at": {"type": "date"}
    }
  }
}
```

## Analytics Database (BigQuery)

### Video Analytics
```sql
CREATE TABLE video_analytics (
  video_id STRING,
  channel_id STRING,
  date DATE,
  country_code STRING,
  device_type STRING,
  traffic_source STRING,
  views INT64,
  watch_time_minutes INT64,
  average_view_duration FLOAT64,
  likes INT64,
  dislikes INT64,
  comments INT64,
  shares INT64,
  subscribers_gained INT64,
  subscribers_lost INT64,
  revenue_usd FLOAT64,
  ad_impressions INT64,
  ad_clicks INT64,
  cpm_usd FLOAT64,
  created_at TIMESTAMP
)
PARTITION BY date
CLUSTER BY video_id, channel_id;
```

### User Engagement Analytics
```sql
CREATE TABLE user_engagement_analytics (
  user_id STRING,
  date DATE,
  session_count INT64,
  total_watch_time_minutes INT64,
  videos_watched INT64,
  unique_channels_watched INT64,
  likes_given INT64,
  comments_posted INT64,
  subscriptions_made INT64,
  searches_performed INT64,
  device_types ARRAY<STRING>,
  top_categories ARRAY<STRING>,
  created_at TIMESTAMP
)
PARTITION BY date
CLUSTER BY user_id;
```

### Content Performance
```sql
CREATE TABLE content_performance (
  video_id STRING,
  channel_id STRING,
  date DATE,
  impressions INT64,
  click_through_rate FLOAT64,
  average_view_percentage FLOAT64,
  audience_retention ARRAY<FLOAT64>,
  traffic_sources STRUCT<
    youtube_search FLOAT64,
    suggested_videos FLOAT64,
    external FLOAT64,
    direct FLOAT64,
    playlists FLOAT64
  >,
  demographics STRUCT<
    age_groups ARRAY<STRUCT<range STRING, percentage FLOAT64>>,
    gender STRUCT<male FLOAT64, female FLOAT64, other FLOAT64>,
    geography ARRAY<STRUCT<country STRING, percentage FLOAT64>>
  >,
  created_at TIMESTAMP
)
PARTITION BY date
CLUSTER BY video_id;
```

## Data Partitioning Strategy

### Horizontal Partitioning
- **videos**: Partitioned by channel_id hash (32 partitions)
- **comments**: Partitioned by video_id in Cassandra
- **watch_history**: Partitioned by user_id in Cassandra
- **analytics**: Partitioned by date in BigQuery

### Geographic Partitioning
- **user_data**: Stored in user's home region for compliance
- **video_files**: Distributed globally with regional CDN caching
- **analytics_data**: Aggregated globally but stored regionally

### Time-based Partitioning
- **analytics tables**: Monthly partitions in BigQuery
- **watch_history**: Daily partitions in Cassandra
- **comment_threads**: Quarterly partitions in Cassandra

## Backup and Recovery

### PostgreSQL
- **Continuous Backup**: WAL-E with streaming replication
- **Point-in-time Recovery**: 30-day retention
- **Cross-region Replication**: Async replication to 3 regions
- **Backup Frequency**: Full backup daily, incremental every hour

### Cassandra
- **Snapshot Backup**: Daily snapshots to cloud storage
- **Incremental Backup**: Hourly incremental backups
- **Multi-datacenter Replication**: 3x replication factor
- **Repair Operations**: Weekly repair jobs

### BigQuery
- **Automatic Backup**: 7-day automatic backup retention
- **Cross-region Backup**: Replicated to secondary region
- **Export Jobs**: Daily exports to Cloud Storage
- **Table Snapshots**: Weekly snapshots for critical tables

### Cloud Storage
- **Multi-region Replication**: 3-way replication across regions
- **Versioning**: Enabled for all video assets
- **Lifecycle Management**: Automated archival after 2 years
- **Disaster Recovery**: Cross-region replication with 99.999999999% durability

This database schema supports YouTube's massive scale while maintaining performance, consistency, and reliability across all core features including video management, user interactions, content discovery, and comprehensive analytics.