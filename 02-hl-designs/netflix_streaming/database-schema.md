# Netflix Streaming System - Database Schema

## Database Architecture Overview

The Netflix streaming system uses a polyglot persistence approach with different databases optimized for specific use cases:

- **PostgreSQL**: User data, content metadata, transactional data
- **Redis**: Caching, session management, real-time data
- **Elasticsearch**: Search indexing and full-text search
- **ClickHouse**: Analytics and time-series data
- **S3/Object Storage**: Video files and static assets

## Core Database Schemas

### User Management (PostgreSQL)

#### users
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    subscription_tier VARCHAR(50) NOT NULL DEFAULT 'basic',
    subscription_status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE,
    email_verified BOOLEAN DEFAULT FALSE,
    country_code VARCHAR(2),
    language VARCHAR(10) DEFAULT 'en-US'
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_subscription ON users(subscription_tier, subscription_status);
```

#### user_profiles
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    is_kids BOOLEAN DEFAULT FALSE,
    language VARCHAR(10) DEFAULT 'en-US',
    maturity_rating VARCHAR(10) DEFAULT 'TV-MA',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
```

#### user_sessions
```sql
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_id UUID REFERENCES user_profiles(id) ON DELETE CASCADE,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    device_type VARCHAR(50),
    device_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_user_sessions_token ON user_sessions(session_token);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
```

### Content Management (PostgreSQL)

#### content
```sql
CREATE TABLE content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(500) NOT NULL,
    original_title VARCHAR(500),
    content_type VARCHAR(50) NOT NULL, -- 'movie', 'series', 'documentary'
    description TEXT,
    release_year INTEGER,
    duration INTEGER, -- in seconds for movies, null for series
    maturity_rating VARCHAR(10),
    language VARCHAR(10),
    country_code VARCHAR(2),
    imdb_id VARCHAR(20),
    tmdb_id INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) DEFAULT 'draft' -- 'draft', 'processing', 'published', 'archived'
);

CREATE INDEX idx_content_type ON content(content_type);
CREATE INDEX idx_content_status ON content(status);
CREATE INDEX idx_content_release_year ON content(release_year);
CREATE INDEX idx_content_rating ON content(maturity_rating);
```

#### content_genres
```sql
CREATE TABLE genres (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE content_genres (
    content_id UUID REFERENCES content(id) ON DELETE CASCADE,
    genre_id INTEGER REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (content_id, genre_id)
);
```

#### content_cast_crew
```sql
CREATE TABLE people (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    birth_date DATE,
    biography TEXT,
    profile_image_url VARCHAR(500),
    imdb_id VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE content_cast_crew (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    role_type VARCHAR(50) NOT NULL, -- 'actor', 'director', 'producer', 'writer'
    character_name VARCHAR(255), -- for actors
    order_index INTEGER, -- for display ordering
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_content_cast_crew_content ON content_cast_crew(content_id);
CREATE INDEX idx_content_cast_crew_person ON content_cast_crew(person_id);
```

#### series_seasons_episodes
```sql
CREATE TABLE seasons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    season_number INTEGER NOT NULL,
    title VARCHAR(500),
    description TEXT,
    release_date DATE,
    episode_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(content_id, season_number)
);

CREATE TABLE episodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    episode_number INTEGER NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    duration INTEGER NOT NULL, -- in seconds
    release_date DATE,
    thumbnail_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(season_id, episode_number)
);

CREATE INDEX idx_episodes_season ON episodes(season_id);
```

### Video Assets (PostgreSQL + Object Storage)

#### video_assets
```sql
CREATE TABLE video_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES content(id) ON DELETE CASCADE,
    episode_id UUID REFERENCES episodes(id) ON DELETE CASCADE,
    asset_type VARCHAR(50) NOT NULL, -- 'video', 'thumbnail', 'trailer'
    quality VARCHAR(20), -- '480p', '720p', '1080p', '4k'
    codec VARCHAR(50), -- 'h264', 'h265', 'av1'
    file_size BIGINT,
    duration INTEGER,
    bitrate INTEGER,
    resolution VARCHAR(20), -- '1920x1080'
    storage_path VARCHAR(1000) NOT NULL,
    cdn_url VARCHAR(1000),
    checksum VARCHAR(64),
    processing_status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_video_assets_content ON video_assets(content_id);
CREATE INDEX idx_video_assets_episode ON video_assets(episode_id);
CREATE INDEX idx_video_assets_quality ON video_assets(quality);
```

