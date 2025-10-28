# URL Shortener - API Design

## API Overview

RESTful API design for the URL shortener service with comprehensive endpoints for URL management, analytics, and user operations. The API supports both authenticated and anonymous usage with appropriate rate limiting.

## Base URL
```
https://api.shorturl.com/v1
```

## Authentication

### API Key Authentication (Recommended)
```
X-API-Key: your_api_key_here
```

### Bearer Token Authentication
```
Authorization: Bearer <access_token>
```

### Anonymous Usage
Limited functionality available without authentication with stricter rate limits.

## Common Response Format

### Success Response
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2024-01-15T10:30:00Z",
    "request_id": "req_abc123",
    "rate_limit": {
      "limit": 1000,
      "remaining": 999,
      "reset": 1642248600
    }
  }
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "INVALID_URL",
    "message": "The provided URL is not valid",
    "details": "URL must start with http:// or https://"
  },
  "meta": {
    "timestamp": "2024-01-15T10:30:00Z",
    "request_id": "req_abc123"
  }
}
```

## URL Management APIs

### Create Short URL
```http
POST /urls
```

**Request Body:**
```json
{
  "long_url": "https://example.com/very/long/url/with/many/parameters?param1=value1&param2=value2",
  "custom_alias": "my-custom-link",
  "expiration_date": "2024-12-31T23:59:59Z",
  "description": "My important link",
  "tags": ["marketing", "campaign-2024"]
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "short_url": "https://shorturl.com/abc123",
    "short_code": "abc123",
    "long_url": "https://example.com/very/long/url/with/many/parameters?param1=value1&param2=value2",
    "custom_alias": "my-custom-link",
    "qr_code_url": "https://api.shorturl.com/v1/qr/abc123",
    "created_at": "2024-01-15T10:30:00Z",
    "expiration_date": "2024-12-31T23:59:59Z",
    "description": "My important link",
    "tags": ["marketing", "campaign-2024"],
    "click_count": 0,
    "status": "active"
  }
}
```

### Get URL Details
```http
GET /urls/{short_code}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "short_url": "https://shorturl.com/abc123",
    "short_code": "abc123",
    "long_url": "https://example.com/very/long/url",
    "created_at": "2024-01-15T10:30:00Z",
    "updated_at": "2024-01-15T10:30:00Z",
    "expiration_date": null,
    "description": "My important link",
    "tags": ["marketing"],
    "click_count": 1250,
    "status": "active",
    "owner": {
      "user_id": "user_123",
      "username": "johndoe"
    }
  }
}
```

### Update URL
```http
PUT /urls/{short_code}
```

**Request Body:**
```json
{
  "long_url": "https://example.com/updated/url",
  "description": "Updated description",
  "expiration_date": "2024-12-31T23:59:59Z",
  "tags": ["updated", "marketing"]
}
```

### Delete URL
```http
DELETE /urls/{short_code}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "message": "URL successfully deleted",
    "short_code": "abc123",
    "deleted_at": "2024-01-15T10:30:00Z"
  }
}
```

### List User URLs
```http
GET /urls?limit=20&offset=0&status=active&tag=marketing&sort=created_at&order=desc
```

**Query Parameters:**
- `limit`: Number of results (default: 20, max: 100)
- `offset`: Pagination offset
- `status`: Filter by status (`active`, `expired`, `deleted`)
- `tag`: Filter by tag
- `sort`: Sort field (`created_at`, `click_count`, `updated_at`)
- `order`: Sort order (`asc`, `desc`)

**Response:**
```json
{
  "success": true,
  "data": {
    "urls": [
      {
        "short_url": "https://shorturl.com/abc123",
        "short_code": "abc123",
        "long_url": "https://example.com/page1",
        "created_at": "2024-01-15T10:30:00Z",
        "click_count": 1250,
        "status": "active"
      }
    ],
    "pagination": {
      "total": 150,
      "limit": 20,
      "offset": 0,
      "has_more": true
    }
  }
}
```

## Redirection API

### Redirect Short URL
```http
GET /{short_code}
```

**Response:**
```http
HTTP/1.1 301 Moved Permanently
Location: https://example.com/original/url
Cache-Control: public, max-age=3600
```

### Get Redirect Info (No Redirect)
```http
GET /{short_code}/info
```

**Response:**
```json
{
  "success": true,
  "data": {
    "short_code": "abc123",
    "long_url": "https://example.com/original/url",
    "title": "Example Page Title",
    "description": "Page description from meta tags",
    "image": "https://example.com/image.jpg",
    "safe_browsing": {
      "is_safe": true,
      "last_checked": "2024-01-15T09:00:00Z"
    }
  }
}
```

## Analytics APIs

### Get URL Analytics
```http
GET /urls/{short_code}/analytics?period=7d&granularity=day
```

**Query Parameters:**
- `period`: Time period (`1h`, `24h`, `7d`, `30d`, `90d`, `1y`, `all`)
- `granularity`: Data granularity (`hour`, `day`, `week`, `month`)

**Response:**
```json
{
  "success": true,
  "data": {
    "short_code": "abc123",
    "total_clicks": 1250,
    "unique_clicks": 890,
    "period": "7d",
    "granularity": "day",
    "time_series": [
      {
        "date": "2024-01-15",
        "clicks": 180,
        "unique_clicks": 120
      },
      {
        "date": "2024-01-14",
        "clicks": 220,
        "unique_clicks": 150
      }
    ],
    "geographic_data": [
      {
        "country": "United States",
        "country_code": "US",
        "clicks": 450,
        "percentage": 36.0
      },
      {
        "country": "United Kingdom",
        "country_code": "GB",
        "clicks": 200,
        "percentage": 16.0
      }
    ],
    "referrer_data": [
      {
        "referrer": "google.com",
        "clicks": 300,
        "percentage": 24.0
      },
      {
        "referrer": "twitter.com",
        "clicks": 150,
        "percentage": 12.0
      },
      {
        "referrer": "direct",
        "clicks": 400,
        "percentage": 32.0
      }
    ],
    "device_data": [
      {
        "device_type": "desktop",
        "clicks": 750,
        "percentage": 60.0
      },
      {
        "device_type": "mobile",
        "clicks": 400,
        "percentage": 32.0
      },
      {
        "device_type": "tablet",
        "clicks": 100,
        "percentage": 8.0
      }
    ]
  }
}
```

### Get Aggregate Analytics
```http
GET /analytics/summary?period=30d
```

**Response:**
```json
{
  "success": true,
  "data": {
    "total_urls": 1500,
    "total_clicks": 45000,
    "unique_clicks": 32000,
    "active_urls": 1200,
    "top_performing_urls": [
      {
        "short_code": "abc123",
        "clicks": 1250,
        "click_rate": 2.78
      }
    ],
    "click_trends": {
      "daily_average": 1500,
      "weekly_growth": 5.2,
      "monthly_growth": 12.8
    }
  }
}
```

## QR Code APIs

### Generate QR Code
```http
GET /qr/{short_code}?size=200&format=png
```

**Query Parameters:**
- `size`: QR code size in pixels (default: 200, max: 1000)
- `format`: Image format (`png`, `svg`, `jpg`)
- `color`: Foreground color (hex code, default: #000000)
- `background`: Background color (hex code, default: #FFFFFF)

**Response:**
```
Content-Type: image/png
Cache-Control: public, max-age=86400

