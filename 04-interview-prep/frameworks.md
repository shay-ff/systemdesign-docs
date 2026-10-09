# System Design Interview Frameworks and Methodologies

This guide provides structured approaches to tackle system design interviews with confidence. Master these frameworks to consistently deliver well-organized, comprehensive solutions.

## Table of Contents
1. [The SCALE Framework](#the-scale-framework)
2. [STAR Method for System Design](#star-method-for-system-design)
3. [Time Management Strategies](#time-management-strategies)
4. [Requirement Gathering Templates](#requirement-gathering-templates)
5. [Solution Presentation Structure](#solution-presentation-structure)
6. [Common Pitfalls and Recovery](#common-pitfalls-and-recovery)

---

## The SCALE Framework

**SCALE** is a systematic approach to system design interviews:

### S - Scope and Requirements
**Time Allocation: 8-12 minutes**

#### Functional Requirements
- **What** does the system need to do?
- **Who** are the users and what are their roles?
- **How** will users interact with the system?

**Template Questions to Ask**:
```
- "What are the core features we need to support?"
- "Who are the primary users of this system?"
- "Are there any specific user flows we should prioritize?"
- "What platforms should we support (web, mobile, API)?"
```

#### Non-Functional Requirements
- **Scale**: How many users, requests, data volume?
- **Performance**: Latency, throughput requirements?
- **Availability**: Uptime expectations, disaster recovery?
- **Consistency**: Strong vs eventual consistency needs?

**Template Questions to Ask**:
```
- "How many users do we expect (DAU/MAU)?"
- "What's the expected read/write ratio?"
- "What are the latency requirements?"
- "How important is consistency vs availability?"
- "Are there any compliance requirements?"
```

### C - Capacity Estimation
**Time Allocation: 5-8 minutes**

#### Back-of-Envelope Calculations
```
Users and Traffic:
- Daily Active Users (DAU)
- Requests per second (peak vs average)
- Read/Write ratio

Storage:
- Data per user/transaction
- Growth rate
- Retention period

Bandwidth:
- Request/response sizes
- Media content considerations
```

**Example Calculation Template**:
```
Given: 100M DAU, each user makes 10 requests/day
- Average RPS: 100M × 10 / 86400 = ~11.6K RPS
- Peak RPS: 11.6K × 3 = ~35K RPS (assuming 3x peak factor)
- Storage: If each request stores 1KB, daily storage = 100M × 10 × 1KB = 1TB/day
```

### A - Architecture Design
**Time Allocation: 15-20 minutes**

#### High-Level Architecture
1. **Start Simple**: Begin with basic client-server model
2. **Identify Components**: Break down into logical services
3. **Add Infrastructure**: Load balancers, databases, caches
4. **Consider Data Flow**: How data moves through the system

#### Component Identification Template
```
Client Layer:
- Web browsers, mobile apps, APIs

Application Layer:
- Web servers, application servers
- Microservices (if applicable)

Data Layer:
- Primary databases
- Caches
- Message queues
- File storage
```

### L - Low-Level Design
**Time Allocation: 10-15 minutes**

#### Database Design
- **Schema Design**: Tables, relationships, indexes
- **Data Modeling**: SQL vs NoSQL considerations
- **Partitioning**: Sharding strategies if needed

#### API Design
- **REST Endpoints**: Key operations and their signatures
- **Request/Response**: Data formats and structures
- **Authentication**: How users authenticate and authorize

**API Template Example**:
```
POST /api/v1/posts
GET /api/v1/posts/{id}
GET /api/v1/users/{id}/feed
PUT /api/v1/posts/{id}
DELETE /api/v1/posts/{id}
```

### E - Evolution and Scale
**Time Allocation: 8-12 minutes**

#### Scaling Strategies
- **Horizontal vs Vertical Scaling**
- **Database Scaling**: Read replicas, sharding
- **Caching**: Application, database, CDN
- **Load Balancing**: Algorithms and strategies

#### Monitoring and Reliability
- **Metrics**: What to monitor
- **Alerting**: When to alert
- **Fault Tolerance**: Handling failures
- **Disaster Recovery**: Backup and recovery strategies

---

## STAR Method for System Design

Adapt the traditional STAR method for system design contexts:

### Situation
- **Context**: Understand the business problem
- **Constraints**: Technical and business limitations
- **Stakeholders**: Who will use and maintain the system

### Task
- **Objectives**: What needs to be accomplished
- **Success Criteria**: How to measure success
- **Priorities**: What's most important vs nice-to-have

### Action
- **Design Decisions**: Explain your choices
- **Trade-offs**: Discuss alternatives considered
- **Implementation**: How you'd build it

### Result
- **Benefits**: How your design solves the problem
- **Metrics**: Expected performance improvements
- **Future**: How the system can evolve

---

## Time Management Strategies

### 45-Minute Interview Structure
```
0-10 min:  Requirements and scope clarification
10-15 min: Capacity estimation
15-30 min: High-level architecture design
30-40 min: Deep dive into 1-2 components
40-45 min: Scaling and wrap-up
```

### 60-Minute Interview Structure
```
0-12 min:  Requirements and scope clarification
12-18 min: Capacity estimation
18-35 min: High-level architecture design
35-50 min: Low-level design and API design
50-60 min: Scaling, monitoring, and Q&A
```

### 75-Minute Interview Structure
```
0-15 min:  Requirements and scope clarification
15-22 min: Capacity estimation
22-40 min: High-level architecture design
40-60 min: Low-level design and database schema
60-70 min: Scaling and advanced topics
70-75 min: Final questions and wrap-up
```

### Time Management Tips
1. **Set Expectations**: "I'd like to spend about 10 minutes on requirements"
2. **Check Progress**: "We're about halfway through, let me move to architecture"
3. **Prioritize**: If running short, focus on core components
4. **Ask for Guidance**: "Would you like me to dive deeper into X or move to Y?"

---

## Requirement Gathering Templates

### Functional Requirements Checklist
```
Core Features:
□ User registration and authentication
□ Primary user actions (post, search, etc.)
□ Content management (create, read, update, delete)
□ User interactions (like, comment, share)
□ Notifications and alerts

Advanced Features:
□ Real-time updates
□ Personalization and recommendations
□ Analytics and reporting
□ Admin and moderation tools
□ Third-party integrations
```

### Non-Functional Requirements Template
```
Scale:
- Users: ___ DAU, ___ MAU
- Data: ___ GB/TB total, ___ growth rate
- Traffic: ___ RPS average, ___ RPS peak

Performance:
- Latency: ___ ms for reads, ___ ms for writes
- Throughput: ___ requests/second
- Availability: ___% uptime (99.9%, 99.99%)

Other:
- Consistency requirements: Strong/Eventual
- Security requirements: Authentication, encryption
- Compliance: GDPR, HIPAA, etc.
```

### Clarifying Questions Bank
```
Scope Questions:
- "Should we focus on the core functionality or include advanced features?"
- "Are there any features that are explicitly out of scope?"
- "What's the priority order of features if we need to make trade-offs?"

Scale Questions:
- "What's the expected user growth over the next 2-3 years?"
- "Are there any seasonal or event-driven traffic spikes?"
- "What geographic regions should we support?"

Technical Questions:
- "Are there any existing systems we need to integrate with?"
- "Do we have any technology preferences or constraints?"
- "What's the budget for infrastructure costs?"
```

---

## Solution Presentation Structure

### Opening Statement Template
```
"Based on our discussion, I understand we need to design [SYSTEM] that supports [KEY FEATURES] for [USER SCALE] with [PERFORMANCE REQUIREMENTS]. Let me start with a high-level architecture and then dive into the details."
```

### Architecture Walkthrough Structure
1. **Start with User Journey**: "When a user wants to [ACTION], here's what happens..."
2. **Trace Data Flow**: "The request goes from client → load balancer → app server → database"
3. **Explain Components**: "The app server handles business logic and calls these services..."
4. **Justify Decisions**: "I chose this approach because..."

### Deep Dive Presentation
```
Component: [NAME]
Purpose: What it does and why it's needed
Interface: How other components interact with it
Implementation: Key technical details
Alternatives: Other options considered and why this was chosen
```

### Scaling Discussion Structure
```
Current Bottlenecks:
- Identify potential failure points
- Explain capacity limits

Scaling Solutions:
- Horizontal scaling approaches
- Caching strategies
- Database optimizations

Monitoring:
- Key metrics to track
- Alerting thresholds
- Health checks
```

---

## Common Pitfalls and Recovery

### Pitfall 1: Jumping to Solution Too Quickly
**Problem**: Starting to design without understanding requirements
**Recovery**: 
- "Let me step back and make sure I understand the requirements correctly"
- "Can we clarify the scope before I continue with the design?"

### Pitfall 2: Over-Engineering Early
**Problem**: Adding complexity before establishing basics
**Recovery**:
- "Let me start with a simpler approach and then discuss how to scale"
- "I'll design for the current requirements and then show how to evolve"

### Pitfall 3: Ignoring Non-Functional Requirements
**Problem**: Focusing only on features, not scale/performance
**Recovery**:
- "Let me revisit the performance requirements and adjust my design"
- "I should consider how this handles [SCALE/LATENCY] requirements"

### Pitfall 4: Not Explaining Trade-offs
**Problem**: Making decisions without justification
**Recovery**:
- "Let me explain why I chose this approach over alternatives"
- "The trade-off here is [X] vs [Y], and I chose [X] because..."

### Pitfall 5: Running Out of Time
**Problem**: Spending too much time on one area
**Recovery**:
- "I notice we're running short on time. Should I focus on [X] or move to [Y]?"
- "Let me quickly cover the key points of [REMAINING TOPICS]"

### Pitfall 6: Getting Stuck on Details
**Problem**: Spending too much time on implementation specifics
**Recovery**:
- "I'm getting into implementation details. Should I continue or move to higher-level concerns?"
- "Let me note this detail and come back to it if we have time"

---

## Advanced Interview Techniques

### The Iterative Approach
1. **Version 1**: Simple, working solution
2. **Version 2**: Handle scale requirements
3. **Version 3**: Add advanced features
4. **Version 4**: Optimize for specific constraints

### The Constraint-Driven Method
1. **Identify the Primary Constraint**: Scale, latency, consistency, cost
2. **Design Around the Constraint**: Make it the central consideration
3. **Validate Against Other Requirements**: Ensure nothing else breaks
4. **Optimize**: Fine-tune for secondary constraints

### The Component-First Strategy
1. **Identify Core Components**: What are the main building blocks?
2. **Design Each Component**: Interface, responsibilities, implementation
3. **Connect Components**: How do they communicate?
4. **Optimize Connections**: Reduce latency, increase throughput

---

## Practice Framework

### Self-Assessment Questions
After each practice session, ask yourself:
- Did I clarify requirements before designing?
- Did I consider both functional and non-functional requirements?
- Did I explain my reasoning for major decisions?
- Did I discuss trade-offs and alternatives?
- Did I manage time effectively?
- Did I handle follow-up questions well?

### Improvement Areas Checklist
```
□ Requirement gathering speed and thoroughness
□ Capacity estimation accuracy and speed
□ Architecture diagram clarity and completeness
□ Component design depth and accuracy
□ API design completeness and RESTfulness
□ Database schema appropriateness
□ Scaling strategy comprehensiveness
□ Trade-off analysis depth
□ Communication clarity and confidence
□ Time management and prioritization
```

### Mock Interview Structure
1. **Warm-up** (5 min): Brief introduction and question clarification
2. **Main Interview** (45-75 min): Follow framework structure
3. **Feedback** (10 min): Discuss strengths and improvement areas
4. **Reflection** (5 min): Note key learnings and next steps

Remember: The goal isn't to have the "perfect" solution, but to demonstrate systematic thinking, clear communication, and the ability to make reasonable trade-offs under time pressure.