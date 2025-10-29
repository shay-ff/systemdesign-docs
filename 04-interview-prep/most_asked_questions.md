# Comprehensive System Design Interview Questions

This document contains 50+ carefully curated system design interview questions, organized by difficulty level and system type. Each question includes estimated time requirements, complexity ratings, and follow-up variations.

## Legend
- **Time**: Estimated interview duration
- **Complexity**: Technical difficulty (1-5 scale)
- **Type**: System category
- **Level**: Interview level (Junior/Mid/Senior/Staff)

---

## Beginner Level Questions (0-2 Years Experience)

### 1. Design a URL Shortener (like bit.ly)
- **Time**: 45 minutes
- **Complexity**: 2/5
- **Type**: Web Service
- **Level**: Junior/Mid

**Core Question**: Design a service that takes long URLs and returns shortened versions.

**Follow-up Questions**:
- How would you handle custom aliases?
- What happens if the same URL is shortened multiple times?
- How would you implement analytics (click tracking)?
- How would you handle URL expiration?

**Key Focus Areas**: Basic database design, URL encoding algorithms, caching

---

### 2. Design a Basic Chat Application
- **Time**: 45 minutes
- **Complexity**: 2/5
- **Type**: Real-time Communication
- **Level**: Junior/Mid

**Core Question**: Design a simple chat application for 1-on-1 messaging.

**Follow-up Questions**:
- How would you show online/offline status?
- How would you handle message delivery confirmation?
- What about message history storage?
- How would you handle multiple devices for the same user?

**Key Focus Areas**: WebSocket connections, message queuing, basic real-time systems

---

### 3. Design a Simple Cache System
- **Time**: 30 minutes
- **Complexity**: 2/5
- **Type**: Storage System
- **Level**: Junior

**Core Question**: Design an in-memory cache with get/put operations.

**Follow-up Questions**:
- What eviction policy would you use?
- How would you handle cache misses?
- What about thread safety?
- How would you implement TTL (time-to-live)?

**Key Focus Areas**: Data structures, eviction policies, concurrency

---

### 4. Design a Basic Rate Limiter
- **Time**: 30 minutes
- **Complexity**: 2/5
- **Type**: Infrastructure
- **Level**: Junior/Mid

**Core Question**: Design a system to limit API requests per user.

**Follow-up Questions**:
- What algorithm would you use (token bucket vs sliding window)?
- How would you handle distributed rate limiting?
- What about different rate limits for different API endpoints?
- How would you handle rate limit exceeded responses?

**Key Focus Areas**: Rate limiting algorithms, distributed systems basics

---

### 5. Design a Simple Notification System
- **Time**: 45 minutes
- **Complexity**: 2/5
- **Type**: Communication System
- **Level**: Junior/Mid

**Core Question**: Design a system to send push notifications to mobile devices.

**Follow-up Questions**:
- How would you handle different notification types (email, SMS, push)?
- What about notification preferences and opt-outs?
- How would you ensure delivery reliability?
- What about notification scheduling?

**Key Focus Areas**: Message queuing, external service integration, reliability

---

## Intermediate Level Questions (2-5 Years Experience)

### 6. Design Instagram/Twitter Feed
- **Time**: 60 minutes
- **Complexity**: 3/5
- **Type**: Social Media
- **Level**: Mid

**Core Question**: Design a social media feed that shows posts from followed users.

**Follow-up Questions**:
- How would you handle celebrity users with millions of followers?
- What about feed ranking and personalization?
- How would you implement real-time updates?
- What about media content (images/videos) in posts?

**Key Focus Areas**: Fan-out strategies, timeline generation, content delivery

---

### 7. Design a Video Streaming Service (like YouTube)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Media Streaming
- **Level**: Mid/Senior

**Core Question**: Design a platform for uploading, processing, and streaming videos.

**Follow-up Questions**:
- How would you handle video transcoding and multiple resolutions?
- What about global content delivery?
- How would you implement video recommendations?
- What about live streaming capabilities?

**Key Focus Areas**: CDN, video processing, distributed storage, recommendation systems

---

### 8. Design a Ride-Sharing Service (like Uber)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Location-based Service
- **Level**: Mid/Senior

**Core Question**: Design a system to match riders with drivers in real-time.

**Follow-up Questions**:
- How would you handle location updates and tracking?
- What about surge pricing during high demand?
- How would you optimize driver-rider matching?
- What about payment processing and trip history?

**Key Focus Areas**: Geospatial indexing, real-time matching, location services

---

### 9. Design a Search Engine (like Google)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Search System
- **Level**: Mid/Senior

**Core Question**: Design a web search engine that crawls, indexes, and serves search results.

