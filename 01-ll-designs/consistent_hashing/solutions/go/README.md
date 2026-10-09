# Consistent Hashing - Go Implementation

## Overview

This Go implementation provides a concurrent-safe consistent hashing system using Go's built-in sort package for efficient ring operations and MD5 hashing for key distribution.

## Features

- Concurrent-safe implementation using sync.RWMutex
- Efficient ring operations using sorted slice and binary search
- MD5 hashing for uniform key distribution
- Configurable virtual nodes per physical node
- Load distribution analysis
- Idiomatic Go code with proper error handling

## Usage

```go
package main

import (
    "fmt"
    "log"
)

func main() {
    // Create hash ring with 3 virtual nodes per physical node
    ch := NewConsistentHash(3)
    
    // Add nodes
    ch.AddNode("server1")
    ch.AddNode("server2")
    ch.AddNode("server3")
    
    // Get node for a key
    node, err := ch.GetNode("user:12345")
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Key 'user:12345' maps to %s\n", node)
    
    // Remove a node
    ch.RemoveNode("server2")
    
    // Check load distribution
    keys := []string{"key1", "key2", "key3", "key4", "key5"}
    distribution := ch.GetLoadDistribution(keys)
    fmt.Printf("Load distribution: %v\n", distribution)
}
```

## Running the Example

```bash
go run consistent_hash.go
```

## Building

```bash
go build -o consistent_hash_demo consistent_hash.go
./consistent_hash_demo
```

## Time Complexity

- `AddNode()`: O(V log N) where V is virtual nodes, N is total virtual nodes
- `RemoveNode()`: O(V log N)
- `GetNode()`: O(log N)
- Space: O(N) where N is total virtual nodes

## Concurrency

The implementation uses sync.RWMutex for thread safety:
- Read operations (GetNode) use read locks for concurrent access
- Write operations (AddNode, RemoveNode) use write locks for exclusive access

## Performance Characteristics

- Optimized for Go's runtime and garbage collector
- Minimal memory allocations in hot paths
- Efficient binary search for node lookup