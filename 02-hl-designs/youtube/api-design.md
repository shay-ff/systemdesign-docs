# YouTube System - API Design

## API Overview

The YouTube system exposes comprehensive RESTful APIs for video management, streaming, social features, and monetization. All APIs use JSON for request/response payloads and follow REST conventions with proper HTTP status codes.

**Base URL**: `https://www.googleapis.com/youtube/v3`

**Authentication**: OAuth 2.0 with scopes for different access levels
```
Authorization: Bearer <oauth_token>
```

## Authentication & Authorization

### OAuth 2.0 Scopes
```
youtube.readonly: Read access to user's YouTube data
youtube.upload: Upload videos to user's channel
youtube.force-ssl: Manage user's YouTube account
youtube.channel-memberships.creator: Manage channel memberships
```

### POST /oauth/token
Exchange authorization code for access token.

**Request:**
```json
{
  "client_id": "your_client_id",
  "client_secret": "your_client_secret",
  "code": "authorization_code",
  "grant_type": "authorization_code",
  "redirect_uri": "https://yourapp.com/callback"
}
```

**Response:**
```json
{
  "access_token": "ya29.a0AfH6SMC...",
  "refresh_token": "1//04_refresh_token",
  "expires_in": 3600,
  "token_type": "Bearer",
  "scope": "youtube.upload youtube.readonly"
}
```

## Video Management APIs

### Video Upload

#### POST /videos
Upload a video file and metadata.

**Request (Multipart Form Data):**
```
Content-Type: multipart/form-data

part=snippet,status
snippet={
  "title": "My Awesome Video",
  "description": "This is a great video about...",
  "tags": ["tutorial", "technology", "programming"],
  "categoryId": "28",
  "defaultLanguage": "en",
  "defaultAudioLanguage": "en"
}
status={
  "privacyStatus": "public",
  "publishAt": "2024-02-01T10:00:00Z",
  "selfDeclaredMadeForKids": false
}
media=[video file binary data]
```

**Response:**
```json
{
  "kind": "youtube#video",
  "etag": "etag_value",
  "id": "dQw4w9WgXcQ",
  "snippet": {
    "publishedAt": "2024-01-15T10:30:00Z",
    "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
    "title": "My Awesome Video",
    "description": "This is a great video about...",
    "thumbnails": {
      "default": {
        "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg",
        "width": 120,
        "height": 90
      },
      "high": {
        "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        "width": 480,
        "height": 360
      }
    },
    "channelTitle": "Creator Channel",
    "tags": ["tutorial", "technology", "programming"],
    "categoryId": "28"
  },
  "status": {
    "uploadStatus": "uploaded",
    "privacyStatus": "public",
    "license": "youtube",
    "embeddable": true,
    "publicStatsViewable": true
  },
  "statistics": {
    "viewCount": "0",
    "likeCount": "0",
    "dislikeCount": "0",
    "commentCount": "0"
  }
}
```

#### GET /videos
Retrieve video information.

**Query Parameters:**
- `part`: Comma-separated list of properties (snippet, statistics, status, etc.)
- `id`: Comma-separated list of video IDs
- `chart`: Chart to retrieve (mostPopular)
- `regionCode`: ISO 3166-1 alpha-2 country code
- `maxResults`: Maximum number of results (default: 5, max: 50)

**Response:**
```json
{
  "kind": "youtube#videoListResponse",
  "etag": "etag_value",
  "nextPageToken": "CAUQAA",
  "pageInfo": {
    "totalResults": 1000000,
    "resultsPerPage": 5
  },
  "items": [
    {
      "kind": "youtube#video",
      "etag": "etag_value",
      "id": "dQw4w9WgXcQ",
      "snippet": {
        "publishedAt": "2024-01-15T10:30:00Z",
        "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
        "title": "My Awesome Video",
        "description": "This is a great video about...",
        "thumbnails": {...},
        "channelTitle": "Creator Channel",
        "tags": ["tutorial", "technology"],
        "categoryId": "28",
        "liveBroadcastContent": "none",
        "duration": "PT4M13S"
      },
      "statistics": {
        "viewCount": "1234567",
        "likeCount": "12345",
        "dislikeCount": "123",
        "favoriteCount": "0",
        "commentCount": "1234"
      }
    }
  ]
}
```

#### PUT /videos
Update video metadata.

#### DELETE /videos
Delete a video.

### Video Processing Status

#### GET /videos/{videoId}/processingDetails
Get video processing status.