**Follow-up Questions**:
- How would you crawl and index billions of web pages?
- What about ranking search results?
- How would you handle real-time search suggestions?
- What about handling different types of content (images, videos, news)?

**Key Focus Areas**: Web crawling, indexing, ranking algorithms, distributed processing

---

### 10. Design a Message Queue System (like Apache Kafka)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Mid/Senior

**Core Question**: Design a distributed message queue for high-throughput applications.

**Follow-up Questions**:
- How would you ensure message ordering and delivery guarantees?
- What about handling consumer groups and partitioning?
- How would you implement message retention and cleanup?
- What about handling producer and consumer failures?

**Key Focus Areas**: Distributed systems, message ordering, fault tolerance

---

### 11. Design an E-commerce Platform (like Amazon)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: E-commerce
- **Level**: Mid/Senior

**Core Question**: Design an online marketplace with product catalog, shopping cart, and checkout.

**Follow-up Questions**:
- How would you handle inventory management across multiple sellers?
- What about payment processing and fraud detection?
- How would you implement product search and recommendations?
- What about order fulfillment and tracking?

**Key Focus Areas**: Microservices, payment systems, inventory management, search

---

### 12. Design a Distributed Cache (like Redis Cluster)
- **Time**: 45 minutes
- **Complexity**: 3/5
- **Type**: Storage System
- **Level**: Mid

**Core Question**: Design a distributed caching system that can scale horizontally.

**Follow-up Questions**:
- How would you handle data partitioning and replication?
- What about cache consistency across nodes?
- How would you handle node failures and recovery?
- What about cache warming and cold start problems?

**Key Focus Areas**: Consistent hashing, replication, fault tolerance

---

### 13. Design a Content Delivery Network (CDN)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Mid/Senior

**Core Question**: Design a global CDN to serve static content with low latency.

**Follow-up Questions**:
- How would you determine optimal cache locations?
- What about cache invalidation and content updates?
- How would you handle different content types (static vs dynamic)?
- What about DDoS protection and security?

**Key Focus Areas**: Geographic distribution, caching strategies, content routing

---

### 14. Design a Real-time Analytics System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Analytics
- **Level**: Mid/Senior

**Core Question**: Design a system to process and analyze streaming data in real-time.

**Follow-up Questions**:
- How would you handle high-volume data ingestion?
- What about real-time aggregations and windowing?
- How would you store and query historical data?
- What about handling late-arriving data and corrections?

**Key Focus Areas**: Stream processing, time-series data, real-time aggregation

---

### 15. Design a File Storage System (like Dropbox)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Storage System
- **Level**: Mid/Senior

**Core Question**: Design a cloud file storage and synchronization service.

**Follow-up Questions**:
- How would you handle file versioning and conflict resolution?
- What about file sharing and permissions?
- How would you implement efficient file synchronization?
- What about handling large files and resumable uploads?

**Key Focus Areas**: File synchronization, conflict resolution, distributed storage

---

## Advanced Level Questions (5+ Years Experience)

### 16. Design a Global Payment System (like PayPal)
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Financial System
- **Level**: Senior/Staff

**Core Question**: Design a global payment processing system handling millions of transactions.

**Follow-up Questions**:
- How would you ensure ACID properties across distributed transactions?
- What about fraud detection and risk management?
- How would you handle different currencies and exchange rates?
- What about regulatory compliance across different countries?

**Key Focus Areas**: Distributed transactions, financial compliance, fraud detection

---

### 17. Design a Stock Trading System
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Financial System
- **Level**: Senior/Staff

**Core Question**: Design a high-frequency trading system with microsecond latency requirements.

**Follow-up Questions**:
- How would you ensure order matching fairness and speed?
- What about handling market data feeds and real-time updates?
- How would you implement risk management and circuit breakers?
- What about audit trails and regulatory reporting?

**Key Focus Areas**: Low-latency systems, order matching, financial regulations

---

### 18. Design a Multi-tenant SaaS Platform
- **Time**: 75 minutes
- **Complexity**: 4/5
- **Type**: Platform
- **Level**: Senior

**Core Question**: Design a SaaS platform that serves multiple customers with data isolation.

**Follow-up Questions**:
- How would you handle data isolation between tenants?
- What about custom configurations per tenant?
- How would you implement billing and usage tracking?
- What about tenant-specific scaling and performance?

**Key Focus Areas**: Multi-tenancy, data isolation, resource management

---

### 19. Design a Distributed Database (like Cassandra)
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Database System
- **Level**: Senior/Staff

**Core Question**: Design a distributed NoSQL database with high availability.

**Follow-up Questions**:
- How would you handle data partitioning and replication?
- What about consistency models and conflict resolution?
- How would you implement distributed transactions?
- What about handling node failures and data recovery?

