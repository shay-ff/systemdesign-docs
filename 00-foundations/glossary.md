# Glossary — Comprehensive Definitions for System Design

## Core Performance Metrics

**Availability**: Fraction of time the system is operational and accessible to users. Measured as uptime/(uptime + downtime). Example: 99.9% = 8.76 hours downtime per year.

**Throughput**: Number of operations the system can handle per unit time. Measured in RPS (Requests Per Second), QPS (Queries Per Second), or TPS (Transactions Per Second).

**Latency**: Time to complete a single operation from request initiation to response completion. Key metrics: P50 (median), P95 (95th percentile), P99 (99th percentile).

**Response Time**: Total time from user action to visible result, including network delays and client processing.

**Bandwidth**: Maximum rate of data transfer across a network connection, measured in bits per second (bps).

## Consistency and Reliability

**CAP Theorem**: In the presence of network partitions, distributed systems must choose between Consistency (all nodes see the same data) and Availability (system remains operational). Partition tolerance is mandatory in distributed systems.

**ACID Properties**: Strong transactional guarantees for databases:
- **Atomicity**: All operations in a transaction succeed or all fail
- **Consistency**: Database remains in valid state after transactions
- **Isolation**: Concurrent transactions don't interfere with each other
- **Durability**: Committed transactions survive system failures

**BASE Properties**: Alternative to ACID for distributed systems:
- **Basically Available**: System remains available despite failures
- **Soft State**: Data consistency is not guaranteed at all times
- **Eventual Consistency**: System will become consistent over time

**Strong Consistency**: All nodes return the same data simultaneously. Reads always return the most recent write.

**Eventual Consistency**: System will become consistent over time, but immediate consistency is not guaranteed. Suitable for systems where temporary inconsistency is acceptable.

**Weak Consistency**: No guarantees about when all nodes will see the same data. Best effort approach.

## Caching Strategies

**Cache-Aside (Lazy Loading)**: Application manages cache explicitly. On cache miss, app loads data from database and updates cache.

**Write-Through**: Data is written to cache and database simultaneously. Ensures cache is always consistent but adds write latency.

**Write-Behind (Write-Back)**: Data is written to cache immediately and to database asynchronously. Improves write performance but risks data loss.

**Cache Invalidation**: Process of removing or updating stale data in cache. Strategies include TTL (Time To Live), manual invalidation, and event-driven invalidation.

**Cache Stampede**: Situation where multiple requests simultaneously try to regenerate the same cache entry, causing database overload.

## Scaling and Distribution

**CDN (Content Delivery Network)**: Geographically distributed network of edge servers that cache static content closer to users, reducing latency and server load.

**Sharding (Horizontal Partitioning)**: Splitting data across multiple databases based on a partition key (e.g., user_id, geographic region). Enables scaling writes and storage.

**Replication**: Creating copies of data across multiple nodes to improve read performance, availability, and fault tolerance. Types include master-slave and master-master.

**Load Balancing**: Distributing incoming requests across multiple servers. Algorithms include round-robin, least connections, weighted round-robin, and consistent hashing.

**Horizontal Scaling (Scale Out)**: Adding more servers to handle increased load. Requires stateless application design.

**Vertical Scaling (Scale Up)**: Increasing the power of existing servers (CPU, RAM, storage). Simpler but has physical limits.

## Reliability and Fault Tolerance

**Idempotency**: Property where performing an operation multiple times has the same effect as performing it once. Critical for retry mechanisms and distributed systems.

**Circuit Breaker**: Design pattern that prevents cascading failures by stopping calls to a failing service. States: Closed (normal), Open (failing), Half-Open (testing recovery).

**Backpressure**: Mechanism to handle situations where producers generate data faster than consumers can process it. Strategies include dropping requests, queuing, or slowing down producers.

**Graceful Degradation**: System's ability to maintain limited functionality when some components fail, rather than complete system failure.

