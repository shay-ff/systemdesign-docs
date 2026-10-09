# Rate Limiter Service

A standalone rate limiting service implementing multiple algorithms with Redis backend for distributed rate limiting.

## Features

- **Multiple Algorithms**: Token bucket, sliding window, fixed window
- **Distributed**: Redis backend for multi-instance deployments
- **REST API**: Configuration and monitoring endpoints
- **Real-time Monitoring**: Rate limit status and metrics
- **Docker Support**: Containerized deployment with docker-compose

## Quick Start

```bash
# Start with Docker Compose
docker-compose up -d

# Or run locally
pip install -r requirements.txt
python app.py
```

## API Endpoints

### Rate Limiting
- `POST /check` - Check if request is allowed
- `GET /status/{key}` - Get current rate limit status

### Configuration
- `POST /config` - Create/update rate limit configuration
- `GET /config/{key}` - Get configuration for a key
- `DELETE /config/{key}` - Delete configuration

### Monitoring
- `GET /metrics` - Get service metrics
- `GET /health` - Health check endpoint

## Rate Limiting Algorithms

### Token Bucket
- Allows burst traffic up to bucket capacity
- Tokens refill at constant rate
- Good for APIs with occasional spikes

### Sliding Window
- Precise rate limiting over time window
- Memory efficient with Redis sorted sets
- Best accuracy for strict rate limits

### Fixed Window
- Simple counter-based approach
- Resets at fixed intervals
- Lightweight but allows burst at window boundaries

## Configuration Example

```json
{
  "key": "api:user:123",
  "algorithm": "token_bucket",
  "limit": 100,
  "window": 3600,
  "burst": 10
}
```

## Performance

- **Throughput**: 10,000+ requests/second
- **Latency**: < 5ms average response time
- **Memory**: ~1MB per 10,000 active keys
- **Redis**: Single Redis instance supports 100,000+ keys

## Load Testing

```bash
# Install dependencies
pip install -r load-test/requirements.txt

# Run load test
python load-test/load_test.py --concurrent 100 --requests 10000
```

## Monitoring

The service exposes Prometheus-compatible metrics at `/metrics`:

- `rate_limiter_requests_total` - Total requests processed
- `rate_limiter_allowed_total` - Requests allowed
- `rate_limiter_denied_total` - Requests denied
- `rate_limiter_response_time` - Response time histogram