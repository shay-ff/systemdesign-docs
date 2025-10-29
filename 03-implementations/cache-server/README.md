# Cache Server Implementation

A high-performance REST API cache service built with FastAPI and Redis backend, demonstrating key caching concepts from system design fundamentals.

## Features

- **REST API**: Clean HTTP interface for cache operations
- **Redis Backend**: Distributed caching with configurable eviction policies
- **Multiple Eviction Policies**: LRU, LFU, TTL-based expiration
- **Performance Monitoring**: Built-in metrics and benchmarking
- **Docker Support**: Containerized deployment with docker-compose

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client App    │───▶│  Cache Server   │───▶│   Redis Store   │
│                 │    │   (FastAPI)     │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## API Endpoints

- `GET /cache/{key}` - Retrieve cached value
- `PUT /cache/{key}` - Store key-value pair with optional TTL
- `DELETE /cache/{key}` - Remove specific key
- `DELETE /cache` - Clear entire cache
- `GET /stats` - Cache statistics and performance metrics
- `GET /health` - Health check endpoint

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Start the cache server and Redis
docker-compose up -d

# Test the API
curl -X PUT "http://localhost:8000/cache/test" \
     -H "Content-Type: application/json" \
     -d '{"value": "Hello World", "ttl": 300}'

curl "http://localhost:8000/cache/test"

# Run functionality tests
python test_cache.py

# Run performance benchmarks
cd benchmarks && python benchmark.py
```

### Local Development

```bash
# Install dependencies
pip install -r requirements.txt

# Start Redis (required)
redis-server

# Run the cache server
python app.py

# In another terminal, run tests
python test_cache.py
```

### Using Make Commands

```bash
# See all available commands
make help

# Start with docker-compose
make docker-up

# Run tests
make test

# Run benchmarks (after installing benchmark deps)
make benchmark-install
make benchmark

# Check server health
make health
```

## Configuration

Environment variables:
- `REDIS_HOST`: Redis server host (default: localhost)
- `REDIS_PORT`: Redis server port (default: 6379)
- `REDIS_DB`: Redis database number (default: 0)
- `DEFAULT_TTL`: Default TTL in seconds (default: 3600)
- `MAX_MEMORY_POLICY`: Redis eviction policy (default: allkeys-lru)

## Performance Benchmarks

See `benchmarks/` directory for performance testing scripts and results.

## Related System Design Concepts

This implementation demonstrates:
- **Caching Strategies**: Write-through, write-around patterns
- **Eviction Policies**: LRU, LFU, TTL-based expiration
- **Distributed Caching**: Redis as external cache store
- **API Design**: RESTful interface for cache operations
- **Performance Monitoring**: Metrics collection and analysis

## References

- [00-foundations/caching-strategies.md](../../00-foundations/caching-strategies.md)
- [01-ll-designs/lru_cache/](../../01-ll-designs/lru_cache/)