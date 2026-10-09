# URL Shortener - Python Implementation

A comprehensive URL shortening service with analytics, expiration, and custom aliases.

## Features

- **Base62 Encoding**: Efficient short URL generation
- **Custom Aliases**: Support for user-defined short codes
- **Expiration**: Time-based URL expiration
- **Analytics**: Click tracking and statistics
- **Validation**: URL format validation and sanitization
- **Thread Safety**: Concurrent access support

## Design Patterns Used

- **Strategy Pattern**: Different encoding strategies (Base62, Custom)
- **Builder Pattern**: URL configuration with optional parameters
- **Observer Pattern**: Analytics event tracking
- **Singleton Pattern**: Global configuration management

## Time Complexity

- **Shorten URL**: O(1) average case
- **Expand URL**: O(1) hash map lookup
- **Analytics Query**: O(1) for basic stats, O(n) for detailed logs

## Space Complexity

- **Storage**: O(n) where n = number of URLs
- **Analytics**: O(m) where m = number of clicks (configurable retention)

## Usage

```python
python url_shortener.py
```

## Example Output

```
=== URL Shortener Demo ===

Shortened https://example.com/very/long/path -> abc123
Expanded abc123 -> https://example.com/very/long/path
Click count: 1

Custom alias 'my-link' -> https://custom.example.com
Analytics: 2 total clicks, last accessed: 2023-11-02 10:30:45
```

## Key Features

1. **Collision Handling**: Automatic retry with different IDs
2. **URL Validation**: Comprehensive URL format checking
3. **Expiration Management**: Automatic cleanup of expired URLs
4. **Analytics Tracking**: Detailed click statistics and timestamps
5. **Custom Aliases**: User-friendly custom short codes

## Configuration Options

- Base URL for short links
- Default expiration time
- Analytics retention period
- Maximum custom alias length
- Rate limiting parameters