#### subtitles_audio_tracks
```sql
CREATE TABLE subtitles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES content(id) ON DELETE CASCADE,
    episode_id UUID REFERENCES episodes(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    format VARCHAR(20) DEFAULT 'vtt', -- 'vtt', 'srt'
    storage_path VARCHAR(1000) NOT NULL,
    cdn_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE audio_tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID REFERENCES content(id) ON DELETE CASCADE,
    episode_id UUID REFERENCES episodes(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    codec VARCHAR(50) DEFAULT 'aac',
    bitrate INTEGER,
    channels INTEGER DEFAULT 2,
    storage_path VARCHAR(1000) NOT NULL,
    cdn_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### User Interactions (PostgreSQL)

#### viewing_history
```sql
CREATE TABLE viewing_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    content_id UUID REFERENCES content(id) ON DELETE CASCADE,
    episode_id UUID REFERENCES episodes(id) ON DELETE CASCADE,
    watch_position INTEGER DEFAULT 0, -- seconds watched
    total_duration INTEGER, -- total content duration
    completion_percentage DECIMAL(5,2) DEFAULT 0.0,
    last_watched_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    device_type VARCHAR(50),
    quality VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_viewing_history_profile ON viewing_history(profile_id);
CREATE INDEX idx_viewing_history_content ON viewing_history(content_id);
CREATE INDEX idx_viewing_history_last_watched ON viewing_history(last_watched_at);
```

#### watchlist
```sql
CREATE TABLE watchlist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(profile_id, content_id)
);

CREATE INDEX idx_watchlist_profile ON watchlist(profile_id);
```

#### ratings_reviews
```sql
CREATE TABLE ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(profile_id, content_id)
);

CREATE INDEX idx_ratings_content ON ratings(content_id);
CREATE INDEX idx_ratings_rating ON ratings(rating);
```

## Cache Layer (Redis)

### Session Management
```
Key Pattern: session:{session_token}
Value: JSON object with user session data
TTL: 24 hours

Key Pattern: user_sessions:{user_id}
Value: Set of active session tokens
TTL: 24 hours
```

### Content Caching
```
Key Pattern: content:{content_id}
Value: JSON object with content metadata
TTL: 1 hour

Key Pattern: trending_content:{region}
Value: List of trending content IDs
TTL: 15 minutes

Key Pattern: recommendations:{profile_id}
Value: JSON array of recommended content
TTL: 30 minutes
```

### Streaming State
```
Key Pattern: streaming:{session_id}
Value: JSON object with streaming session data
TTL: 4 hours

Key Pattern: watch_progress:{profile_id}:{content_id}
Value: JSON object with viewing progress
TTL: 7 days
```

## Search Index (Elasticsearch)

### Content Search Index
```json
{
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"},
          "suggest": {"type": "completion"}
        }
      },
      "description": {"type": "text"},
      "content_type": {"type": "keyword"},
      "genres": {"type": "keyword"},
      "cast": {"type": "text"},
      "director": {"type": "text"},
      "release_year": {"type": "integer"},
      "maturity_rating": {"type": "keyword"},
      "language": {"type": "keyword"},
      "popularity_score": {"type": "float"},
      "created_at": {"type": "date"}
    }
  }
}
```

## Analytics Database (ClickHouse)

### Viewing Events
```sql
CREATE TABLE viewing_events (
    event_id UUID,
    profile_id UUID,
    content_id UUID,
    episode_id Nullable(UUID),
    event_type String, -- 'play', 'pause', 'stop', 'seek', 'quality_change'
    timestamp DateTime,
    session_id UUID,
    device_type String,
    quality String,
    position UInt32, -- seconds
    duration UInt32, -- seconds
    bitrate Nullable(UInt32),
    buffer_health Nullable(Float32),
    error_code Nullable(String),
    country_code String,
    city String,
    user_agent String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, profile_id, content_id);
```

### Content Performance
```sql
CREATE TABLE content_metrics (
    content_id UUID,
    date Date,
    total_views UInt64,
    unique_viewers UInt64,
    total_watch_time UInt64, -- seconds
    completion_rate Float32,
    average_rating Float32,
    bounce_rate Float32,
    country_code String
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, content_id, country_code);
```

## Data Partitioning Strategy

### Horizontal Partitioning
- **viewing_history**: Partitioned by profile_id hash (16 partitions)
- **video_assets**: Partitioned by content_id hash (8 partitions)
- **analytics events**: Partitioned by timestamp (monthly partitions)

### Geographic Partitioning
- **content**: Replicated across regions with region-specific content
- **user_data**: Stored in user's home region for compliance
- **cdn_assets**: Distributed globally with regional caching

## Backup and Recovery

### PostgreSQL
- **Primary Backup**: Continuous WAL archiving to S3
- **Point-in-time Recovery**: 30-day retention
- **Cross-region Replication**: Async replication to 2 regions
- **Backup Frequency**: Full backup daily, incremental every 4 hours

### Redis
- **Persistence**: RDB snapshots every 15 minutes
- **AOF**: Append-only file with fsync every second
- **Replication**: Master-slave setup with automatic failover

### Object Storage
- **Replication**: 3-way replication across availability zones
- **Cross-region**: Critical content replicated to 3 regions
- **Versioning**: Enabled for all video assets
- **Lifecycle**: Automated archival to cheaper storage after 1 year

This database schema supports Netflix's massive scale while maintaining performance, consistency, and reliability across all core features.