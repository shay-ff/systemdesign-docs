# LRU Cache - Java Implementation

## Overview

This directory contains a Java implementation of an LRU (Least Recently Used) cache using a doubly-linked list and HashMap for O(1) operations.

## Files

- `LRUCache.java` - Complete implementation with test cases

## Requirements

- Java 8+ (uses HashMap and modern Java features)
- JDK for compilation

## Compiling and Running

```bash
# Navigate to the java directory
cd 01-ll-designs/lru_cache/solutions/java/

# Compile the Java file
javac LRUCache.java

# Run the compiled class
java LRUCache
```

## Expected Output

```
Testing LRU Cache Implementation
========================================
Creating cache with capacity 2
put(1, 1)
put(2, 2)
get(1) = 1
put(3, 3) - evicts key 2
get(2) = -1
put(4, 4) - evicts key 1
get(1) = -1
get(3) = 3
get(4) = 4
```

## Key Features

- **Strong typing** with generics support (can be extended to `LRUCache<K, V>`)
- **Automatic memory management** via garbage collection
- **Clean OOP design** with proper encapsulation
- **Javadoc comments** for API documentation

## Usage Example

```java
// Create cache with capacity 100
LRUCache cache = new LRUCache(100);

// Add items
cache.put(1, 42);
cache.put(2, 84);

// Retrieve items
int value = cache.get(1);  // Returns 42
int missing = cache.get(3); // Returns -1
```

## Generic Version

For production use, consider extending to support generic types:

```java
public class LRUCache<K, V> {
    private final Map<K, Node<K, V>> cache;
    // ... implementation with generic types
}
```

## Performance Characteristics

- **Time Complexity**: O(1) for both get and put operations
- **Space Complexity**: O(capacity)
- **Memory Overhead**: ~32-48 bytes per cached item (JVM object overhead)
- **JIT Optimization**: Performance improves with warm-up

## Testing

The implementation includes basic test cases in the `main()` method. For production use, consider adding:

- JUnit test cases
- Performance benchmarks with JMH
- Memory profiling with JProfiler
- Concurrent access testing

## Thread Safety

This implementation is **not thread-safe**. For concurrent use, consider:

- Synchronizing methods with `synchronized` keyword
- Using `ConcurrentHashMap` with additional locking
- Implementing lock-free algorithms with atomic operations