# System Design Docs

A practical guide to system design with explanations, diagrams, runnable
implementations, and interview preparation.

## Contents

- **Foundations**: Core concepts, scalability, caching, databases, and load
  balancing.
- **Low-Level Design**: Classic object-oriented design problems and reusable
  components such as caches, rate limiters, message queues, and bloom filters.
- **High-Level Design**: Complete designs for systems such as chat, payments,
  streaming, ride sharing, URL shortening, and model serving.
- **Implementations**: Runnable services with APIs, Docker configurations, and
  benchmarks.
- **Interview Preparation**: Frameworks, common questions, machine coding
  guidance, and mock interviews.
- **Study Plans**: Structured four-week and six-week learning plans.

See the [navigation guide](NAVIGATION.md) for the full index.

## Learning Paths

### New to System Design

1. Read [Core Concepts](00-foundations/concepts.md).
2. Build an [LRU cache](01-ll-designs/lru_cache/README.md).
3. Study a [real system design](02-hl-designs/twitter_clone/README.md).

### Interview Preparation

1. Read the [Interview Framework](04-interview-prep/frameworks.md).
2. Practice the [Common Questions](04-interview-prep/most_asked_questions.md).
3. Use the [Mock Interview scenarios](04-interview-prep/mock-interviews/README.md).

### Hands-On Practice

1. Review a component in [Low-Level Design](01-ll-designs/README.md).
2. Run an example from [Runnable Examples](03-implementations/README.md).
3. Use the Docker configuration in the relevant implementation directory.

### Structured Study

Follow the [six-week study plan](05-study-plan/study_plan.md), or use the
[four-week plan](05-study-plan/crunch-plan.md) for a shorter interview schedule.

## Running Examples

### Low-Level Design

```bash
# LRU Cache (Python)
cd 01-ll-designs/lru_cache/solutions/python
python lru_cache.py

# Rate Limiter (Go)
cd 01-ll-designs/rate_limiter/solutions/go
go run token_bucket.go

# Consistent Hashing (Java)
cd 01-ll-designs/consistent_hashing/solutions/java
javac ConsistentHash.java && java ConsistentHashDemo
```

### Services

```bash
# Cache Server
cd 03-implementations/cache-server
docker compose up

# Rate Limiter Service
cd 03-implementations/rate-limiter-service
docker compose up

# Message Broker
cd 03-implementations/simple-message-broker
docker compose up
```

## Visual Guides

The repository uses Mermaid for lightweight diagrams and PlantUML for detailed
class and sequence diagrams. See the [diagram guide](assets/diagrams/README.md)
and the reusable patterns in `assets/diagrams/templates/`.

The visual guides are included on the website because they explain structures
and flows that are difficult to convey in prose alone.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for content, code, diagram, and pull
request guidelines.

## License

This project is available under the [MIT License](LICENSE).
