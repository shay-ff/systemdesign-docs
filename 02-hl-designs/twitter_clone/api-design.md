# Twitter Clone - API Design

## API Overview

RESTful API design for the Twitter clone system with clear resource-based endpoints, consistent response formats, and comprehensive error handling.

## Base URL
```
https://api.twitter-clone.com/v1
```

## Authentication

All API requests require authentication via Bearer token:
```
Authorization: Bearer <access_token>
```

## Common Response Format

### Success Response
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2024-01-15T10:30:00Z",
    "request_id": "req_123456789"
  }
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "The request is invalid",
    "details": "Tweet content exceeds 280 characters"
  },
  "meta": {
    "timestamp": "2024-01-15T10:30:00Z",
    "request_id": "req_123456789"
  }
}
```

## User Management APIs

### Get User Profile
```http
GET /users/{user_id}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "user_id": "user_123",
    "username": "johndoe",
    "display_name": "John Doe",
    "bio": "Software engineer and coffee enthusiast",
    "profile_image_url": "https://cdn.twitter-clone.com/profiles/user_123.jpg",
    "followers_count": 1250,
    "following_count": 890,
    "tweets_count": 3420,
    "verified": false,
    "created_at": "2020-03-15T08:00:00Z"
  }
}
```

### Update User Profile
```http
PUT /users/{user_id}
```

**Request Body:**
```json
{
  "display_name": "John Doe",
  "bio": "Updated bio",
  "profile_image_url": "https://cdn.twitter-clone.com/profiles/new_image.jpg"
}
```

## Tweet Management APIs

### Create Tweet
```http
POST /tweets
```

**Request Body:**
```json
{
  "content": "This is my tweet content #hashtag",
  "media_urls": ["https://cdn.twitter-clone.com/media/image1.jpg"],
  "reply_to_tweet_id": null,
  "quote_tweet_id": null
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "tweet_id": "tweet_456",
    "user_id": "user_123",
    "content": "This is my tweet content #hashtag",
    "media_urls": ["https://cdn.twitter-clone.com/media/image1.jpg"],
    "created_at": "2024-01-15T10:30:00Z",
    "like_count": 0,
    "retweet_count": 0,
    "reply_count": 0,
    "hashtags": ["hashtag"],
    "mentions": []
  }
}
```

### Get Tweet
```http
GET /tweets/{tweet_id}
```

### Delete Tweet
```http
DELETE /tweets/{tweet_id}
```

### Like Tweet
```http
POST /tweets/{tweet_id}/like
```

### Unlike Tweet
```http
DELETE /tweets/{tweet_id}/like
```

### Retweet
```http
POST /tweets/{tweet_id}/retweet
```

## Timeline APIs

### Get Home Timeline
```http
GET /timeline/home?limit=20&cursor=cursor_token
```

**Response:**
```json
{
  "success": true,
  "data": {
    "tweets": [
      {
        "tweet_id": "tweet_789",
        "user": {
          "user_id": "user_456",
          "username": "janedoe",
          "display_name": "Jane Doe",
          "profile_image_url": "https://cdn.twitter-clone.com/profiles/user_456.jpg",
          "verified": true
        },
        "content": "Great weather today! ☀️",
        "created_at": "2024-01-15T09:45:00Z",
        "like_count": 15,
        "retweet_count": 3,
        "reply_count": 2,
        "liked_by_user": false,
        "retweeted_by_user": false
      }
    ],
    "next_cursor": "next_cursor_token",
    "has_more": true
  }
}
```

### Get User Timeline
```http
GET /users/{user_id}/timeline?limit=20&cursor=cursor_token
```

## Social Graph APIs

### Follow User
```http
POST /users/{user_id}/follow
```

### Unfollow User
```http
DELETE /users/{user_id}/follow
```

### Get Followers
```http
GET /users/{user_id}/followers?limit=50&cursor=cursor_token
```

### Get Following
```http
GET /users/{user_id}/following?limit=50&cursor=cursor_token
```

## Search APIs

### Search Tweets
```http
GET /search/tweets?q=search_query&limit=20&cursor=cursor_token
```

**Query Parameters:**
- `q`: Search query (required)
- `limit`: Number of results (default: 20, max: 100)
- `cursor`: Pagination cursor
- `result_type`: `recent`, `popular`, or `mixed` (default: mixed)

### Search Users
```http
GET /search/users?q=search_query&limit=20&cursor=cursor_token
```

## Notification APIs

### Get Notifications
```http
GET /notifications?limit=50&cursor=cursor_token&type=all
```

**Query Parameters:**
- `type`: `all`, `mentions`, `likes`, `follows`, `retweets`

**Response:**
```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "notification_id": "notif_123",
        "type": "like",
        "created_at": "2024-01-15T10:15:00Z",
        "read": false,
        "actor": {
          "user_id": "user_789",
          "username": "alice",
          "display_name": "Alice Smith"
        },
        "target_tweet": {
          "tweet_id": "tweet_456",
          "content": "My original tweet"
        }
      }
    ],
    "next_cursor": "next_cursor_token",
    "unread_count": 5
  }
}
```

### Mark Notifications as Read
```http
PUT /notifications/read
```

## Rate Limiting

API endpoints are rate limited per user:

- **Tweet Creation**: 300 tweets per 15-minute window
- **Timeline Requests**: 300 requests per 15-minute window
- **Search Requests**: 450 requests per 15-minute window
- **User Operations**: 75 requests per 15-minute window

Rate limit headers are included in all responses:
```
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 299
X-RateLimit-Reset: 1642248600
```

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_REQUEST` | 400 | Request validation failed |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Access denied |
| `NOT_FOUND` | 404 | Resource not found |
| `RATE_LIMITED` | 429 | Rate limit exceeded |
| `INTERNAL_ERROR` | 500 | Server error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

## Pagination

All list endpoints use cursor-based pagination:

```json
{
  "data": [...],
  "next_cursor": "eyJjcmVhdGVkX2F0IjoiMjAyNC0wMS0xNVQxMDozMDowMFoiLCJpZCI6InR3ZWV0XzQ1NiJ9",
  "has_more": true
}
```

Use the `next_cursor` value in the `cursor` query parameter for the next page.

## WebSocket API (Real-time Features)

### Connection
```
wss://ws.twitter-clone.com/v1/stream
```

### Authentication
Send authentication message after connection:
```json
{
  "type": "auth",
  "token": "bearer_token"
}
```

### Subscribe to Timeline Updates
```json
{
  "type": "subscribe",
  "channel": "timeline"
}
```

### Real-time Tweet Event
```json
{
  "type": "new_tweet",
  "data": {
    "tweet_id": "tweet_789",
    "user": { ... },
    "content": "Real-time tweet content",
    "created_at": "2024-01-15T10:30:00Z"
  }
}
```