# Netflix Streaming System - API Design

## API Overview

The Netflix streaming system exposes RESTful APIs organized around core business entities. All APIs use JSON for request/response payloads and follow REST conventions with proper HTTP status codes.

**Base URL**: `https://api.netflix.com/v1`

**Authentication**: Bearer token (JWT) in Authorization header
```
Authorization: Bearer <jwt_token>
```

## User Management APIs

### Authentication

#### POST /auth/login
Login user and return JWT token.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securepassword"
}
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 3600,
  "user": {
    "id": "user123",
    "email": "user@example.com",
    "subscription_tier": "premium"
  }
}
```

#### POST /auth/refresh
Refresh access token using refresh token.

#### POST /auth/logout
Logout user and invalidate tokens.

### User Profiles

#### GET /users/profiles
Get all profiles for authenticated user.

**Response:**
```json
{
  "profiles": [
    {
      "id": "profile123",
      "name": "John Doe",
      "avatar_url": "https://cdn.netflix.com/avatars/profile123.jpg",
      "is_kids": false,
      "language": "en-US",
      "maturity_rating": "TV-MA"
    }
  ]
}
```

#### POST /users/profiles
Create new user profile.

#### PUT /users/profiles/{profile_id}
Update user profile.

#### DELETE /users/profiles/{profile_id}
Delete user profile.

## Content Management APIs

### Content Discovery

#### GET /content/browse
Browse content with filtering and pagination.

**Query Parameters:**
- `genre`: Filter by genre (action, comedy, drama, etc.)
- `type`: Content type (movie, series, documentary)
- `rating`: Maturity rating filter
- `language`: Content language
- `page`: Page number (default: 1)
- `limit`: Items per page (default: 20, max: 100)

**Response:**
```json
{
  "content": [
    {
      "id": "content123",
      "title": "Stranger Things",
      "type": "series",
      "genre": ["sci-fi", "thriller"],
      "rating": "TV-14",
      "duration": 3600,
      "release_year": 2016,
      "thumbnail_url": "https://cdn.netflix.com/thumbnails/content123.jpg",
      "description": "A group of kids uncover supernatural mysteries...",
      "cast": ["Millie Bobby Brown", "Finn Wolfhard"],
      "director": "The Duffer Brothers",
      "seasons": 4,
      "episodes": 34
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 15000,
    "has_next": true
  }
}
```

#### GET /content/{content_id}
Get detailed information about specific content.

#### GET /content/trending
Get trending content.

#### GET /content/new-releases
Get recently added content.

### Search

#### GET /search
Search content by title, cast, director, or genre.

**Query Parameters:**
- `q`: Search query (required)
- `type`: Filter by content type
- `limit`: Number of results (default: 10, max: 50)

**Response:**
```json
{
  "results": [
    {
      "id": "content123",
      "title": "Stranger Things",
      "type": "series",
      "match_score": 0.95,
      "thumbnail_url": "https://cdn.netflix.com/thumbnails/content123.jpg"
    }
  ],
  "total_results": 42,
  "query": "stranger things"
}
```

## Streaming APIs

### Video Playback

#### GET /streaming/{content_id}/manifest
Get video streaming manifest for adaptive bitrate streaming.

**Response:**
```json
{
  "content_id": "content123",
  "manifest_url": "https://cdn.netflix.com/manifests/content123.m3u8",
  "drm_license_url": "https://drm.netflix.com/license/content123",
  "subtitles": [
    {
      "language": "en-US",
      "url": "https://cdn.netflix.com/subtitles/content123_en.vtt"
    }
  ],
  "audio_tracks": [
    {
      "language": "en-US",
      "codec": "aac",
      "url": "https://cdn.netflix.com/audio/content123_en.m4a"
    }
  ]
}
```

#### POST /streaming/{content_id}/start
Start streaming session and log viewing event.

**Request:**
```json
{
  "profile_id": "profile123",
  "device_type": "web",
  "quality": "1080p"
}
```

#### PUT /streaming/{content_id}/progress
Update viewing progress.

**Request:**
```json
{
  "profile_id": "profile123",
  "position": 1800,
  "duration": 3600,
  "session_id": "session123"
}
```

#### POST /streaming/{content_id}/stop
End streaming session.

### Viewing History

#### GET /users/profiles/{profile_id}/history
Get viewing history for profile.

#### DELETE /users/profiles/{profile_id}/history/{content_id}
Remove item from viewing history.

## Recommendation APIs

### Personalized Recommendations

#### GET /recommendations/{profile_id}
Get personalized recommendations for user profile.

**Response:**
```json
{
  "recommendations": [
    {
      "category": "Because you watched Stranger Things",
      "content": [
        {
          "id": "content456",
          "title": "Dark",
          "confidence_score": 0.89,
          "thumbnail_url": "https://cdn.netflix.com/thumbnails/content456.jpg"
        }
      ]
    }
  ],
  "generated_at": "2024-01-15T10:30:00Z"
}
```

#### GET /recommendations/trending
Get trending recommendations.

#### GET /recommendations/similar/{content_id}
Get content similar to specified item.

## Watchlist APIs

#### GET /users/profiles/{profile_id}/watchlist
Get user's watchlist.

#### POST /users/profiles/{profile_id}/watchlist
Add content to watchlist.

**Request:**
```json
{
  "content_id": "content123"
}
```

#### DELETE /users/profiles/{profile_id}/watchlist/{content_id}
Remove content from watchlist.

## Analytics APIs

### User Analytics

#### POST /analytics/events
Track user interaction events.

**Request:**
```json
{
  "event_type": "video_play",
  "profile_id": "profile123",
  "content_id": "content123",
  "timestamp": "2024-01-15T10:30:00Z",
  "metadata": {
    "device_type": "web",
    "quality": "1080p",
    "position": 0
  }
}
```

#### GET /analytics/viewing-stats/{profile_id}
Get viewing statistics for profile.

## Content Management APIs (Admin)

### Content Upload

#### POST /admin/content
Upload new content (multipart form data).

**Request:**
```
Content-Type: multipart/form-data

video_file: [binary video file]
metadata: {
  "title": "New Movie",
  "description": "Movie description",
  "genre": ["action", "thriller"],
  "rating": "PG-13",
  "release_year": 2024
}
```

#### PUT /admin/content/{content_id}
Update content metadata.

#### DELETE /admin/content/{content_id}
Delete content.

### Content Processing

#### GET /admin/content/{content_id}/processing-status
Get video processing status.

**Response:**
```json
{
  "content_id": "content123",
  "status": "processing",
  "progress": 75,
  "stages": {
    "upload": "completed",
    "encoding": "in_progress",
    "thumbnail_generation": "pending",
    "cdn_distribution": "pending"
  },
  "estimated_completion": "2024-01-15T12:00:00Z"
}
```

## Error Responses

All APIs return consistent error responses:

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "The request is invalid",
    "details": "Email format is invalid"
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "request_id": "req_123456"
}
```

### Common Error Codes

- `UNAUTHORIZED` (401): Invalid or missing authentication
- `FORBIDDEN` (403): Insufficient permissions
- `NOT_FOUND` (404): Resource not found
- `INVALID_REQUEST` (400): Request validation failed
- `RATE_LIMITED` (429): Too many requests
- `INTERNAL_ERROR` (500): Server error
- `SERVICE_UNAVAILABLE` (503): Service temporarily unavailable

## Rate Limiting

APIs are rate limited per user:
- Authentication APIs: 10 requests/minute
- Content APIs: 1000 requests/hour
- Streaming APIs: 100 requests/minute
- Analytics APIs: 500 requests/hour

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642248600
```