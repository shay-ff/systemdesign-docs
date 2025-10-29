# Cache Server API Documentation

## Overview

The Cache Server provides a RESTful HTTP interface for cache operations with Redis backend. All endpoints return JSON responses and support standard HTTP status codes.

## Base URL

```
http://localhost:8000
```

## Authentication

No authentication required for this implementation. In production, consider adding API keys or OAuth.

## Endpoints

### Health Check

Check if the cache server and Redis are healthy.

```http
GET /health
```

**Response:**
```json
{
  "status": "healthy",
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

**Status Codes:**
- `200 OK` - Service is healthy
- `503 Service Unavailable` - Redis connection failed

---

### Get Cache Item

Retrieve a cached value by key.

```http
GET /cache/{key}
```

**Parameters:**
- `key` (string, required) - The cache key to retrieve

**Response:**
```json
{
  "key": "user:123",
  "value": {"name": "John Doe", "email": "john@example.com"},
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

**Status Codes:**
- `200 OK` - Key found and returned
- `404 Not Found` - Key does not exist
- `500 Internal Server Error` - Redis error

---

### Set Cache Item

Store a key-value pair in the cache.

```http
PUT /cache/{key}
```

**Parameters:**
- `key` (string, required) - The cache key to store

**Request Body:**
```json
{
  "value": "any JSON value",
  "ttl": 300
}
```

**Fields:**
- `value` (any, required) - The value to cache (JSON serializable)
- `ttl` (integer, optional) - Time to live in seconds (default: 3600)

**Response:**
```json
{
  "key": "user:123",
  "value": {"name": "John Doe", "email": "john@example.com"},
  "ttl": 300,
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

**Status Codes:**
- `200 OK` - Item stored successfully
- `500 Internal Server Error` - Failed to store item

---

### Delete Cache Item

Remove a specific key from the cache.

```http
DELETE /cache/{key}
```

**Parameters:**
- `key` (string, required) - The cache key to delete

**Response:**
```json
{
  "key": "user:123",
  "deleted": true,
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

**Status Codes:**
- `200 OK` - Key deleted successfully
- `404 Not Found` - Key does not exist

---

### Clear Cache

Remove all keys from the cache.

```http
DELETE /cache
```

**Response:**
```json
{
  "cleared": true,
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

**Status Codes:**
- `200 OK` - Cache cleared successfully

---

### Get Statistics

Retrieve cache performance statistics.

```http
GET /stats
```

**Response:**
```json
{
  "total_keys": 1250,
  "hits": 8500,
  "misses": 1500,
  "hit_rate": 85.0,
  "memory_usage": "45.2M",
  "uptime_seconds": 3600
}
```

**Fields:**
- `total_keys` - Number of keys currently in cache
- `hits` - Number of successful cache retrievals
- `misses` - Number of cache misses
- `hit_rate` - Cache hit rate as percentage
- `memory_usage` - Redis memory usage (human readable)
- `uptime_seconds` - Server uptime in seconds

---

### Root Endpoint

Get API information and available endpoints.

```http
GET /
```

**Response:**
```json
{
  "service": "Cache Server",
  "version": "1.0.0",
  "description": "REST API cache service with Redis backend",
  "endpoints": {
    "GET /cache/{key}": "Retrieve cached value",
    "PUT /cache/{key}": "Store key-value pair",
    "DELETE /cache/{key}": "Remove specific key",
    "DELETE /cache": "Clear entire cache",
    "GET /stats": "Cache statistics",
    "GET /health": "Health check"
  }
}
```

## Error Responses

All error responses follow this format:

```json
{
  "detail": "Error message description"
}
```

Common error status codes:
- `400 Bad Request` - Invalid request format
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server or Redis error
- `503 Service Unavailable` - Service unhealthy

## Examples

### Store a user object

```bash
curl -X PUT "http://localhost:8000/cache/user:123" \
     -H "Content-Type: application/json" \
     -d '{
       "value": {
         "name": "John Doe",
         "email": "john@example.com",
         "role": "admin"
       },
       "ttl": 1800
     }'
```

### Retrieve the user

```bash
curl "http://localhost:8000/cache/user:123"
```

### Store a simple string

```bash
curl -X PUT "http://localhost:8000/cache/session:abc123" \
     -H "Content-Type: application/json" \
     -d '{
       "value": "active",
       "ttl": 300
     }'
```

### Get cache statistics

```bash
curl "http://localhost:8000/stats"
```

### Clear all cache

```bash
curl -X DELETE "http://localhost:8000/cache"
```

## Rate Limiting

No rate limiting is implemented in this version. For production use, consider adding rate limiting middleware.

## Monitoring

The `/stats` endpoint provides basic performance metrics. For production monitoring, integrate with:
- Prometheus metrics
- Application performance monitoring (APM)
- Redis monitoring tools