[Binary image data]
```

### Get QR Code URL
```http
GET /urls/{short_code}/qr
```

**Response:**
```json
{
  "success": true,
  "data": {
    "qr_code_url": "https://api.shorturl.com/v1/qr/abc123",
    "download_urls": {
      "png_200": "https://api.shorturl.com/v1/qr/abc123?size=200&format=png",
      "png_500": "https://api.shorturl.com/v1/qr/abc123?size=500&format=png",
      "svg": "https://api.shorturl.com/v1/qr/abc123?format=svg"
    }
  }
}
```

## User Management APIs

### Get User Profile
```http
GET /user/profile
```

**Response:**
```json
{
  "success": true,
  "data": {
    "user_id": "user_123",
    "username": "johndoe",
    "email": "john@example.com",
    "display_name": "John Doe",
    "created_at": "2023-01-15T10:30:00Z",
    "plan": "premium",
    "usage_stats": {
      "urls_created": 1500,
      "total_clicks": 45000,
      "monthly_quota": 10000,
      "monthly_usage": 2500
    }
  }
}
```

### Update User Profile
```http
PUT /user/profile
```

**Request Body:**
```json
{
  "display_name": "John Smith",
  "email": "johnsmith@example.com"
}
```

### Get API Keys
```http
GET /user/api-keys
```

**Response:**
```json
{
  "success": true,
  "data": {
    "api_keys": [
      {
        "key_id": "key_123",
        "name": "Production API Key",
        "key_prefix": "sk_live_abc...",
        "created_at": "2024-01-01T00:00:00Z",
        "last_used": "2024-01-15T10:30:00Z",
        "permissions": ["read", "write"],
        "rate_limit": 10000
      }
    ]
  }
}
```

### Create API Key
```http
POST /user/api-keys
```

**Request Body:**
```json
{
  "name": "My API Key",
  "permissions": ["read", "write"],
  "rate_limit": 5000
}
```

## Bulk Operations APIs

### Bulk Create URLs
```http
POST /urls/bulk
```

**Request Body:**
```json
{
  "urls": [
    {
      "long_url": "https://example.com/page1",
      "custom_alias": "page1",
      "description": "Page 1"
    },
    {
      "long_url": "https://example.com/page2",
      "custom_alias": "page2",
      "description": "Page 2"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "created": [
      {
        "short_url": "https://shorturl.com/page1",
        "long_url": "https://example.com/page1",
        "status": "created"
      }
    ],
    "failed": [
      {
        "long_url": "https://example.com/page2",
        "error": "Custom alias already exists",
        "status": "failed"
      }
    ],
    "summary": {
      "total": 2,
      "created": 1,
      "failed": 1
    }
  }
}
```

## Rate Limiting

### Rate Limits by Plan

| Plan | URLs/hour | Redirections/hour | Analytics Requests/hour |
|------|-----------|-------------------|------------------------|
| Anonymous | 10 | 1,000 | 10 |
| Free | 100 | 10,000 | 100 |
| Premium | 1,000 | 100,000 | 1,000 |
| Enterprise | 10,000 | 1,000,000 | 10,000 |

### Rate Limit Headers
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642248600
X-RateLimit-Retry-After: 3600
```

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_URL` | 400 | URL format is invalid |
| `URL_NOT_ACCESSIBLE` | 400 | URL is not accessible |
| `CUSTOM_ALIAS_TAKEN` | 409 | Custom alias already exists |
| `URL_NOT_FOUND` | 404 | Short URL not found |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Access denied |
| `RATE_LIMITED` | 429 | Rate limit exceeded |
| `URL_EXPIRED` | 410 | Short URL has expired |
| `MALICIOUS_URL` | 400 | URL flagged as malicious |
| `QUOTA_EXCEEDED` | 402 | Usage quota exceeded |
| `INTERNAL_ERROR` | 500 | Server error |

## Webhooks

### Configure Webhook
```http
POST /webhooks
```

**Request Body:**
```json
{
  "url": "https://your-app.com/webhook",
  "events": ["url.clicked", "url.created", "url.expired"],
  "secret": "your_webhook_secret"
}
```

### Webhook Payload Example
```json
{
  "event": "url.clicked",
  "timestamp": "2024-01-15T10:30:00Z",
  "data": {
    "short_code": "abc123",
    "long_url": "https://example.com/page",
    "click_data": {
      "ip_address": "192.168.1.1",
      "user_agent": "Mozilla/5.0...",
      "referrer": "https://google.com",
      "country": "US",
      "device_type": "desktop"
    }
  }
}
```

## SDK Examples

### JavaScript/Node.js
```javascript
const ShortURL = require('@shorturl/sdk');

const client = new ShortURL({
  apiKey: 'your_api_key'
});

// Create short URL
const result = await client.urls.create({
  long_url: 'https://example.com/very/long/url',
  custom_alias: 'my-link'
});

console.log(result.short_url); // https://shorturl.com/my-link
```

### Python
```python
import shorturl

client = shorturl.Client(api_key='your_api_key')

# Create short URL
result = client.urls.create(
    long_url='https://example.com/very/long/url',
    custom_alias='my-link'
)

print(result.short_url)  # https://shorturl.com/my-link
```