**Bulkhead Pattern**: Isolating critical resources to prevent failures in one area from affecting others, like compartments in a ship.

**Timeout**: Maximum time to wait for an operation to complete before considering it failed. Prevents indefinite blocking.

**Retry Logic**: Mechanism to automatically retry failed operations. Often combined with exponential backoff and jitter.

## Service Architecture

**Microservices**: Architectural approach where applications are built as a collection of small, independent services that communicate over well-defined APIs.

**Monolith**: Traditional architecture where all functionality is deployed as a single unit. Simpler to develop and deploy initially.

**Service Mesh**: Infrastructure layer that handles service-to-service communication, providing features like load balancing, service discovery, and security.

**API Gateway**: Single entry point for all client requests, handling routing, authentication, rate limiting, and request/response transformation.

**Event-Driven Architecture**: Design pattern where services communicate through events, enabling loose coupling and asynchronous processing.

## Data Storage

**RDBMS (Relational Database Management System)**: Traditional databases that store data in tables with relationships. Examples: PostgreSQL, MySQL, Oracle.

**NoSQL**: Non-relational databases designed for specific data models. Types include document (MongoDB), key-value (Redis), column-family (Cassandra), and graph (Neo4j).

**Data Lake**: Storage repository that holds vast amounts of raw data in its native format until needed.

**Data Warehouse**: Central repository of integrated data from multiple sources, optimized for analysis and reporting.

**OLTP (Online Transaction Processing)**: Systems optimized for handling large numbers of short online transactions with emphasis on fast query processing.

**OLAP (Online Analytical Processing)**: Systems optimized for complex analytical queries and data mining operations.

## Messaging and Communication

**Message Queue**: Asynchronous communication mechanism where messages are stored in a queue until processed by consumers. Examples: RabbitMQ, Apache Kafka.

**Pub/Sub (Publish/Subscribe)**: Messaging pattern where publishers send messages to topics, and subscribers receive messages from topics they're interested in.

**Event Sourcing**: Pattern where state changes are stored as a sequence of events, allowing system state to be reconstructed by replaying events.

**CQRS (Command Query Responsibility Segregation)**: Pattern that separates read and write operations, allowing independent optimization of each.

## Performance and Monitoring

**SLO (Service Level Objective)**: Internal target for service performance (e.g., 99.9% availability, <100ms latency for 95% of requests).

**SLA (Service Level Agreement)**: Contractual commitment to customers about service performance, often with penalties for non-compliance.

**SLI (Service Level Indicator)**: Quantitative measure of service performance (e.g., request latency, error rate, throughput).

**APM (Application Performance Monitoring)**: Tools and practices for monitoring application performance, identifying bottlenecks, and troubleshooting issues.

**Observability**: System's ability to be understood through its outputs (logs, metrics, traces). Enables effective debugging and optimization.

## Security

**Authentication**: Process of verifying the identity of a user or system.

**Authorization**: Process of determining what actions an authenticated user is allowed to perform.

**OAuth**: Open standard for access delegation, commonly used for token-based authentication.

**JWT (JSON Web Token)**: Compact, URL-safe means of representing claims between two parties, often used for authentication.

**Rate Limiting**: Controlling the rate of requests a user can make to prevent abuse and ensure fair usage.

**DDoS (Distributed Denial of Service)**: Attack that attempts to make a service unavailable by overwhelming it with traffic from multiple sources.

## Development and Deployment

**Blue-Green Deployment**: Deployment strategy that maintains two identical production environments, switching traffic between them for zero-downtime deployments.

**Canary Deployment**: Gradual rollout strategy where new versions are deployed to a small subset of users before full deployment.

**Feature Flags**: Technique that allows enabling or disabling features without deploying new code, enabling safer releases and A/B testing.

**Containerization**: Packaging applications with their dependencies into lightweight, portable containers. Docker is the most popular containerization platform.

**Orchestration**: Automated management of containerized applications, including deployment, scaling, and networking. Kubernetes is the leading orchestration platform.
