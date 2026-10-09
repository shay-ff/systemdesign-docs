# Rate Limiter Service API Documentation

## Base URL
```
http://localhost:5000
```

## Authentication
No authentication required for this demo service.

## Endpoints

### Health Check

#### GET /health
Check service health and Redis connectivity.

**Response:**
```json
{
  "status": "healthy",
  "redis": "connected"
}
```

**Status Codes:**
- `200` - Service healthy
- `503` - Service unhealthy

---

### Rate Limiting

#### POST /check
Check if a request is allowed under the configured rate limit.

**Request Body:**
```json
{
  "key": "api:user:123"
}
```

**Response:**
```json
{
  "allowed": true,
  "remaining": 99,
  "reset_time": 1640995200,
  "algorithm": "token_bucket"
}
```

**Status Codes:**
- `200` - Check completed
- `400` - Invalid request
- `404` - No configuration found
- `500` - Internal error

#### GET /status/{key}
Get current rate limit status for a key without consuming quota.

**Parameters:**
- `key` (path) - Rate limit key

**Response (Token Bucket):**
```json
{
  "key": "api:user:123",
  "algorithm": "token_bucket",
  "tokens_remaining": 95.5,
  "last_refill": 1640995100.123
}
```

**Response (Sliding Window):**
```json
{
  "key": "api:user:123",
  "algorithm": "sliding_window",
  "requests_in_window": 15,
  "limit": 100
}
```

**Response (Fixed Window):**
```json
{
  "key": "api:user:123",
  "algorithm": "fixed_window",
  "requests_in_window": 25,
  "limit": 100,
  "window_reset": 1640995200
}
```

---

### Configuration Management

#### POST /config
Create or update a rate limit configuration.

**Request Body:**
```json
{
  "key": "api:user:123",
  "algorithm": "token_bucket",
  "limit": 100,
  "window": 3600,
  "burst": 10
}
```

**Parameters:**
- `key` (required) - Unique identifier for the rate limit
- `algorithm` (required) - Algorithm type: `token_bucket`, `sliding_window`, `fixed_window`
- `limit` (required) - Number of requests allowed per window
- `window` (required) - Time window in seconds
- `burst` (optional) - Additional burst capacity for token bucket (default: 0)

**Response:**
```json
{
  "message": "Configuration created",
  "config": {
    "key": "api:user:123",
    "algorithm": "token_bucket",
    "limit": 100,
    "window": 3600,
    "burst": 10
  }
}
```

#### GET /config/{key}
Get configuration for a specific key.

**Parameters:**
- `key` (path) - Rate limit key

**Response:**
```json
{
  "key": "api:user:123",
  "algorithm": "token_bucket",
  "limit": "100",
  "window": "3600",
  "burst": "10"
}
```

#### DELETE /config/{key}
Delete configuration and associated data for a key.

**Parameters:**
- `key` (path) - Rate limit key

**Response:**
```json
{
  "message": "Configuration deleted"
}
```

---

### Monitoring

#### GET /metrics
Prometheus-compatible metrics endpoint.

**Response:**
```
# HELP rate_limiter_requests_total Total requests
# TYPE rate_limiter_requests_total counter
rate_limiter_requests_total{method="POST",endpoint="/check"} 1234.0

# HELP rate_limiter_allowed_total Requests allowed
# TYPE rate_limiter_allowed_total counter
rate_limiter_allowed_total{algorithm="token_bucket"} 1200.0

# HELP rate_limiter_denied_total Requests denied
# TYPE rate_limiter_denied_total counter
rate_limiter_denied_total{algorithm="token_bucket"} 34.0

# HELP rate_limiter_response_time_seconds Response time
# TYPE rate_limiter_response_time_seconds histogram
rate_limiter_response_time_seconds_bucket{le="0.005"} 800.0
rate_limiter_response_time_seconds_bucket{le="0.01"} 1150.0
rate_limiter_response_time_seconds_bucket{le="0.025"} 1200.0
rate_limiter_response_time_seconds_bucket{le="+Inf"} 1234.0
rate_limiter_response_time_seconds_count 1234.0
rate_limiter_response_time_seconds_sum 5.67
```

## Rate Limiting Algorithms

### Token Bucket
- **Use Case**: APIs that need to allow burst traffic
- **Behavior**: Tokens are added at a constant rate, requests consume tokens
- **Burst**: Allows burst up to `limit + burst` tokens
- **Memory**: O(1) per key

### Sliding Window
- **Use Case**: Strict rate limiting with precise time windows
- **Behavior**: Tracks exact request timestamps in a sliding window
- **Precision**: Most accurate, no boundary effects
- **Memory**: O(limit) per key

### Fixed Window
- **Use Case**: Simple rate limiting with predictable reset times
- **Behavior**: Counter resets at fixed intervals
- **Burst**: Allows burst at window boundaries
- **Memory**: O(1) per key

## Error Responses

All error responses follow this format:
```json
{
  "error": "Error description"
}
```

Common error codes:
- `400` - Bad Request (missing/invalid parameters)
- `404` - Not Found (configuration not found)
- `500` - Internal Server Error
- `503` - Service Unavailable (Redis connection failed)

## Rate Limit Headers

The service doesn't currently add rate limit headers to responses, but this could be implemented by clients based on the response data:

- `X-RateLimit-Limit` - Request limit per window
- `X-RateLimit-Remaining` - Requests remaining in current window
- `X-RateLimit-Reset` - Time when the rate limit resets

## Examples

### Basic Usage

1. **Create Configuration:**
```bash
curl -X POST http://localhost:5000/config \
  -H "Content-Type: application/json" \
  -d '{
    "key": "api:user:123",
    "algorithm": "token_bucket",
    "limit": 100,
    "window": 3600,
    "burst": 20
  }'
```

2. **Check Rate Limit:**
```bash
curl -X POST http://localhost:5000/check \
  -H "Content-Type: application/json" \
  -d '{"key": "api:user:123"}'
```

3. **Get Status:**
```bash
curl http://localhost:5000/status/api:user:123
```

### Different Algorithms

**Sliding Window (Strict):**
```bash
curl -X POST http://localhost:5000/config \
  -H "Content-Type: application/json" \
  -d '{
    "key": "api:strict",
    "algorithm": "sliding_window",
    "limit": 60,
    "window": 60
  }'
```

**Fixed Window (Simple):**
```bash
curl -X POST http://localhost:5000/config \
  -H "Content-Type: application/json" \
  -d '{
    "key": "api:simple",
    "algorithm": "fixed_window",
    "limit": 1000,
    "window": 3600
  }'
```