**Key Focus Areas**: Distributed consensus, CAP theorem, data consistency

---

### 20. Design a Machine Learning Platform (like AWS SageMaker)
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: ML Platform
- **Level**: Senior/Staff

**Core Question**: Design a platform for training and serving machine learning models at scale.

**Follow-up Questions**:
- How would you handle model training on distributed infrastructure?
- What about model versioning and A/B testing?
- How would you implement real-time model serving with low latency?
- What about handling different ML frameworks and environments?

**Key Focus Areas**: Distributed computing, model lifecycle, real-time inference

---

## Specialized System Types

### 21. Design a Monitoring and Alerting System (like Datadog)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle metric aggregation across thousands of servers?
- What about anomaly detection and intelligent alerting?
- How would you implement dashboard queries with low latency?

---

### 22. Design a Logging System (like ELK Stack)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle log ingestion from thousands of services?
- What about log parsing, indexing, and search?
- How would you implement log retention and archival?

---

### 23. Design a Configuration Management System
- **Time**: 45 minutes
- **Complexity**: 3/5
- **Type**: Infrastructure
- **Level**: Mid

**Follow-up Questions**:
- How would you handle configuration versioning and rollbacks?
- What about real-time configuration updates?
- How would you ensure configuration consistency across environments?

---

### 24. Design a Service Discovery System
- **Time**: 45 minutes
- **Complexity**: 3/5
- **Type**: Infrastructure
- **Level**: Mid

**Follow-up Questions**:
- How would you handle service registration and health checking?
- What about load balancing and failover?
- How would you implement service mesh capabilities?

---

### 25. Design a Backup and Disaster Recovery System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Senior

**Follow-up Questions**:
- How would you ensure backup consistency across distributed systems?
- What about cross-region replication and failover?
- How would you implement point-in-time recovery?

---

## Domain-Specific Questions

### 26. Design a Healthcare Management System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Healthcare
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you ensure HIPAA compliance and data privacy?
- What about integrating with existing hospital systems?
- How would you handle emergency access scenarios?

---

### 27. Design an Online Education Platform
- **Time**: 60 minutes
- **Complexity**: 3/5
- **Type**: Education
- **Level**: Mid

**Follow-up Questions**:
- How would you handle live streaming of classes?
- What about assignment submission and grading?
- How would you implement progress tracking and analytics?

---

### 28. Design a Gaming Platform (like Steam)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Gaming
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle game downloads and updates?
- What about multiplayer matchmaking?
- How would you implement in-game purchases and DRM?

---

### 29. Design a Food Delivery System (like DoorDash)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Logistics
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you optimize delivery routing and timing?
- What about real-time order tracking?
- How would you handle peak demand and driver allocation?

---

### 30. Design a Hotel Booking System (like Booking.com)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Travel
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle inventory management and overbooking?
- What about dynamic pricing and availability?
- How would you implement search and filtering?

---

## Emerging Technology Questions

### 31. Design a Blockchain-based System
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Blockchain
- **Level**: Senior

**Follow-up Questions**:
- How would you handle consensus mechanisms?
- What about scalability and transaction throughput?
- How would you implement smart contracts?

---

### 32. Design an IoT Data Processing Platform
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: IoT
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle millions of device connections?
- What about data aggregation and real-time processing?
- How would you implement device management and updates?

---

### 33. Design a Serverless Computing Platform (like AWS Lambda)
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Platform
- **Level**: Senior/Staff

**Follow-up Questions**:
- How would you handle function cold starts and warm-up?
- What about resource allocation and scaling?
- How would you implement function orchestration?

---

### 34. Design an Edge Computing Network
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Infrastructure
- **Level**: Senior/Staff

**Follow-up Questions**:
- How would you handle workload placement and migration?
- What about data synchronization between edge and cloud?
- How would you implement edge-specific optimizations?

---

### 35. Design a Real-time Collaboration Platform (like Google Docs)
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Collaboration
- **Level**: Senior

**Follow-up Questions**:
- How would you handle operational transformation for concurrent edits?
- What about conflict resolution and version control?
- How would you implement real-time cursor and selection sharing?

---

## System Integration Questions

### 36. Design a Microservices Architecture Migration
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Architecture
- **Level**: Senior

**Follow-up Questions**:
- How would you decompose a monolithic application?
- What about data migration and consistency?
- How would you handle service communication and discovery?

---

### 37. Design a Multi-Cloud Strategy
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Senior

**Follow-up Questions**:
- How would you handle data synchronization across clouds?
- What about vendor lock-in and portability?
- How would you implement disaster recovery across providers?

