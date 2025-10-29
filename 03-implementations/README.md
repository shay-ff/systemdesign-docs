# Production-Ready Implementations — Runnable Microservices

> 🧭 **Navigation**: [← High-Level Designs](../02-hl-designs/) | [📍 Full Navigation](../NAVIGATION.md) | [Next: Interview Prep →](../04-interview-prep/)

Bridge the gap between theory and practice with production-ready microservice implementations. Each service demonstrates key system design concepts with Docker containerization, REST APIs, and performance benchmarks.

## 🚀 Available Services

### Core Infrastructure Services
- **[Cache Server](cache-server/)** — REST API cache service with Redis backend and configurable eviction policies
- **[Rate Limiter Service](rate-limiter-service/)** — Standalone rate limiting with multiple algorithms and distributed support
- **[Simple Message Broker](simple-message-broker/)** — Pub/sub message broker with topic routing and WebSocket support
- **[Distributed Lock](distributed-lock/)** — Distributed locking service with timeout and deadlock detection

## 🏗️ Service Architecture

Each implementation includes:

### 📦 **Production Features**
- **Docker Containerization** — Ready-to-deploy containers with docker-compose
- **REST API** — Well-documented HTTP interfaces with OpenAPI specs
- **Health Checks** — Monitoring endpoints for service health
- **Configuration** — Environment-based configuration management
- **Logging** — Structured logging with different levels

### 🔧 **Development Tools**
- **Local Development** — Easy setup with docker-compose
- **API Documentation** — Interactive API docs and examples
- **Load Testing** — Performance testing scripts and benchmarks
- **Monitoring** — Metrics collection and dashboards

### 🧪 **Testing & Validation**
- **Unit Tests** — Core functionality testing
- **Integration Tests** — End-to-end service testing
- **Performance Benchmarks** — Load testing and performance metrics
- **Correctness Tests** — Distributed system correctness validation

## 🚀 Quick Start

### Prerequisites
- **Docker & Docker Compose** — For containerized deployment
- **Python 3.8+** — For Python-based services
- **Go 1.19+** — For Go-based services
- **Redis** — For distributed state (included in docker-compose)

### Run Any Service
```bash
# Choose a service
cd cache-server/  # or rate-limiter-service/, simple-message-broker/, distributed-lock/

# Start with Docker Compose
docker-compose up

# Or run locally (check service README for specific instructions)
```

## 📋 Service Details

### [Cache Server](cache-server/)
**Technology**: Python (Flask) + Redis  
**Demonstrates**: Caching patterns, eviction policies, REST APIs

```bash
cd cache-server/
docker-compose up
curl http://localhost:5000/cache/mykey -X PUT -d '{"value": "hello"}'
curl http://localhost:5000/cache/mykey
```

**Key Features**:
- Multiple eviction policies (LRU, LFU, TTL)
- Configurable cache size and TTL
- Performance metrics and monitoring
- Load testing scripts included

### [Rate Limiter Service](rate-limiter-service/)
**Technology**: Python (FastAPI) + Redis  
**Demonstrates**: Rate limiting algorithms, distributed coordination

```bash
cd rate-limiter-service/
docker-compose up
curl http://localhost:8000/check -H "X-User-ID: user123"
```

**Key Features**:
- Token bucket and sliding window algorithms
- Per-user and global rate limiting
- Configurable limits and time windows
- Real-time metrics and monitoring

### [Simple Message Broker](simple-message-broker/)
**Technology**: Go + WebSockets  
**Demonstrates**: Pub/sub patterns, real-time communication

```bash
cd simple-message-broker/
docker-compose up
# Use WebSocket client or HTTP API to publish/subscribe
```

**Key Features**:
- Topic-based message routing
- WebSocket and HTTP interfaces
- Message persistence and replay
- Connection management and scaling

### [Distributed Lock](distributed-lock/)
**Technology**: Go + Redis/etcd  
**Demonstrates**: Distributed coordination, consensus

