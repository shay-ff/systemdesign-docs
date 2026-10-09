# URL Shortener - Java Implementation

A robust URL shortening service implemented in Java with enterprise-grade features and thread safety.

## Features

- **Thread Safety**: Concurrent access using ReentrantReadWriteLock
- **Type Safety**: Strong typing with enums and generics
- **Builder Pattern**: Fluent API for URL shortening requests
- **Exception Handling**: Comprehensive error handling with custom exceptions
- **Analytics**: Detailed click tracking and statistics
- **Validation**: Robust URL validation and sanitization

## Design Patterns

- **Builder Pattern**: ShortenRequest.Builder for fluent API
- **Strategy Pattern**: Different encoding strategies
- **Factory Pattern**: URL validation and encoding
- **Observer Pattern**: Analytics event handling (extensible)

## Compilation and Execution

```bash
# Compile all Java files
javac *.java

# Run the demo
java URLShortenerDemo
```

## Class Structure

```
URLShortenerDemo.java    - Main demo class
URLShortener.java        - Core URL shortener service
URLMetadata.java         - URL metadata and analytics
ShortenRequest.java      - Request builder pattern
Base62Encoder.java       - Base62 encoding utility
URLValidator.java        - URL validation utility
ClickEvent.java          - Analytics click event
URLShortenerException.java - Custom exceptions
```

## Key Features

1. **High Performance**: Optimized data structures and algorithms
2. **Memory Efficient**: Configurable analytics retention
3. **Thread Safe**: Concurrent operations with proper locking
4. **Extensible**: Interface-based design for easy extensions

## Example Usage

```java
URLShortener shortener = new URLShortener("https://short.ly/");

// Simple shortening
ShortenRequest request = new ShortenRequest.Builder("https://example.com")
    .withCustomAlias("my-link")
    .withExpirationDays(30)
    .withCreatorId("user123")
    .build();

URLShortener.Result result = shortener.shortenUrl(request);
String shortCode = result.getShortCode();
String fullUrl = result.getFullUrl();

// Expand URL
String originalUrl = shortener.expandUrl(shortCode);

// Get analytics
Analytics analytics = shortener.getAnalytics(shortCode);
```

## Thread Safety

- Uses `ReentrantReadWriteLock` for optimal read/write performance
- All public methods are thread-safe
- Atomic operations for counters and statistics
- Proper synchronization for analytics updates

## Performance Characteristics

- **Shorten URL**: O(1) average case
- **Expand URL**: O(1) hash map lookup
- **Analytics**: O(1) for basic stats
- **Memory**: O(n) where n = number of URLs + analytics events