---

### 38. Design an API Gateway
- **Time**: 45 minutes
- **Complexity**: 3/5
- **Type**: Infrastructure
- **Level**: Mid

**Follow-up Questions**:
- How would you handle authentication and authorization?
- What about rate limiting and throttling?
- How would you implement API versioning and routing?

---

### 39. Design a Data Pipeline (ETL/ELT)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Data Processing
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle data quality and validation?
- What about handling schema evolution?
- How would you implement data lineage and monitoring?

---

### 40. Design a Feature Flag System
- **Time**: 45 minutes
- **Complexity**: 3/5
- **Type**: Infrastructure
- **Level**: Mid

**Follow-up Questions**:
- How would you handle real-time flag updates?
- What about user targeting and segmentation?
- How would you implement flag analytics and rollback?

---

## Performance and Scale Questions

### 41. Design a System for 1 Billion Users
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Scale
- **Level**: Senior/Staff

**Follow-up Questions**:
- How would you handle global distribution and latency?
- What about data partitioning and sharding strategies?
- How would you implement gradual rollouts and canary deployments?

---

### 42. Design a Low-Latency Trading System
- **Time**: 75 minutes
- **Complexity**: 5/5
- **Type**: Performance
- **Level**: Senior/Staff

**Follow-up Questions**:
- How would you achieve microsecond-level latency?
- What about hardware optimizations and network topology?
- How would you handle market data processing?

---

### 43. Design a High-Availability System (99.99% uptime)
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Reliability
- **Level**: Senior

**Follow-up Questions**:
- How would you eliminate single points of failure?
- What about graceful degradation and circuit breakers?
- How would you implement chaos engineering and testing?

---

### 44. Design a Global Content Distribution System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Infrastructure
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you optimize content placement and routing?
- What about handling dynamic vs static content?
- How would you implement edge computing capabilities?

---

### 45. Design a Time-Series Database
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Database
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you optimize for time-based queries?
- What about data compression and retention policies?
- How would you handle real-time ingestion and querying?

---

## Security-Focused Questions

### 46. Design a Zero-Trust Security Architecture
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Security
- **Level**: Senior

**Follow-up Questions**:
- How would you implement identity verification at every layer?
- What about network segmentation and micro-perimeters?
- How would you handle continuous security monitoring?

---

### 47. Design a Secrets Management System
- **Time**: 45 minutes
- **Complexity**: 3/5
- **Type**: Security
- **Level**: Mid

**Follow-up Questions**:
- How would you handle secret rotation and versioning?
- What about access control and audit logging?
- How would you implement secret injection into applications?

---

### 48. Design a DDoS Protection System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Security
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you detect and mitigate different attack types?
- What about legitimate traffic preservation?
- How would you implement rate limiting and traffic shaping?

---

### 49. Design an Identity and Access Management System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Security
- **Level**: Mid/Senior

**Follow-up Questions**:
- How would you handle single sign-on (SSO) across services?
- What about role-based access control (RBAC)?
- How would you implement multi-factor authentication?

---

### 50. Design a Data Privacy and Compliance System
- **Time**: 60 minutes
- **Complexity**: 4/5
- **Type**: Security/Compliance
- **Level**: Senior

**Follow-up Questions**:
- How would you handle GDPR right to be forgotten?
- What about data classification and encryption?
- How would you implement audit trails and compliance reporting?

---

## Bonus: System Design Variations

### 51. Design a System with Specific Constraints
- **Constraint Examples**:
  - Design Twitter but for a country with poor internet connectivity
  - Design Netflix but with 90% cost reduction requirement
  - Design Uber but for autonomous vehicles only
  - Design Instagram but with end-to-end encryption

### 52. Design a System Integration Challenge
- **Integration Examples**:
  - Merge two competing social media platforms
  - Integrate acquired company's user base and data
  - Design backward compatibility for major API changes
  - Handle real-time migration between database systems

---

## Question Selection Strategy

### For 45-minute interviews:
- Choose 1 beginner or intermediate question
- Focus on core system design principles
- Allow time for one major follow-up

### For 60-minute interviews:
- Choose 1 intermediate or advanced question
- Include 2-3 follow-up questions
- Allow time for deep dives into specific areas

### For 75-minute interviews:
- Choose 1 advanced question or 2 related questions
- Include multiple follow-up scenarios
- Allow time for alternative approaches and trade-offs

### Progressive Interview Strategy:
1. **Phone Screen**: Basic cache or rate limiter (30 min)
2. **Technical Round 1**: Social media feed or messaging (60 min)
3. **Technical Round 2**: Video streaming or search engine (60 min)
4. **Final Round**: Payment system or distributed database (75 min)