**Response:**
```json
{
  "processingStatus": "processing",
  "processingProgress": {
    "partsTotal": 4,
    "partsProcessed": 2,
    "timeLeftMs": "120000"
  },
  "processingFailureReason": null,
  "fileDetailsAvailability": "available",
  "processingIssuesAvailability": "available",
  "tagSuggestionsAvailability": "available",
  "editorSuggestionsAvailability": "available",
  "thumbnailsAvailability": "available"
}
```

## Channel Management APIs

### Channels

#### GET /channels
Retrieve channel information.

**Query Parameters:**
- `part`: Properties to include (snippet, statistics, brandingSettings, etc.)
- `id`: Channel ID
- `forUsername`: Channel username
- `mine`: Return authenticated user's channel

**Response:**
```json
{
  "kind": "youtube#channelListResponse",
  "etag": "etag_value",
  "pageInfo": {
    "totalResults": 1,
    "resultsPerPage": 1
  },
  "items": [
    {
      "kind": "youtube#channel",
      "etag": "etag_value",
      "id": "UCuAXFkgsw1L7xaCfnd5JJOw",
      "snippet": {
        "title": "Creator Channel",
        "description": "Welcome to my channel!",
        "customUrl": "@creatorchannel",
        "publishedAt": "2020-01-01T00:00:00Z",
        "thumbnails": {...},
        "country": "US"
      },
      "statistics": {
        "viewCount": "12345678",
        "subscriberCount": "123456",
        "hiddenSubscriberCount": false,
        "videoCount": "456"
      },
      "brandingSettings": {
        "channel": {
          "title": "Creator Channel",
          "description": "Welcome to my channel!",
          "keywords": "technology programming tutorials",
          "trackingAnalyticsAccountId": "UA-12345678-1"
        },
        "image": {
          "bannerExternalUrl": "https://yt3.ggpht.com/banner.jpg"
        }
      }
    }
  ]
}
```

### Subscriptions

#### POST /subscriptions
Subscribe to a channel.

**Request:**
```json
{
  "snippet": {
    "resourceId": {
      "kind": "youtube#channel",
      "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw"
    }
  }
}
```

#### GET /subscriptions
Get user's subscriptions.

#### DELETE /subscriptions
Unsubscribe from a channel.

## Search APIs

### Search

#### GET /search
Search for videos, channels, and playlists.

**Query Parameters:**
- `part`: Properties to include (snippet)
- `q`: Search query
- `type`: Resource type (video, channel, playlist)
- `order`: Sort order (relevance, date, rating, viewCount, title)
- `publishedAfter`: RFC 3339 formatted date-time
- `publishedBefore`: RFC 3339 formatted date-time
- `regionCode`: ISO 3166-1 alpha-2 country code
- `relevanceLanguage`: ISO 639-1 language code
- `maxResults`: Maximum results (default: 5, max: 50)

**Response:**
```json
{
  "kind": "youtube#searchListResponse",
  "etag": "etag_value",
  "nextPageToken": "CAUQAA",
  "regionCode": "US",
  "pageInfo": {
    "totalResults": 1000000,
    "resultsPerPage": 5
  },
  "items": [
    {
      "kind": "youtube#searchResult",
      "etag": "etag_value",
      "id": {
        "kind": "youtube#video",
        "videoId": "dQw4w9WgXcQ"
      },
      "snippet": {
        "publishedAt": "2024-01-15T10:30:00Z",
        "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
        "title": "My Awesome Video",
        "description": "This is a great video about...",
        "thumbnails": {...},
        "channelTitle": "Creator Channel",
        "liveBroadcastContent": "none",
        "publishTime": "2024-01-15T10:30:00Z"
      }
    }
  ]
}
```

## Comments APIs

### Comments

#### GET /commentThreads
Retrieve comment threads for a video.

**Query Parameters:**
- `part`: Properties to include (snippet, replies)
- `videoId`: Video ID
- `order`: Sort order (time, relevance)
- `maxResults`: Maximum results (default: 20, max: 100)

