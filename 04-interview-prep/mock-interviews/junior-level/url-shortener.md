# Mock Interview: URL Shortener (Junior Level)

**Duration**: 45 minutes  
**Difficulty**: Beginner  
**Target Experience**: 0-2 years  

## Interview Setup

### Interviewer Instructions
- Keep the candidate focused on core functionality
- Guide them if they get stuck on advanced topics
- Emphasize basic system design principles
- Allow some flexibility in technology choices

### Candidate Preparation
- Review basic database concepts
- Understand HTTP status codes
- Know basic caching concepts
- Practice drawing simple system diagrams

---

## Interview Script

### Opening (5 minutes)

**Interviewer**: "Today we'll design a URL shortening service like bit.ly. The service should take long URLs and return shortened versions that redirect to the original URL. Do you have any initial questions about the problem?"

**Expected Candidate Response**: Should ask clarifying questions about requirements.

**Interviewer Follow-up Questions** (if not asked by candidate):
- "What's the expected scale? Let's say 100 million URLs shortened per month"
- "Should we support custom aliases?"
- "Do we need analytics or just basic shortening?"
- "What about URL expiration?"

### Requirements Clarification (8 minutes)

**Interviewer**: "Let's define the requirements together."

**Functional Requirements** (guide candidate to identify):
- Shorten long URLs to short URLs (6-7 characters)
- Redirect short URLs to original URLs
- Custom aliases (optional feature)
- Basic analytics (click count)

**Non-Functional Requirements**:
- 100M URLs shortened per month
- 100:1 read/write ratio (more redirects than shortening)
- 99.9% availability
- Low latency for redirects (<100ms)

**Interviewer Notes**: 
- If candidate jumps to solution, redirect: "Let's make sure we understand requirements first"
- If candidate asks about advanced features, say: "Let's focus on core functionality first"

### Capacity Estimation (7 minutes)

**Interviewer**: "Can you estimate the capacity requirements?"

**Expected Calculations** (guide if needed):
```
Write Operations:
- 100M URLs/month = ~40 URLs/second average
- Peak: 2x average = 80 URLs/second

Read Operations:
- 100:1 ratio = 4,000 redirects/second average
- Peak: 8,000 redirects/second

Storage:
- Average URL length: 100 characters
- Metadata: ~200 bytes per URL
- Monthly storage: 100M × 300 bytes = 30GB/month
- 5-year retention: 30GB × 12 × 5 = 1.8TB
```

**Interviewer Prompts**:
- "What's the read/write ratio?"
- "How much storage do we need?"
- "What about peak traffic?"

### High-Level Design (15 minutes)

**Interviewer**: "Now let's design the system architecture."

**Expected Architecture**:
```
[Client] → [Load Balancer] → [Web Servers] → [Database]
                                ↓
                           [Cache Layer]
```

**Key Components to Discuss**:
1. **Web Servers**: Handle API requests
2. **Database**: Store URL mappings
3. **Cache**: Cache popular URLs
4. **Load Balancer**: Distribute traffic

**Interviewer Questions**:
- "How would you generate the short URLs?"
- "What database would you choose and why?"
- "Where would you add caching?"

**Expected Responses**:
- **URL Generation**: Base62 encoding, counter-based approach
- **Database**: Start with SQL (MySQL/PostgreSQL) for simplicity
- **Caching**: Redis/Memcached for popular URLs

### API Design (5 minutes)

**Interviewer**: "What would the APIs look like?"

**Expected APIs**:
```
POST /api/v1/shorten
Request: {
  "long_url": "https://example.com/very/long/url",
  "custom_alias": "optional",
  "expires_at": "2024-12-31T23:59:59Z"
}
Response: {
  "short_url": "https://short.ly/abc123",
  "long_url": "https://example.com/very/long/url"
}

GET /{short_code}
Response: 302 Redirect to long_url
```

### Database Design (3 minutes)

**Interviewer**: "How would you design the database schema?"

**Expected Schema**:
```sql
CREATE TABLE urls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    short_code VARCHAR(10) UNIQUE,
    long_url TEXT NOT NULL,
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    click_count INT DEFAULT 0
);

CREATE INDEX idx_short_code ON urls(short_code);
CREATE INDEX idx_created_at ON urls(created_at);
```

### Scaling Discussion (2 minutes)

**Interviewer**: "How would you scale this system?"

