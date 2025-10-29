# Mock Interview: Social Media Feed (Mid Level)

**Duration**: 60 minutes  
**Difficulty**: Intermediate  
**Target Experience**: 2-5 years  

## Interview Setup

### Interviewer Instructions
- Focus on distributed systems concepts
- Challenge scaling decisions
- Expect discussion of trade-offs
- Push for deeper technical understanding

### Candidate Preparation
- Review fan-out strategies (push vs pull)
- Understand eventual consistency
- Know about message queues and caching
- Practice explaining complex trade-offs

---

## Interview Script

### Opening (5 minutes)

**Interviewer**: "Today we'll design a social media feed system like Twitter. Users can post tweets, follow other users, and see a personalized feed of tweets from people they follow. The system needs to handle 300 million users with 200 million daily active users. What questions do you have?"

**Expected Candidate Response**: Should ask about functional and non-functional requirements.

### Requirements Clarification (10 minutes)

**Interviewer**: "Let's define the requirements."

**Functional Requirements** (guide candidate to identify):
- Users can post tweets (text, images, videos)
- Users can follow/unfollow other users
- Users can view personalized timeline of followed users' tweets
- Users can view their own tweets
- Basic interactions (like, retweet, reply)

**Non-Functional Requirements**:
- 300M total users, 200M DAU
- Average user follows 200 people
- 100M tweets posted per day
- Timeline generation < 200ms
- 99.99% availability
- Global distribution

**Interviewer Probing Questions**:
- "How do we handle celebrity users with millions of followers?"
- "What about tweet ordering in the timeline?"
- "Should we support real-time updates?"

### Capacity Estimation (8 minutes)

**Interviewer**: "Let's estimate the system capacity."

**Expected Calculations**:
```
Users and Posts:
- 200M DAU
- 100M tweets/day = ~1,200 tweets/second
- Peak: 3x average = 3,600 tweets/second
- Average tweet size: 280 chars + metadata = ~1KB
- Daily storage: 100M × 1KB = 100GB/day

Timeline Reads:
- Each user checks timeline 10 times/day
- 200M × 10 = 2B timeline requests/day
- Average: ~23,000 requests/second
- Peak: ~70,000 requests/second

Fan-out Calculation:
- Average followers per user: 200
- Fan-out writes: 1,200 tweets/sec × 200 followers = 240,000 writes/sec
- Celebrity problem: Some users have millions of followers
```

**Interviewer Follow-up**: "What's the main challenge with these numbers?"

### High-Level Architecture (20 minutes)

**Interviewer**: "Design the system architecture."

**Expected Architecture Discussion**:

#### Initial Simple Approach:
```
[Client] → [Load Balancer] → [API Gateway] → [Timeline Service]
                                                    ↓
                                            [User Service] → [Database]
                                                    ↓
                                            [Tweet Service] → [Database]
```

**Interviewer**: "How would you generate a user's timeline with this approach?"

**Expected Response**: Pull model - fetch followed users, get their recent tweets, merge and sort.

**Interviewer**: "What are the problems with this approach at scale?"

#### Evolved Architecture:
```
[Client] → [CDN] → [Load Balancer] → [API Gateway]
                                           ↓
                    [Timeline Service] ← [Cache Layer]
                           ↓                 ↓
                    [Fan-out Service] → [Message Queue]
                           ↓                 ↓
            [User Service] [Tweet Service] [Notification Service]
                    ↓           ↓               ↓
                [User DB]   [Tweet DB]    [Timeline Cache]
```

**Key Components to Discuss**:

1. **Tweet Service**: Handle tweet creation, storage, retrieval
2. **User Service**: Manage user profiles, following relationships
3. **Timeline Service**: Generate and serve user timelines
4. **Fan-out Service**: Distribute tweets to followers' timelines
5. **Cache Layer**: Store pre-computed timelines
6. **Message Queue**: Async processing of fan-out operations

### Deep Dive: Fan-out Strategies (12 minutes)

**Interviewer**: "Let's discuss how to handle tweet distribution to followers."

**Expected Discussion**:

#### Push Model (Fan-out on Write):
```
New Tweet → Fan-out Service → Write to all followers' timelines
```
**Pros**: Fast timeline reads, pre-computed results
**Cons**: Expensive writes for popular users, storage overhead

#### Pull Model (Fan-out on Read):
```
Timeline Request → Fetch following list → Get recent tweets → Merge & sort
```
**Pros**: No storage overhead, simple writes
**Cons**: Slow timeline generation, expensive for active users

#### Hybrid Approach:
**Interviewer**: "How would you combine both approaches?"

**Expected Response**:
- Push model for regular users (< 1M followers)
- Pull model for celebrities (> 1M followers)
- Mixed timelines: pre-computed + real-time celebrity tweets

```python
def generate_timeline(user_id):
    # Get pre-computed timeline (push model)
    precomputed_tweets = get_precomputed_timeline(user_id)
    
    # Get celebrity following (pull model)
    celebrity_following = get_celebrity_following(user_id)
    celebrity_tweets = get_recent_tweets(celebrity_following)
    
    # Merge and rank
    return merge_and_rank(precomputed_tweets, celebrity_tweets)
```

### Database Design (3 minutes)

**Interviewer**: "How would you design the database schema?"