```bash
cd distributed-lock/
docker-compose up
curl http://localhost:8080/lock -X POST -d '{"resource": "shared-resource", "ttl": 30}'
```

**Key Features**:
- Lock acquisition with timeout
- Automatic lock renewal
- Deadlock detection and prevention
- Client libraries for multiple languages

## 🔄 Integration with Learning Path

### Connection to Theory
Each implementation demonstrates concepts from:
- **[Foundations](../00-foundations/)**: Scalability, caching, consistency patterns
- **[Low-Level Designs](../01-ll-designs/)**: Data structures and algorithms in practice
- **[High-Level Designs](../02-hl-designs/)**: System architecture patterns

### Real-World Applications
- **Cache Server** → Used in [Netflix](../02-hl-designs/netflix_streaming/), [Twitter](../02-hl-designs/twitter_clone/) designs
- **Rate Limiter** → Essential for [API gateways](../02-hl-designs/uber_system/) and [user-facing services](../02-hl-designs/url_shortener/)
- **Message Broker** → Core component in [event-driven architectures](../02-hl-designs/uber_system/)
- **Distributed Lock** → Coordination primitive for [distributed systems](../02-hl-designs/youtube/)

## 📊 Performance & Monitoring

### Benchmarking
Each service includes performance testing:
```bash
# Cache Server
cd cache-server/benchmarks/
python benchmark.py

# Rate Limiter Service  
cd rate-limiter-service/load-test/
python load_test.py

# Message Broker
cd simple-message-broker/load-test/
go run load_test.go
```

### Monitoring
Services include:
- **Prometheus Metrics** — Performance and business metrics
- **Health Endpoints** — Service status and dependencies
- **Structured Logging** — JSON logs with correlation IDs
- **Grafana Dashboards** — Visual monitoring (where applicable)

## 🛠️ Development & Customization

### Local Development
```bash
# Start dependencies only
docker-compose up redis  # or other dependencies

# Run service locally for development
# (Check individual service README for specific commands)
```

### Configuration
Services use environment variables for configuration:
- **Database connections** — Redis, PostgreSQL endpoints
- **Service parameters** — Cache size, rate limits, timeouts
- **Feature flags** — Enable/disable specific features
- **Logging levels** — Debug, info, warn, error

### Extending Services
- **Add new endpoints** — Follow existing API patterns
- **Implement new algorithms** — Rate limiting, caching strategies
- **Add persistence** — Database integration for durability
- **Scale horizontally** — Load balancer and service discovery

## 🎯 Learning Objectives

After working with these implementations, you'll understand:

### Technical Skills
- **Microservice Architecture** — Service boundaries and communication
- **API Design** — RESTful interfaces and documentation
- **Containerization** — Docker and orchestration basics
- **Distributed Systems** — Coordination and consistency challenges

### System Design Concepts
- **Caching Strategies** — When and how to cache effectively
- **Rate Limiting** — Protecting services from overload
- **Message Patterns** — Asynchronous communication design
- **Distributed Coordination** — Consensus and locking mechanisms

## 🎯 Next Steps

After exploring implementations:

- **Interview Practice**: Use these as examples in [system design interviews](../04-interview-prep/)
- **Advanced Concepts**: Study [high-level designs](../02-hl-designs/) that use these components
- **Structured Learning**: Follow the [study plan](../05-study-plan/) for comprehensive coverage
- **Contribute**: Add new services or enhance existing ones via [contributing guidelines](../CONTRIBUTING.md)

## 🔗 External Resources

- **Docker Documentation** — Container best practices
- **API Design Guidelines** — REST and OpenAPI standards
- **Microservice Patterns** — Service design and communication
- **Monitoring & Observability** — Prometheus, Grafana, logging

---

*💻 **Pro Tip**: Start by running the services locally, then dive into the code to understand implementation details. Try modifying configurations to see how behavior changes!*