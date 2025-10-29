# Content Templates

This document provides standardized templates for creating consistent content across the System Design Learning Guide.

## 📚 Foundation Topic Template

Use this template for core concept explanations in the `00-foundations/` directory.

````markdown
# Topic Name

Brief one-sentence description of what this topic covers and why it's important.

## What You'll Learn

- Key concept 1
- Key concept 2
- Key concept 3
- Practical applications

## Prerequisites

- Required knowledge 1
- Required knowledge 2

## Core Concepts

### Main Concept 1

Clear explanation with real-world analogy.

> **💡 Tip:** Helpful insight or best practice

#### Example

Concrete example that illustrates the concept.

```language
// Code example if applicable
```
````

### Main Concept 2

Another key concept with explanation.

> **⚠️ Warning:** Common pitfall to avoid

## Real-World Applications

### Use Case 1: [Specific Application]

How this concept applies in practice.

### Use Case 2: [Another Application]

Another practical application.

## Trade-offs and Considerations

| Approach | Pros     | Cons      | When to Use |
| -------- | -------- | --------- | ----------- |
| Option 1 | Benefits | Drawbacks | Scenarios   |
| Option 2 | Benefits | Drawbacks | Scenarios   |

## Common Misconceptions

### Misconception 1

**Wrong thinking:** Incorrect belief
**Reality:** Correct explanation

### Misconception 2

**Wrong thinking:** Another incorrect belief
**Reality:** Correct explanation

## Key Takeaways

- Essential point 1
- Essential point 2
- Essential point 3

## Further Reading