**Expected Schema**:
```sql
-- Users table
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(100),
    created_at TIMESTAMP,
    follower_count INT,
    following_count INT
);

-- Tweets table
CREATE TABLE tweets (
    tweet_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    content TEXT,
    created_at TIMESTAMP,
    like_count INT DEFAULT 0,
    retweet_count INT DEFAULT 0,
    INDEX idx_user_created (user_id, created_at)
);

-- Following relationships
CREATE TABLE follows (
    follower_id BIGINT,
    following_id BIGINT,
    created_at TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    INDEX idx_following (following_id)
);

-- Pre-computed timelines (for push model)
CREATE TABLE timelines (
    user_id BIGINT,
    tweet_id BIGINT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, tweet_id),
    INDEX idx_user_created (user_id, created_at)
);
```

### Scaling and Advanced Topics (2 minutes)

**Interviewer**: "How would you scale this system further?"

**Expected Responses**:
- **Database Sharding**: Shard by user_id or tweet_id
- **Caching**: Multi-level caching (Redis, CDN)
- **Geographic Distribution**: Regional data centers
- **Read Replicas**: Scale read operations
- **Message Queues**: Kafka for high-throughput fan-out
- **Microservices**: Separate services for different functions

---

## Evaluation Criteria

### Technical Depth (50 points)

**Excellent (45-50 points)**:
- Understands fan-out strategies and trade-offs
- Explains hybrid approach clearly
- Appropriate database design
- Considers scaling challenges
- Discusses consistency models

**Good (35-44 points)**:
- Understands basic fan-out concepts
- Some trade-off discussion
- Reasonable database design
- Basic scaling considerations
- Limited consistency discussion

**Needs Improvement (25-34 points)**:
- Confused about fan-out strategies
- Poor trade-off analysis
- Weak database design
- No scaling considerations
- No consistency awareness

**Poor (0-24 points)**:
- Doesn't understand distributed concepts
- No trade-off consideration
- Inappropriate database design
- No scaling awareness
- Major technical gaps

### System Design Skills (30 points)

**Excellent (27-30 points)**:
- Well-structured architecture
- Appropriate component separation
- Considers multiple approaches
- Handles complexity well

**Good (21-26 points)**:
- Reasonable architecture
- Most components identified
- Some alternative approaches
- Handles moderate complexity

**Needs Improvement (15-20 points)**:
- Basic architecture
- Missing key components
- Single approach only
- Struggles with complexity

**Poor (0-14 points)**:
- Poor architecture
- Major components missing
- No systematic approach
- Cannot handle complexity

### Communication and Process (20 points)

**Excellent (18-20 points)**:
- Clear explanations of complex concepts
- Good use of diagrams
- Systematic problem-solving
- Handles questions well

**Good (14-17 points)**:
- Generally clear communication
- Some diagram usage
- Mostly systematic approach
- Handles most questions

**Needs Improvement (10-13 points)**:
- Unclear explanations
- Limited diagrams
- Disorganized approach
- Struggles with questions

**Poor (0-9 points)**:
- Very poor communication
- No visual aids
- No systematic approach
- Cannot handle questions

---

## Common Candidate Mistakes

### Mistake 1: Not Understanding Fan-out Problem
**Problem**: Doesn't realize the challenge of distributing tweets to millions of followers.
**Interviewer Response**: "What happens when a celebrity with 50 million followers posts a tweet?"

### Mistake 2: Only Considering One Approach
**Problem**: Suggests only push or only pull model without considering hybrid.
**Interviewer Response**: "What are the trade-offs of this approach? Are there alternatives?"

### Mistake 3: Ignoring Consistency Requirements
**Problem**: Doesn't discuss eventual consistency vs strong consistency.
**Interviewer Response**: "What if a user posts a tweet but doesn't see it in their timeline immediately?"

### Mistake 4: Poor Database Design
**Problem**: Inappropriate schema or no consideration of indexing.
**Interviewer Response**: "How would you optimize queries for timeline generation?"

### Mistake 5: No Scaling Strategy
**Problem**: Doesn't consider how to handle growth.
**Interviewer Response**: "What if we needed to support 10x more users?"

---

## Follow-up Questions

### Medium Difficulty:
- "How would you implement real-time notifications?"
- "How would you handle tweet deletion in pre-computed timelines?"
- "What about implementing trending topics?"
- "How would you prevent spam and abuse?"

### Advanced Difficulty:
- "How would you implement global timeline consistency?"
- "What if we needed to support video tweets?"
- "How would you handle data center failures?"
- "How would you implement A/B testing for timeline algorithms?"

### Expert Level:
- "How would you migrate from push to hybrid model with zero downtime?"
- "How would you implement cross-region replication?"
- "What about implementing machine learning for timeline ranking?"

---

## Interviewer Debrief Notes

### Strong Performance Indicators:
- [ ] Identified fan-out problem immediately
- [ ] Discussed multiple approaches and trade-offs
- [ ] Understood scaling challenges
- [ ] Appropriate database design
- [ ] Considered consistency requirements
- [ ] Clear communication of complex concepts

### Areas of Concern:
- [ ] Didn't understand distributed systems concepts
- [ ] Poor trade-off analysis
- [ ] Inappropriate scaling strategies
- [ ] Weak database design
- [ ] Poor communication of technical concepts
- [ ] Couldn't handle follow-up questions

### Overall Assessment:
- **Strong Pass**: 85+ points, ready for mid-level distributed systems roles
- **Pass**: 70-84 points, good foundation with room for growth
- **Weak Pass**: 55-69 points, needs improvement in key areas
- **No Pass**: <55 points, not ready for mid-level system design roles