**Expected Responses**:
- **Database**: Read replicas for redirects
- **Caching**: Cache popular URLs (80/20 rule)
- **CDN**: Global distribution for redirects
- **Load Balancing**: Multiple web servers

---

## Evaluation Criteria

### Technical Knowledge (40 points)

**Excellent (36-40 points)**:
- Understands basic system components
- Makes reasonable technology choices
- Explains URL generation approach
- Considers caching appropriately

**Good (28-35 points)**:
- Identifies most key components
- Makes mostly appropriate choices
- Basic understanding of scaling
- Some caching consideration

**Needs Improvement (20-27 points)**:
- Missing key components
- Poor technology choices
- Limited scaling understanding
- No caching consideration

**Poor (0-19 points)**:
- Doesn't understand basic concepts
- Inappropriate design choices
- No scaling consideration
- Major technical gaps

### Problem-Solving Process (30 points)

**Excellent (27-30 points)**:
- Asks clarifying questions
- Systematic approach to design
- Builds solution incrementally
- Considers edge cases

**Good (21-26 points)**:
- Some clarifying questions
- Generally systematic approach
- Mostly incremental building
- Limited edge case consideration

**Needs Improvement (15-20 points)**:
- Few clarifying questions
- Somewhat disorganized approach
- Jumps around in solution
- No edge case consideration

**Poor (0-14 points)**:
- No clarifying questions
- Disorganized approach
- Confused solution building
- No systematic thinking

### Communication (30 points)

**Excellent (27-30 points)**:
- Clear explanations
- Good use of diagrams
- Engages with interviewer
- Explains reasoning well

**Good (21-26 points)**:
- Generally clear communication
- Some diagram usage
- Some interviewer engagement
- Basic reasoning explanation

**Needs Improvement (15-20 points)**:
- Unclear at times
- Limited diagram usage
- Little engagement
- Poor reasoning explanation

**Poor (0-14 points)**:
- Very unclear communication
- No diagrams
- No engagement
- No reasoning provided

---

## Common Candidate Mistakes

### Mistake 1: Over-Engineering
**Problem**: Immediately suggesting microservices, Kubernetes, etc.
**Interviewer Response**: "Let's start with a simpler approach that can handle our current requirements."

### Mistake 2: Ignoring Requirements
**Problem**: Not asking about scale, features, or constraints.
**Interviewer Response**: "What questions do you have about the requirements?"

### Mistake 3: Poor URL Generation
**Problem**: Suggesting complex hashing without considering collisions.
**Interviewer Response**: "What happens if two URLs generate the same hash?"

### Mistake 4: No Caching Strategy
**Problem**: Not considering caching for performance.
**Interviewer Response**: "How would you handle popular URLs that get accessed frequently?"

### Mistake 5: Weak Database Design
**Problem**: Poor schema or no indexing consideration.
**Interviewer Response**: "How would you optimize database queries for redirects?"

---

## Follow-up Questions (if time permits)

### Easy Follow-ups:
- "How would you handle URL expiration?"
- "What if we wanted to show click analytics?"
- "How would you prevent abuse of the service?"

### Medium Follow-ups:
- "How would you handle custom aliases?"
- "What if we needed to support 10x more traffic?"
- "How would you implement rate limiting?"

### Challenging Follow-ups:
- "How would you ensure the service works globally?"
- "What if we needed to migrate to a new database?"
- "How would you handle malicious URLs?"

---

## Interviewer Debrief Notes

### Strengths to Look For:
- [ ] Asked clarifying questions about requirements
- [ ] Provided reasonable capacity estimates
- [ ] Drew clear system architecture diagram
- [ ] Explained technology choices
- [ ] Considered basic scaling strategies
- [ ] Communicated clearly throughout

### Red Flags:
- [ ] Jumped to solution without understanding requirements
- [ ] Made inappropriate technology choices for scale
- [ ] Couldn't explain basic concepts
- [ ] Poor communication or organization
- [ ] No consideration of performance or scaling
- [ ] Couldn't handle simple follow-up questions

### Overall Assessment:
- **Strong Pass**: 80+ points, ready for junior system design roles
- **Pass**: 65-79 points, has potential with some improvement
- **Weak Pass**: 50-64 points, needs significant improvement
- **No Pass**: <50 points, not ready for system design interviews

### Feedback Template:
"You did well on [specific strengths]. Areas for improvement include [specific weaknesses]. I'd recommend focusing on [specific study areas] before your next interview."