- [Related internal topic](../path/to/topic.md)
- [External resource](https://example.com) - Brief description
- [Another resource](https://example.com) - Brief description

## Practice Questions

1. **Question 1:** Test understanding of concept 1
2. **Question 2:** Test understanding of concept 2
3. **Question 3:** Application-based question

> **🔍 Deep Dive:** For advanced learners, explore [advanced topic](link)

````

## ⚙️ Low-Level Design Template

Use this template for implementable system components in `01-ll-designs/`.

### README.md Template
```markdown
# System Name

One-sentence description of the system and its primary purpose.

## Problem Statement

### Requirements
- Functional requirement 1
- Functional requirement 2
- Non-functional requirement 1
- Non-functional requirement 2

### Constraints
- Constraint 1 (e.g., memory limit)
- Constraint 2 (e.g., time complexity)

## Solution Overview

High-level approach to solving the problem.

### Key Components
- Component 1: Brief description
- Component 2: Brief description
- Component 3: Brief description

### Architecture Diagram

```mermaid
graph TB
    A[Component A] --> B[Component B]
    B --> C[Component C]

    style A fill:#2563eb,color:#fff
    style B fill:#059669,color:#fff
    style C fill:#dc2626,color:#fff
````

## Implementations

| Language                    | Complexity          | Features              |
| --------------------------- | ------------------- | --------------------- |
| [Python](solutions/python/) | Beginner-friendly   | Clean, readable       |
| [Java](solutions/java/)     | Object-oriented     | Type-safe, performant |
| [C++](solutions/cpp/)       | Performance-focused | Memory-efficient      |
| [Go](solutions/go/)         | Concurrent          | Simple, concurrent    |

## Quick Start

### Python Example

```bash
cd solutions/python
python system_name.py
```

### Java Example

```bash
cd solutions/java
javac SystemName.java
java SystemNameDemo
```

## Complexity Analysis

### Time Complexity

- Operation 1: O(complexity) - Explanation
- Operation 2: O(complexity) - Explanation

### Space Complexity

- Overall: O(complexity) - Explanation

## Trade-offs

| Approach   | Time | Space | Pros     | Cons      |
| ---------- | ---- | ----- | -------- | --------- |
| Approach 1 | O(n) | O(1)  | Benefits | Drawbacks |
| Approach 2 | O(1) | O(n)  | Benefits | Drawbacks |

## Extensions

- [ ] Enhancement 1
- [ ] Enhancement 2
- [ ] Performance optimization

## Related Topics

- [Related LLD](../other-system/)
- [Foundation concept](../../00-foundations/concept.md)
- [HLD application](../../02-hl-designs/system/)

````

### explanation.md Template
```markdown
# System Name - Design Deep Dive

## Design Decisions

### Core Data Structure Choice

**Decision:** Chosen data structure
**Reasoning:** Why this choice was made
**Alternatives considered:** Other options and why they were rejected

### Algorithm Selection

**Decision:** Chosen algorithm
**Reasoning:** Performance and complexity considerations
**Trade-offs:** What we gain and what we sacrifice

## Implementation Details

### Key Classes/Structures

#### Class 1: [Name]
**Purpose:** What this class does
**Key methods:**
- `method1()`: Description and complexity
- `method2()`: Description and complexity

#### Class 2: [Name]
**Purpose:** What this class does
**Relationships:** How it interacts with other classes

### Critical Operations

#### Operation 1: [Name]
```python
def operation_example():
    # Step-by-step explanation
    pass
````

**Flow:**

1. Step 1 explanation
2. Step 2 explanation
3. Step 3 explanation

**Edge cases handled:**

- Edge case 1 and solution
- Edge case 2 and solution

## Performance Analysis

### Benchmarks

| Operation   | Best Case | Average Case | Worst Case |
| ----------- | --------- | ------------ | ---------- |
| Operation 1 | O(1)      | O(log n)     | O(n)       |
| Operation 2 | O(1)      | O(1)         | O(n)       |

### Memory Usage

- Base memory: O(capacity)
- Per operation overhead: O(1)
- Peak memory scenarios: When and why

## Optimization Opportunities

### Current Limitations

1. Limitation 1 and impact
2. Limitation 2 and impact

### Potential Improvements

1. **Improvement 1:** Description and expected benefit
2. **Improvement 2:** Description and expected benefit

### Advanced Variations

- Variation 1: Use case and modifications needed
- Variation 2: Use case and modifications needed

## Production Considerations

### Scalability

- How the system scales with load
- Bottlenecks and mitigation strategies

### Reliability

- Failure modes and handling
- Recovery mechanisms

### Monitoring

- Key metrics to track
- Warning signs and alerts

## Comparison with Alternatives

| Approach                  | Our Solution | Alternative 1 | Alternative 2 |
| ------------------------- | ------------ | ------------- | ------------- |
| Time Complexity           | O(x)         | O(y)          | O(z)          |
| Space Complexity          | O(a)         | O(b)          | O(c)          |
| Implementation Complexity | Medium       | Low           | High          |
| Use Cases                 | Best for X   | Best for Y    | Best for Z    |

## Learning Outcomes

After studying this implementation, you should understand:

- Core concept 1
- Core concept 2
- When to apply this pattern
- How to adapt it for different requirements

````

### Language-Specific Solution Template

#### solutions/python/README.md
```markdown
# System Name - Python Implementation

Clean, readable implementation focusing on clarity and Pythonic patterns.

## Features

- Feature 1
- Feature 2
- Type hints for better code documentation
- Comprehensive docstrings

## Usage

```python
from system_name import SystemName

# Create instance
system = SystemName(capacity=100)

# Use the system
result = system.operation("example")
print(f"Result: {result}")
````

## Running the Code

```bash
# Run the main example
python system_name.py

# Run tests (if available)
python -m pytest test_system_name.py
```

## Code Structure

- `system_name.py`: Main implementation
- `test_system_name.py`: Unit tests
- `demo.py`: Usage examples

## Key Implementation Details

### Design Choices

- Choice 1: Reasoning
- Choice 2: Reasoning

### Python-Specific Features Used

- Feature 1: How and why
- Feature 2: How and why

## Complexity

- Time: O(operation complexity)
- Space: O(space complexity)

## Extensions

Ideas for extending this implementation:

- Extension 1
- Extension 2

````

## 🏛️ High-Level Design Template

Use this template for system architecture designs in `02-hl-designs/`.

### README.md Template
```markdown
# System Name

Brief description of the system and what it does (e.g., "A distributed social media platform supporting millions of users").

## System Overview

### What We're Building
High-level description of the system's purpose and main functionality.

### Key Features
- Feature 1
- Feature 2
- Feature 3

### Scale Requirements
- Users: X million active users
- Data: Y TB of data
- Requests: Z requests per second

## Architecture Overview

```mermaid
graph TB
    Users[Users] --> LB[Load Balancer]
    LB --> API[API Gateway]
    API --> Services[Microservices]
    Services --> DB[(Databases)]
    Services --> Cache[Cache Layer]

    style Users fill:#e2e8f0
    style LB fill:#7c3aed,color:#fff
    style API fill:#0891b2,color:#fff
    style Services fill:#2563eb,color:#fff
    style DB fill:#059669,color:#fff
    style Cache fill:#dc2626,color:#fff
````

## Deep Dive Sections

| Section                                 | Description                                |
| --------------------------------------- | ------------------------------------------ |
| [Requirements](requirements.md)         | Functional and non-functional requirements |
| [Architecture](architecture.puml)       | Detailed system architecture               |
| [API Design](api-design.md)             | REST API specifications                    |
| [Database Schema](database-schema.md)   | Data model and relationships               |
| [Scaling Strategy](scaling-strategy.md) | How to handle growth                       |
| [Trade-offs](tradeoffs.md)              | Design decisions and alternatives          |
| [Complete Solution](solution.md)        | End-to-end walkthrough                     |

## Quick Navigation

### For Beginners

1. Start with [Requirements](requirements.md)
2. Review [Architecture Overview](architecture.puml)
3. Read [Complete Solution](solution.md)

### For Interview Prep

1. Practice with [Requirements](requirements.md)
2. Study [Trade-offs](tradeoffs.md)
3. Review [Scaling Strategy](scaling-strategy.md)

### For Implementation

1. Study [API Design](api-design.md)
2. Review [Database Schema](database-schema.md)
3. Check [Scaling Strategy](scaling-strategy.md)

## Related Systems

- [Similar System 1](../similar-system-1/) - How it differs
- [Similar System 2](../similar-system-2/) - Key differences
- [Foundation Concepts](../../00-foundations/) - Prerequisites

````

### requirements.md Template
```markdown
# System Requirements

## Functional Requirements

### Core Features
1. **Feature 1**
   - User can perform action X
   - System responds with Y
   - Edge case handling: Z

2. **Feature 2**
   - User can perform action A
   - System responds with B
   - Constraints: C

### User Stories
- As a [user type], I want [functionality], so that [benefit]
- As a [user type], I want [functionality], so that [benefit]

## Non-Functional Requirements

### Performance
- **Latency**: Response time requirements
- **Throughput**: Requests per second
- **Availability**: Uptime requirements (e.g., 99.9%)

### Scalability
- **Users**: Number of concurrent users
- **Data**: Amount of data to store
- **Growth**: Expected growth rate

### Reliability
- **Data consistency**: Requirements for data accuracy
- **Fault tolerance**: How to handle failures
- **Recovery**: Recovery time objectives

### Security
- **Authentication**: User verification requirements
- **Authorization**: Access control needs
- **Data protection**: Privacy and encryption needs

## Constraints

### Technical Constraints
- Technology stack limitations
- Integration requirements
- Legacy system compatibility

### Business Constraints
- Budget limitations
- Timeline requirements
- Regulatory compliance

## Success Metrics

### User Experience
- Metric 1: Target value
- Metric 2: Target value

### System Performance
- Metric 1: Target value
- Metric 2: Target value

### Business Impact
- Metric 1: Target value
- Metric 2: Target value
````

## 💻 Implementation Template

Use this template for runnable prototypes in `03-implementations/`.

### README.md Template

````markdown
# Service Name

Brief description of what this service demonstrates and its learning purpose.

## What You'll Learn

- Concept 1 in practice
- Concept 2 implementation
- Production considerations

## Architecture

```mermaid
graph TB
    Client[Client] --> API[REST API]
    API --> Service[Core Service]
    Service --> DB[(Database)]
    Service --> Cache[Cache]

    style Client fill:#e2e8f0
    style API fill:#2563eb,color:#fff
    style Service fill:#059669,color:#fff
    style DB fill:#6b7280,color:#fff
    style Cache fill:#dc2626,color:#fff
```
````

## Quick Start

### Using Docker (Recommended)

```bash
# Clone and navigate
git clone <repo>
cd 03-implementations/service-name

# Start services
docker-compose up -d

# Test the service
curl http://localhost:8080/health
```

### Local Development

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables
cp .env.example .env

# Run the service
python app.py
```

## API Documentation

### Endpoints

#### GET /health

Health check endpoint.

**Response:**

```json
{
  "status": "healthy",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

#### POST /api/resource

Create a new resource.

**Request:**

```json
{
  "field1": "value1",
  "field2": "value2"
}
```

**Response:**

```json
{
  "id": "123",
  "field1": "value1",
  "field2": "value2",
  "created_at": "2024-01-01T00:00:00Z"
}
```

## Configuration

### Environment Variables

- `PORT`: Service port (default: 8080)
- `DATABASE_URL`: Database connection string
- `CACHE_URL`: Cache connection string
- `LOG_LEVEL`: Logging level (default: INFO)

### Docker Compose Services

- `app`: Main application service
- `database`: PostgreSQL database
- `cache`: Redis cache
- `monitoring`: Prometheus metrics

## Performance Testing

### Load Testing

```bash
# Install dependencies
pip install locust

# Run load test
locust -f load_test.py --host=http://localhost:8080
```

### Benchmarks

- Throughput: X requests/second
- Latency: Y ms average
- Memory usage: Z MB

## Monitoring

### Metrics

- Request rate and latency
- Error rates
- Resource utilization
- Business metrics

### Dashboards

- Grafana dashboard: `http://localhost:3000`
- Prometheus metrics: `http://localhost:9090`

## Learning Exercises

### Basic Exercises

1. **Exercise 1:** Modify the API to add new functionality
2. **Exercise 2:** Add input validation and error handling
3. **Exercise 3:** Implement caching for improved performance

### Advanced Exercises

1. **Exercise 1:** Add authentication and authorization
2. **Exercise 2:** Implement rate limiting
3. **Exercise 3:** Add distributed tracing

## Production Considerations

### Deployment

- Container orchestration (Kubernetes)
- Service discovery
- Load balancing

### Observability

- Logging strategy
- Metrics collection
- Distributed tracing

### Security

- Input validation
- Authentication/authorization
- Data encryption

## Troubleshooting

### Common Issues

1. **Issue 1:** Description and solution
2. **Issue 2:** Description and solution

### Debugging

- Enable debug logging: `LOG_LEVEL=DEBUG`
- Check service health: `curl /health`
- View logs: `docker-compose logs -f app`

## Next Steps

- Deploy to cloud platform
- Add more advanced features
- Study related implementations
- Contribute improvements

````

## 🎯 Interview Prep Template

Use this template for interview preparation content in `04-interview-prep/`.

### Question Template
```markdown
# Question: [Question Title]

## Problem Statement

Clear statement of the system design problem.

### Requirements Clarification
- Clarifying question 1 → Answer
- Clarifying question 2 → Answer
- Clarifying question 3 → Answer

### Scale Estimation
- Users: X million
- Data: Y TB
- Requests: Z per second

## Solution Approach

### High-Level Architecture

```mermaid
graph TB
    Users[Users] --> System[System Components]
    System --> Storage[Storage Layer]

    style Users fill:#e2e8f0
    style System fill:#2563eb,color:#fff
    style Storage fill:#059669,color:#fff
````

### Step-by-Step Solution

#### Step 1: [Component Name]

- Purpose and functionality
- Technology choices
- Reasoning

#### Step 2: [Component Name]

- Purpose and functionality
- Technology choices
- Reasoning

## Deep Dive Topics

### Database Design

- Schema design
- Partitioning strategy
- Consistency requirements

### Scaling Strategy

- Horizontal vs vertical scaling
- Bottlenecks and solutions
- Performance optimizations

### Trade-offs Discussion

| Aspect      | Option A | Option B | Recommendation         |
| ----------- | -------- | -------- | ---------------------- |
| Consistency | Strong   | Eventual | Depends on use case    |
| Latency     | Low      | High     | Option A for real-time |

## Follow-up Questions

### Technical Deep Dives

1. **Question:** How would you handle X scenario?
   **Answer:** Approach and reasoning

2. **Question:** What if the system needs to support Y?
   **Answer:** Modifications needed

### Failure Scenarios

1. **Scenario:** Database goes down
   **Solution:** Failover strategy

2. **Scenario:** Network partition
   **Solution:** Partition tolerance approach

## Common Mistakes

### Mistake 1: [Description]

**Why it's wrong:** Explanation
**Correct approach:** Better solution

### Mistake 2: [Description]

**Why it's wrong:** Explanation
**Correct approach:** Better solution

## Evaluation Criteria

### What Interviewers Look For

- [ ] Clear problem understanding
- [ ] Systematic approach
- [ ] Trade-off analysis
- [ ] Scalability considerations
- [ ] Communication skills

### Scoring Rubric

- **Excellent (4/4):** Comprehensive solution with trade-offs
- **Good (3/4):** Solid solution with minor gaps
- **Average (2/4):** Basic solution, missing key components
- **Poor (1/4):** Incomplete or incorrect solution

## Practice Tips

### Preparation

- Review related systems
- Practice drawing diagrams
- Time yourself (45-60 minutes)

### During Interview

- Ask clarifying questions
- Start with high-level design
- Dive deep when asked
- Discuss trade-offs

## Related Questions

- [Similar Question 1](similar-question-1.md)
- [Similar Question 2](similar-question-2.md)
- [Foundation Topic](../../00-foundations/related-topic.md)

````

## 📈 Study Plan Template

Use this template for learning roadmaps in `05-study-plan/`.

### Weekly Plan Template
```markdown
# Week X: [Theme]

## Learning Objectives

By the end of this week, you will:
- Objective 1
- Objective 2
- Objective 3

## Prerequisites

- Prerequisite 1
- Prerequisite 2

## Daily Schedule

### Day 1: [Topic]
**Time:** 2-3 hours
**Focus:** Introduction and core concepts

#### Morning (1 hour)
- [ ] Read [Foundation Topic](../../00-foundations/topic.md)
- [ ] Review key terminology
- [ ] Complete concept quiz

#### Afternoon (1-2 hours)
- [ ] Study [System Example](../../02-hl-designs/system/)
- [ ] Analyze architecture decisions
- [ ] Practice explaining concepts

**Deliverable:** Summary of key concepts learned

### Day 2: [Topic]
**Time:** 2-3 hours
**Focus:** Hands-on implementation

#### Morning (1 hour)
- [ ] Review [LLD Example](../../01-ll-designs/system/)
- [ ] Understand algorithm and data structures
- [ ] Analyze complexity

#### Afternoon (1-2 hours)
- [ ] Implement solution in preferred language
- [ ] Test with different inputs
- [ ] Compare with provided solutions

**Deliverable:** Working implementation with tests

### [Continue for remaining days...]

## Weekly Assessment

### Knowledge Check
1. **Question 1:** Test understanding
2. **Question 2:** Application question
3. **Question 3:** Design question

### Practical Exercise
**Task:** Build a mini-system incorporating week's concepts
**Time:** 2-3 hours
**Deliverable:** Working prototype with documentation

## Reflection Questions

1. What was the most challenging concept this week?
2. How do the topics connect to previous learning?
3. What would you explain differently to someone else?
4. What questions do you still have?

## Next Week Preview

Brief overview of what's coming next and how it builds on this week's learning.

## Resources

### Required Reading
- [Resource 1](link) - Description
- [Resource 2](link) - Description

### Optional Deep Dives
- [Advanced Topic](link) - For curious learners
- [Research Paper](link) - Academic perspective

### Practice Problems
- [Problem Set](link) - Additional practice
- [Mock Interview](link) - Interview simulation
````

---

These templates ensure consistency across all content while providing flexibility for different types of learning materials. Remember to adapt them based on the specific needs of each topic while maintaining the overall structure and style guidelines.