**Response:**
```json
{
  "kind": "youtube#commentThreadListResponse",
  "etag": "etag_value",
  "nextPageToken": "QURTSl9pN...",
  "pageInfo": {
    "totalResults": 1234,
    "resultsPerPage": 20
  },
  "items": [
    {
      "kind": "youtube#commentThread",
      "etag": "etag_value",
      "id": "UgxKREWq4Pn...",
      "snippet": {
        "videoId": "dQw4w9WgXcQ",
        "topLevelComment": {
          "kind": "youtube#comment",
          "etag": "etag_value",
          "id": "UgxKREWq4Pn...",
          "snippet": {
            "videoId": "dQw4w9WgXcQ",
            "textDisplay": "Great video! Thanks for sharing.",
            "textOriginal": "Great video! Thanks for sharing.",
            "authorDisplayName": "John Doe",
            "authorProfileImageUrl": "https://yt3.ggpht.com/profile.jpg",
            "authorChannelUrl": "http://www.youtube.com/channel/UC...",
            "authorChannelId": {
              "value": "UCuAXFkgsw1L7xaCfnd5JJOw"
            },
            "canRate": true,
            "likeCount": 5,
            "publishedAt": "2024-01-15T11:00:00Z",
            "updatedAt": "2024-01-15T11:00:00Z"
          }
        },
        "canReply": true,
        "totalReplyCount": 2,
        "isPublic": true
      },
      "replies": {
        "comments": [...]
      }
    }
  ]
}
```

#### POST /commentThreads
Post a new comment.

**Request:**
```json
{
  "snippet": {
    "videoId": "dQw4w9WgXcQ",
    "topLevelComment": {
      "snippet": {
        "textOriginal": "Great video! Thanks for sharing."
      }
    }
  }
}
```

#### POST /comments
Reply to a comment.

#### PUT /comments
Update a comment.

#### DELETE /comments
Delete a comment.

## Analytics APIs

### Analytics

#### GET /analytics/reports
Get analytics data for channels and videos.

**Query Parameters:**
- `ids`: Channel or content owner ID
- `startDate`: Start date (YYYY-MM-DD)
- `endDate`: End date (YYYY-MM-DD)
- `metrics`: Comma-separated list of metrics
- `dimensions`: Comma-separated list of dimensions
- `filters`: Filter expression
- `sort`: Sort expression

**Response:**
```json
{
  "kind": "youtubeAnalytics#resultTable",
  "columnHeaders": [
    {
      "name": "day",
      "columnType": "DIMENSION",
      "dataType": "STRING"
    },
    {
      "name": "views",
      "columnType": "METRIC",
      "dataType": "INTEGER"
    },
    {
      "name": "estimatedMinutesWatched",
      "columnType": "METRIC",
      "dataType": "INTEGER"
    }
  ],
  "rows": [
    ["2024-01-15", 1234, 5678],
    ["2024-01-16", 2345, 6789]
  ]
}
```

## Live Streaming APIs

### Live Broadcasts

#### POST /liveBroadcasts
Create a live broadcast.

**Request:**
```json
{
  "snippet": {
    "title": "My Live Stream",
    "description": "Live streaming event",
    "scheduledStartTime": "2024-02-01T20:00:00Z"
  },
  "status": {
    "privacyStatus": "public"
  },
  "contentDetails": {
    "enableAutoStart": true,
    "enableAutoStop": true,
    "enableDvr": true,
    "enableContentEncryption": false,
    "startWithSlate": false,
    "recordFromStart": true
  }
}
```

#### GET /liveBroadcasts
List live broadcasts.

#### PUT /liveBroadcasts
Update live broadcast.

### Live Streams

#### POST /liveStreams
Create a live stream.

#### GET /liveStreams
List live streams.

## Monetization APIs

### Channel Revenue

#### GET /revenue/channels/{channelId}
Get channel revenue data.

**Response:**
```json
{
  "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
  "period": {
    "startDate": "2024-01-01",
    "endDate": "2024-01-31"
  },
  "revenue": {
    "totalRevenue": 1234.56,
    "adRevenue": 987.65,
    "membershipRevenue": 123.45,
    "superChatRevenue": 67.89,
    "merchandiseRevenue": 55.57
  },
  "currency": "USD"
}
```

## Error Responses

All APIs return consistent error responses:

```json
{
  "error": {
    "code": 400,
    "message": "Bad Request",
    "errors": [
      {
        "domain": "youtube.parameter",
        "reason": "invalidParameter",
        "message": "Invalid value for parameter 'part'",
        "locationType": "parameter",
        "location": "part"
      }
    ]
  }
}
```

### Common Error Codes

- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Invalid or missing authentication
- `403 Forbidden`: Insufficient permissions or quota exceeded
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflict
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server error
- `503 Service Unavailable`: Service temporarily unavailable

## Rate Limiting

APIs are rate limited with different quotas:

- **Search API**: 100 requests per 100 seconds per user
- **Video Upload**: 6 uploads per day for unverified channels
- **Comments API**: 100 requests per 100 seconds per user
- **Analytics API**: 50,000 requests per day

Rate limit headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1642248600
```