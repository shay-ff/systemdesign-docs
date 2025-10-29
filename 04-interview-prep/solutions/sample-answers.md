# Sample System Design Interview Answers

This document provides complete walkthroughs for common system design questions, demonstrating multiple solution approaches, common mistakes to avoid, and evaluation rubrics for self-assessment.

## Table of Contents
1. [URL Shortener (Complete Walkthrough)](#url-shortener-complete-walkthrough)
2. [Social Media Feed (Multiple Approaches)](#social-media-feed-multiple-approaches)
3. [Chat Application (Real-time Focus)](#chat-application-real-time-focus)
4. [Video Streaming Service (Scale Focus)](#video-streaming-service-scale-focus)
5. [Common Mistakes and Recovery](#common-mistakes-and-recovery)
6. [Self-Assessment Rubrics](#self-assessment-rubrics)

---

## URL Shortener (Complete Walkthrough)

**Question**: Design a URL shortening service like bit.ly that can handle 100M URLs shortened per day.

### Phase 1: Requirements Clarification (8 minutes)

**Candidate**: "Let me start by understanding the requirements. For the URL shortener:

**Functional Requirements**:
- Shorten long URLs to short URLs
- Redirect short URLs to original URLs
- Custom aliases (optional)
- URL expiration (optional)
- Analytics (click tracking)

**Non-Functional Requirements**:
- Scale: 100M URLs shortened per day
- Read-heavy: 100:1 read/write ratio
- Latency: <100ms for redirects
- Availability: 99.9% uptime
- URL length: 6-7 characters

Does this align with your expectations?"

**Interviewer**: "Yes, that sounds right. Focus on the core functionality first."

### Phase 2: Capacity Estimation (5 minutes)

**Candidate**: "Let me do some back-of-envelope calculations:

**Traffic**:
- Write: 100M URLs/day = 100M/(24×3600) ≈ 1,200 URLs/second
- Read: 100:1 ratio = 120,000 redirects/second
- Peak traffic: 2x average = 2,400 writes/s, 240,000 reads/s

**Storage**:
- Each URL record: ~500 bytes (original URL + metadata)
- Daily storage: 100M × 500 bytes = 50GB/day
- 5-year retention: 50GB × 365 × 5 ≈ 90TB

**Bandwidth**:
- Write: 1,200 × 500 bytes = 600KB/s
- Read: 120,000 × 500 bytes = 60MB/s

This gives us a good baseline for our design."

### Phase 3: High-Level Architecture (15 minutes)

**Candidate**: "Let me start with a simple architecture and then scale it:

```
[Client] → [Load Balancer] → [Web Servers] → [Database]
                                ↓
                           [Cache Layer]
```

**Core Components**:

1. **Web Servers**: Handle URL shortening and redirect requests
2. **Database**: Store URL mappings (original → short)
3. **Cache**: Cache popular URLs for fast redirects
4. **Load Balancer**: Distribute traffic across web servers

**URL Encoding Strategy**:
I'll use base62 encoding (a-z, A-Z, 0-9) which gives us 62^6 ≈ 56 billion possible URLs with 6 characters.

**Database Choice**:
I'll use a relational database initially because:
- ACID properties for consistency
- Simple schema
- Good for analytics queries

**Schema**:
```sql
CREATE TABLE urls (
    id BIGINT PRIMARY KEY,
    short_url VARCHAR(7) UNIQUE,
    long_url TEXT,
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    click_count INT DEFAULT 0
);
```

**API Design**:
```
POST /api/v1/shorten
{
  "long_url": "https://example.com/very/long/url",
  "custom_alias": "optional",
  "expires_at": "2024-12-31T23:59:59Z"
}

Response:
{
  "short_url": "https://short.ly/abc123",
  "long_url": "https://example.com/very/long/url"
}

GET /{short_code}
→ 302 Redirect to long_url
```

Does this approach make sense so far?"

### Phase 4: Deep Dive - URL Generation (10 minutes)

**Candidate**: "Let me dive deeper into the URL generation strategy. I see two main approaches:

**Approach 1: Hash-based**
- Hash the long URL using MD5/SHA-1
- Take first 6 characters
- Pros: Deterministic, same URL always gets same short code
- Cons: Hash collisions, not truly random

**Approach 2: Counter-based**
- Use auto-incrementing database ID
- Convert to base62
- Pros: No collisions, predictable
- Cons: Sequential (security concern), single point of failure

**Approach 3: Random Generation**
- Generate random 6-character string
- Check for uniqueness in database
- Retry if collision
- Pros: Truly random, secure
- Cons: Potential performance impact from retries

I'd recommend **Approach 2 with multiple counters**:

```
Service 1: generates IDs 1, 4, 7, 10, ...
Service 2: generates IDs 2, 5, 8, 11, ...
Service 3: generates IDs 3, 6, 9, 12, ...
```

This eliminates collisions while distributing the load."

### Phase 5: Scaling and Optimization (12 minutes)

**Candidate**: "Now let's address the scale requirements:

**Database Scaling**:
- **Read Replicas**: Handle 240K reads/second
- **Sharding**: Partition by short_url hash if single DB becomes bottleneck
- **Connection Pooling**: Manage database connections efficiently

**Caching Strategy**:
- **Application Cache**: Cache popular URLs (80/20 rule)
- **CDN**: Cache redirect responses globally
- **Cache Size**: 20% of daily URLs = 20M × 500 bytes = 10GB

**Architecture Evolution**:
```
                    [CDN]
                      ↓
[Client] → [Load Balancer] → [Web Servers] → [Cache] → [DB Master]
                                                ↓         ↓
                                           [Cache]   [DB Replicas]
```

**Additional Optimizations**:
1. **Bloom Filter**: Check if URL exists before DB query
2. **Async Analytics**: Update click counts asynchronously
3. **Batch Processing**: Batch analytics updates
4. **Geographic Distribution**: Deploy in multiple regions

**Monitoring**:
- Response time percentiles (p50, p95, p99)
- Cache hit ratio
- Database connection pool utilization
- Error rates and types"

### Phase 6: Advanced Topics (10 minutes)

**Candidate**: "Let me address some advanced considerations:

**Custom Aliases**:
- Separate table for custom aliases
- Check uniqueness before allowing
- Premium feature with validation

**Analytics Deep Dive**:
```sql
CREATE TABLE analytics (
    short_url VARCHAR(7),
    timestamp TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    referer TEXT
);
```

**Security Considerations**:
- Rate limiting to prevent abuse
- Malicious URL detection
- HTTPS enforcement
- Input validation and sanitization

**Failure Scenarios**:
- Database failure: Serve from cache, graceful degradation
- Cache failure: Direct database queries
- Service failure: Load balancer health checks

**Cost Optimization**:
- Archive old URLs to cheaper storage
- Compress analytics data
- Use reserved instances for predictable load"

---

## Social Media Feed (Multiple Approaches)

**Question**: Design a social media feed like Twitter that can handle 300M users with 200M daily active users.

### Approach 1: Pull Model (Fan-out on Read)

**Architecture**:
```
[User Request] → [Feed Service] → [Following Service] → [Post Service]
                      ↓
                 [Aggregation] → [Ranking] → [Response]
```

**Process**:
1. User requests feed
2. Fetch list of followed users
3. Fetch recent posts from each followed user
4. Merge and rank posts
5. Return paginated results

**Pros**:
- Simple to implement
- No storage overhead for feeds
- Consistent view of data

**Cons**:
- High latency for users following many people
- Expensive computation on each request
- Poor performance for active users

### Approach 2: Push Model (Fan-out on Write)

**Architecture**:
```
[New Post] → [Fan-out Service] → [Feed Generation] → [User Feeds Storage]
                                        ↓
[User Request] → [Feed Service] → [Pre-computed Feed]
```

**Process**:
1. User creates post
2. Fan-out service identifies all followers
3. Add post to each follower's pre-computed feed
4. User requests are served from pre-computed feeds

**Pros**:
- Fast read performance
- Predictable latency
- Good for read-heavy workloads

**Cons**:
- High storage requirements
- Expensive writes for users with many followers
- Potential inconsistency during updates

### Approach 3: Hybrid Model (Recommended)

**Strategy**:
- **Push for regular users**: Pre-compute feeds for users with <1M followers
- **Pull for celebrities**: Compute feeds on-demand for high-follower users
- **Mixed feeds**: Combine pre-computed + real-time for optimal performance

**Implementation**:
```python
def generate_feed(user_id):
    # Get pre-computed feed (push model)
    precomputed_posts = get_precomputed_feed(user_id)
    
    # Get posts from celebrity users (pull model)
    celebrity_following = get_celebrity_following(user_id)
    celebrity_posts = get_recent_posts(celebrity_following)
    
    # Merge and rank
    all_posts = merge_posts(precomputed_posts, celebrity_posts)
    return rank_posts(all_posts, user_id)
```

---

## Chat Application (Real-time Focus)

**Question**: Design a real-time chat application like WhatsApp that supports 1-on-1 and group messaging.

### Real-time Communication Strategy

**WebSocket Architecture**:
```
[Mobile App] ←→ [WebSocket Gateway] ←→ [Message Service] ←→ [Database]
                        ↓
                 [Message Queue] ←→ [Notification Service]
```

**Connection Management**:
```python
class ConnectionManager:
    def __init__(self):
        self.active_connections = {}  # user_id -> websocket
        
    async def connect(self, user_id, websocket):
        self.active_connections[user_id] = websocket
        await self.update_user_status(user_id, "online")
        
    async def disconnect(self, user_id):
        if user_id in self.active_connections:
            del self.active_connections[user_id]
            await self.update_user_status(user_id, "offline")
            
    async def send_message(self, recipient_id, message):
        if recipient_id in self.active_connections:
            websocket = self.active_connections[recipient_id]
            await websocket.send_text(json.dumps(message))
        else:
            # User offline, queue for later delivery
            await self.queue_message(recipient_id, message)
```

**Message Delivery Guarantees**:
1. **At-least-once delivery**: Use message acknowledgments
2. **Idempotency**: Handle duplicate messages with unique IDs
3. **Ordering**: Use sequence numbers per conversation

**Database Schema**:
```sql
-- Messages table
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID,
    sender_id UUID,
    content TEXT,
    message_type ENUM('text', 'image', 'file'),
    created_at TIMESTAMP,
    sequence_number BIGINT
);

-- Conversations table
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    type ENUM('direct', 'group'),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Participants table
CREATE TABLE participants (
    conversation_id UUID,
    user_id UUID,
    joined_at TIMESTAMP,
    last_read_sequence BIGINT,
    PRIMARY KEY (conversation_id, user_id)
);
```

---

## Video Streaming Service (Scale Focus)

**Question**: Design a video streaming service like YouTube that can handle 1 billion hours of video watched per day.

### Content Delivery Architecture

**Global CDN Strategy**:
```
[User] → [Edge CDN] → [Regional CDN] → [Origin Servers] → [Storage]
           ↓
    [Adaptive Bitrate]
```

**Video Processing Pipeline**:
```
[Upload] → [Validation] → [Transcoding] → [Storage] → [CDN Distribution]
             ↓              ↓             ↓
        [Virus Scan]   [Multiple Formats] [Metadata DB]
```

**Transcoding Strategy**:
```python
class VideoProcessor:
    def process_video(self, video_file):
        # Generate multiple resolutions
        resolutions = ['240p', '360p', '480p', '720p', '1080p', '4K']
        
        # Parallel transcoding
        tasks = []
        for resolution in resolutions:
            task = self.transcode_async(video_file, resolution)
            tasks.append(task)
            
        # Wait for all transcoding to complete
        transcoded_files = await asyncio.gather(*tasks)
        
        # Generate adaptive bitrate manifest
        manifest = self.create_hls_manifest(transcoded_files)
        
        return {
            'video_files': transcoded_files,
            'manifest': manifest,
            'thumbnails': self.generate_thumbnails(video_file)
        }
```

**Storage Strategy**:
- **Hot Storage**: Recently uploaded, popular videos (SSD)
- **Warm Storage**: Moderately accessed videos (HDD)
- **Cold Storage**: Rarely accessed videos (Archive storage)

**Capacity Planning**:
```
Daily Upload: 500 hours of video
Average File Size: 1GB per hour
Daily Storage: 500GB
With transcoding (5 formats): 2.5TB/day
Annual Storage: ~900TB
```

---

## Common Mistakes and Recovery

### Mistake 1: Not Clarifying Requirements

**Bad Example**:
"I'll design a URL shortener. Let me start with the database schema..."

**Good Recovery**:
"Actually, let me step back. Can you help me understand the scale we're targeting and the key features you'd like me to focus on?"

### Mistake 2: Over-Engineering Early

**Bad Example**:
"We'll need microservices, Kubernetes, service mesh, and distributed caching from day one..."

**Good Recovery**:
"Let me start with a simpler monolithic approach that can handle the current requirements, then discuss how we'd scale it."

### Mistake 3: Ignoring Data Consistency

**Bad Example**:
"We'll use eventual consistency everywhere for better performance."

**Good Recovery**:
"Actually, let me think about which operations need strong consistency. For financial transactions, we definitely need ACID properties..."

### Mistake 4: Not Considering Failure Scenarios

**Bad Example**:
[Presents architecture without discussing failures]

**Good Recovery**:
"I should also discuss what happens when components fail. If the database goes down, we could serve stale data from cache..."

### Mistake 5: Poor Time Management

**Bad Example**:
[Spends 30 minutes on database schema in a 45-minute interview]

**Good Recovery**:
"I notice I'm spending a lot of time on the database design. Should I continue with this level of detail or move to other components?"

---

## Self-Assessment Rubrics

### Requirements Gathering (20 points)

**Excellent (18-20 points)**:
- Asked clarifying questions about both functional and non-functional requirements
- Identified key constraints and assumptions
- Prioritized features appropriately
- Demonstrated understanding of business context

**Good (14-17 points)**:
- Asked some clarifying questions
- Identified most key requirements
- Made reasonable assumptions
- Showed basic business understanding

**Needs Improvement (10-13 points)**:
- Asked few clarifying questions
- Missed important requirements
- Made unrealistic assumptions
- Limited business context awareness

**Poor (0-9 points)**:
- Jumped to solution without clarification
- Misunderstood basic requirements
- Made inappropriate assumptions
- No business context consideration

### Architecture Design (25 points)

**Excellent (23-25 points)**:
- Clear, well-organized architecture
- Appropriate component separation
- Scalable design patterns
- Considered multiple approaches

**Good (18-22 points)**:
- Reasonable architecture
- Most components well-defined
- Some scalability considerations
- Limited alternative approaches

**Needs Improvement (13-17 points)**:
- Basic architecture
- Some unclear components
- Limited scalability planning
- Single approach only

**Poor (0-12 points)**:
- Unclear or inappropriate architecture
- Poorly defined components
- No scalability considerations
- Unrealistic design

### Technical Depth (20 points)

**Excellent (18-20 points)**:
- Deep understanding of technologies
- Appropriate technology choices
- Detailed implementation considerations
- Strong grasp of trade-offs

**Good (14-17 points)**:
- Good technology understanding
- Mostly appropriate choices
- Some implementation details
- Basic trade-off awareness

**Needs Improvement (10-13 points)**:
- Limited technology knowledge
- Some inappropriate choices
- Superficial implementation details
- Minimal trade-off discussion

**Poor (0-9 points)**:
- Poor technology understanding
- Inappropriate technology choices
- No implementation details
- No trade-off consideration

### Scalability and Performance (20 points)

**Excellent (18-20 points)**:
- Comprehensive scaling strategy
- Multiple optimization techniques
- Realistic performance estimates
- Proactive bottleneck identification

**Good (14-17 points)**:
- Basic scaling approach
- Some optimization ideas
- Reasonable performance discussion
- Some bottleneck awareness

**Needs Improvement (10-13 points)**:
- Limited scaling considerations
- Few optimization techniques
- Vague performance discussion
- Minimal bottleneck identification

**Poor (0-9 points)**:
- No scaling strategy
- No optimization considerations
- No performance discussion
- No bottleneck awareness

### Communication and Process (15 points)

**Excellent (14-15 points)**:
- Clear, structured communication
- Good time management
- Engaged with interviewer
- Handled questions well

**Good (11-13 points)**:
- Generally clear communication
- Reasonable time management
- Some interviewer engagement
- Handled most questions

**Needs Improvement (8-10 points)**:
- Unclear communication at times
- Poor time management
- Limited engagement
- Struggled with some questions

**Poor (0-7 points)**:
- Poor communication
- Very poor time management
- No engagement
- Couldn't handle questions

### Overall Score Interpretation

- **90-100 points**: Excellent performance, likely to pass senior-level interviews
- **80-89 points**: Good performance, likely to pass mid-level interviews
- **70-79 points**: Acceptable performance, may pass with some luck
- **60-69 points**: Needs improvement, unlikely to pass without more preparation
- **Below 60 points**: Significant preparation needed

### Improvement Action Items

Based on your score, focus on:

**If weak in Requirements (< 14 points)**:
- Practice asking clarifying questions
- Study business contexts of different systems
- Learn to prioritize features

**If weak in Architecture (< 18 points)**:
- Study common architecture patterns
- Practice drawing system diagrams
- Learn about component responsibilities

**If weak in Technical Depth (< 14 points)**:
- Deepen knowledge of databases, caching, messaging
- Study technology trade-offs
- Practice API design

**If weak in Scalability (< 14 points)**:
- Study scaling patterns and techniques
- Learn about performance optimization
- Practice capacity estimation

**If weak in Communication (< 11 points)**:
- Practice explaining technical concepts clearly
- Work on time management
- Practice with mock interviews