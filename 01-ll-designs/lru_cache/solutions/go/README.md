# LRU Cache - Go Implementation

## Overview

This directory contains a Go implementation of an LRU (Least Recently Used) cache using a doubly-linked list and map for O(1) operations.

## Files

- `lru_cache.go` - Complete implementation with test cases

## Requirements

- Go 1.13+ (uses Go modules)

## Running the Code

```bash
# Navigate to the go directory
cd 01-ll-designs/lru_cache/solutions/go/

# Run the implementation
go run lru_cache.go
```

## Building and Running

```bash
# Build the binary
go build -o lru_cache lru_cache.go

# Run the binary
./lru_cache
```

## Expected Output

```
Testing LRU Cache Implementation
========================================
Creating cache with capacity 2
Put(1, 1)
Put(2, 2)
Get(1) = 1
Put(3, 3) - evicts key 2
Get(2) = -1
Put(4, 4) - evicts key 1
Get(1) = -1
Get(3) = 3
Get(4) = 4

Final cache size: 2
```

## Key Features

- **Idiomatic Go** with proper naming conventions
- **Pointer-based** linked list for efficiency
- **Garbage collection** handles memory management
- **Constructor pattern** with `NewLRUCache()`
- **Method receivers** for clean API design

## Usage Example

```go
package main

import "fmt"

func main() {
    // Create cache with capacity 10
    cache := NewLRUCache(10)
    
    // Add items
    cache.Put(1, 100)
    cache.Put(2, 200)
    
    // Retrieve items
    value := cache.Get(1)  // Returns 100
    missing := cache.Get(3) // Returns -1
    
    fmt.Printf("Cache size: %d\n", cache.Size())
}
```

## Generic Version (Go 1.18+)

For Go 1.18+, you can create a generic version:

```go
type LRUCache[K comparable, V any] struct {
    capacity int
    cache    map[K]*Node[K, V]
    head     *Node[K, V]
    tail     *Node[K, V]
}

func NewLRUCache[K comparable, V any](capacity int) *LRUCache[K, V] {
    // ... generic implementation
}
```

## Performance Characteristics

- **Time Complexity**: O(1) for both Get and Put operations
- **Space Complexity**: O(capacity)
- **Memory Overhead**: ~40-56 bytes per cached item (Go runtime overhead)
- **Garbage Collection**: Automatic cleanup of evicted nodes

## Testing

The implementation includes basic test cases in the `main()` function. For production use, consider adding:

```bash
# Create test file
# lru_cache_test.go

# Run tests
go test -v

# Run benchmarks
go test -bench=.

# Run with race detection
go test -race
```

## Concurrency

This implementation is **not thread-safe**. For concurrent use, consider:

- Adding `sync.RWMutex` for read/write locking
- Using channels for serialized access
- Implementing lock-free algorithms with atomic operations

```go
type ConcurrentLRU struct {
    mu sync.RWMutex
    cache *LRUCache
}

func (c *ConcurrentLRU) Get(key int) int {
    c.mu.RLock()
    defer c.mu.RUnlock()
    return c.cache.Get(key)
}
```

## Module Support

For use as a module, create `go.mod`:

```bash
go mod init lru-cache
go mod tidy
```