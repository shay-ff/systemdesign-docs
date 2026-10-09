# LRU Cache - Low Level Design

A comprehensive implementation of Least Recently Used (LRU) cache in multiple programming languages, demonstrating O(1) get and put operations using doubly-linked list and hash map data structures.

## Problem Statement

Design a data structure that follows the constraints of a Least Recently Used (LRU) cache with the following operations:
- `get(key)`: Get the value of the key if it exists, otherwise return -1
- `put(key, value)`: Update the value if key exists, otherwise add the key-value pair

When the cache reaches capacity, invalidate the least recently used item before inserting new items.

## Files Structure

```
lru_cache/
├── README.md              # This file
├── design.puml           # PlantUML class diagram
├── explanation.md        # Detailed design discussion
├── src/
│   └── lru_cache.cpp    # Enhanced C++ implementation
└── solutions/
    ├── python/
    │   ├── lru_cache.py # Python implementation
    │   └── README.md    # Python-specific docs
    ├── java/
    │   ├── LRUCache.java # Java implementation
    │   └── README.md     # Java-specific docs
    └── go/
        ├── lru_cache.go  # Go implementation
        └── README.md     # Go-specific docs
```

## Quick Start

### C++ Implementation
```bash
cd src/
g++ -std=c++17 lru_cache.cpp -o lru && ./lru
```

### Python Implementation
```bash
cd solutions/python/
python lru_cache.py
```

### Java Implementation
```bash
cd solutions/java/
javac LRUCache.java && java LRUCache
```

### Go Implementation
```bash
cd solutions/go/
go run lru_cache.go
```

## Key Concepts

- **Time Complexity**: O(1) for both get and put operations
- **Space Complexity**: O(capacity)
- **Data Structures**: Doubly-linked list + Hash map/Dictionary
- **Design Pattern**: Combination of fast lookup and efficient reordering

## Core Algorithm

1. **Hash Map**: Provides O(1) key-to-node mapping
2. **Doubly-Linked List**: Maintains access order with O(1) insertion/deletion
3. **Dummy Nodes**: Simplify edge case handling

## Learning Objectives

After studying this implementation, you should understand:
- How to achieve O(1) time complexity for cache operations
- The trade-offs between different eviction policies
- Memory management across different programming languages
- Real-world applications in system design

## Further Reading

- See `explanation.md` for detailed design discussion and trade-offs
- Each language directory contains specific implementation notes
- `design.puml` shows the class structure and relationships

## Related Concepts

- Cache replacement policies (FIFO, LFU, Random)
- Memory hierarchy in computer systems
- Database buffer pool management
- CPU cache design principles