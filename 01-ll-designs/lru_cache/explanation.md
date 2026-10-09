# LRU Cache - Design Discussion and Trade-offs

## Problem Statement

Design and implement a data structure for a Least Recently Used (LRU) cache. The cache should support the following operations in **O(1)** time complexity:

- `get(key)`: Get the value of the key if it exists in the cache, otherwise return -1
- `put(key, value)`: Update the value of the key if it exists, otherwise add the key-value pair to the cache

When the cache reaches its capacity, it should invalidate the least recently used item before inserting a new item.

## Core Design Concepts

### Why LRU?

LRU (Least Recently Used) is a popular cache eviction policy based on the principle of **temporal locality** - recently accessed items are more likely to be accessed again soon. This makes LRU effective for many real-world scenarios:

- **Web browsers**: Recently visited pages
- **Operating systems**: Page replacement algorithms
- **Database systems**: Buffer pool management
- **CDNs**: Content caching strategies

### Key Insight: Dual Data Structure Approach

The challenge is achieving O(1) time complexity for both operations. This requires:

1. **Fast lookup**: Hash map provides O(1) key-to-value mapping
2. **Fast reordering**: Doubly-linked list provides O(1) insertion/deletion

## Architecture Overview

```
Hash Map (O(1) lookup)
┌─────────────────────┐
│ key1 → node1       │     Doubly-Linked List (O(1) reorder)
│ key2 → node2       │     ┌──────────────────────────────────┐
│ key3 → node3       │     │ [HEAD] ↔ [node1] ↔ [node2] ↔ [TAIL] │
└─────────────────────┘     └──────────────────────────────────┘
                                 ↑                    ↑
                            Most Recent          Least Recent
```

### Components

1. **Hash Map**: Maps keys to list nodes for O(1) access
2. **Doubly-Linked List**: Maintains access order
   - **Head**: Most recently used items
   - **Tail**: Least recently used items
3. **Dummy Nodes**: Simplify edge cases (empty list, single item)

## Implementation Details

### Node Structure

```python
class Node:
    def __init__(self, key=0, value=0):
        self.key = key      # Store key for reverse lookup during eviction
        self.value = value  # Actual cached value
        self.prev = None    # Previous node in list
        self.next = None    # Next node in list
```

**Key Design Decision**: Store both key and value in nodes to enable efficient eviction (need key to remove from hash map).

### Core Operations

#### Get Operation

```
1. Check if key exists in hash map
2. If not found: return -1
3. If found:
   - Get node from hash map
   - Move node to head (mark as recently used)
   - Return node value
```

#### Put Operation

```
1. Check if key already exists
2. If exists:
   - Update value
   - Move to head
3. If new key:
   - Check capacity
   - If at capacity: evict LRU item (tail)
   - Create new node
   - Add to head and hash map
```

### Dummy Head/Tail Pattern

Using dummy nodes eliminates edge case handling:

```
Without dummies: Need special cases for empty list, single item
With dummies:    Always have head.next and tail.prev available

[DUMMY_HEAD] ↔ [actual nodes...] ↔ [DUMMY_TAIL]
```

## Language-Specific Implementations

### Python Implementation

- **Strengths**: Clean, readable code with type hints
- **Memory**: Higher overhead due to object model
- **Performance**: Slower than compiled languages but excellent for prototyping

### Java Implementation

- **Strengths**: Strong typing, good performance, extensive standard library
- **Memory**: Automatic garbage collection handles cleanup
- **Performance**: JIT compilation provides good runtime performance

### C++ Implementation

- **Strengths**: Maximum performance, fine-grained memory control
- **Memory**: Manual memory management (requires destructor)
- **Performance**: Fastest execution, minimal overhead

### Go Implementation

- **Strengths**: Simple syntax, built-in concurrency support
- **Memory**: Garbage collected but efficient
- **Performance**: Good balance of simplicity and speed

## Time and Space Complexity

| Operation | Time Complexity | Space Complexity |
| --------- | --------------- | ---------------- |
| get()     | O(1)            | O(1)             |
| put()     | O(1)            | O(1)             |
| Overall   | -               | O(capacity)      |

**Why O(1)?**

- Hash map operations: O(1) average case
- Linked list operations: O(1) with direct node references
- No iteration or searching required

## Trade-offs and Alternatives

### LRU vs Other Eviction Policies

| Policy     | Pros                                | Cons                      | Use Case                   |
| ---------- | ----------------------------------- | ------------------------- | -------------------------- |
| **LRU**    | Good temporal locality, predictable | Complex implementation    | General purpose            |
| **FIFO**   | Simple implementation               | Ignores access patterns   | Simple queues              |
| **LFU**    | Good for frequency-based patterns   | Complex, slow adaptation  | Analytics workloads        |
| **Random** | Very simple, no overhead            | Unpredictable performance | Memory-constrained systems |

### Implementation Alternatives

#### 1. OrderedDict/LinkedHashMap Approach

```python
# Python built-in solution
from collections import OrderedDict

class LRUCache(OrderedDict):
    def __init__(self, capacity):
        self.capacity = capacity

    def get(self, key):
        if key not in self:
            return -1
        self.move_to_end(key)  # Mark as recently used
        return self[key]
```

**Pros**: Simpler code, leverages standard library
**Cons**: Less educational, potential performance overhead

#### 2. Array-Based Circular Buffer

**Pros**: Better cache locality, lower memory overhead
**Cons**: Complex index management, harder to implement O(1) random access

### Memory Considerations

**Memory Overhead per Item**:

- Hash map entry: ~24-32 bytes (key, value, hash, metadata)
- List node: ~24-32 bytes (key, value, prev, next pointers)
- **Total**: ~48-64 bytes per cached item

**Memory Layout**:

```
Fragmented (linked list):  [node] ... [node] ... [node]
Contiguous (array):        [item][item][item][item]
```

## Real-World Applications

### 1. CPU Cache Design

Modern processors use LRU-approximation algorithms for cache replacement:

- **L1/L2 caches**: Hardware-implemented LRU
- **TLB**: Translation lookaside buffer management

### 2. Database Buffer Pools

```sql
-- PostgreSQL shared_buffers uses clock-sweep (LRU approximation)
-- MySQL InnoDB buffer pool uses LRU with young/old sublists
```

### 3. Web Application Caching

```python
# Redis with LRU eviction
redis_client.config_set('maxmemory-policy', 'allkeys-lru')

# Application-level caching
@lru_cache(maxsize=1000)
def expensive_computation(param):
    return complex_calculation(param)
```

### 4. Operating System Page Replacement

```c
// Linux kernel uses LRU approximation for page replacement
// /proc/meminfo shows page cache statistics
```

## Performance Benchmarks

### Theoretical Analysis

- **Cache hit**: 1 hash lookup + 4 pointer updates = ~5 operations
- **Cache miss**: 1 hash lookup + eviction + insertion = ~10 operations
- **Memory accesses**: 2-3 per operation (hash table + linked list)

### Practical Considerations

1. **Hash collisions**: Can degrade to O(n) in worst case
2. **Memory locality**: Linked list has poor cache performance
3. **Allocation overhead**: Dynamic allocation can be expensive

### Optimization Strategies

1. **Pre-allocate nodes**: Use object pools to reduce allocation overhead
2. **Custom hash functions**: Minimize collisions for specific key patterns
3. **Memory pools**: Reduce fragmentation for better cache performance

## Common Pitfalls and Solutions

### 1. Memory Leaks (C++)

**Problem**: Forgetting to delete evicted nodes
**Solution**: Implement proper destructor and RAII principles

### 2. Circular References

**Problem**: Incorrect pointer updates causing cycles
**Solution**: Careful pointer manipulation with dummy nodes

### 3. Capacity Edge Cases

**Problem**: Handling capacity = 0 or capacity = 1
**Solution**: Validate input and test edge cases thoroughly

### 4. Thread Safety

**Problem**: Concurrent access corrupting data structures
**Solution**: Add mutex/locks or use lock-free algorithms

## Extensions and Variations

### 1. TTL (Time-To-Live) Support

```python
class TTLLRUCache:
    def __init__(self, capacity, ttl_seconds):
        self.ttl = ttl_seconds
        # Add timestamp to each node
```

### 2. Multi-Level LRU

```python
class MultiLevelLRU:
    def __init__(self, l1_size, l2_size):
        self.l1_cache = LRUCache(l1_size)  # Fast tier
        self.l2_cache = LRUCache(l2_size)  # Slower tier
```

### 3. Weighted LRU

```python
class WeightedLRU:
    # Items have different sizes/costs
    # Evict based on total weight, not just count
```

### 4. Concurrent LRU

```java
public class ConcurrentLRU<K, V> {
    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final ReentrantReadWriteLock lock;
    // Thread-safe implementation with read/write locks
}
```

## Testing Strategy

### Unit Tests

1. **Basic operations**: get/put with various scenarios
2. **Capacity limits**: Eviction behavior
3. **Edge cases**: Empty cache, single item, capacity changes
4. **Memory**: No leaks, proper cleanup

### Performance Tests

1. **Throughput**: Operations per second
2. **Latency**: Response time distribution
3. **Memory usage**: Peak and steady-state consumption
4. **Scalability**: Performance vs cache size

### Integration Tests

1. **Real workloads**: Web request patterns, database queries
2. **Stress testing**: High concurrency, memory pressure
3. **Failure scenarios**: Out of memory, corrupted state

## Conclusion

The LRU cache demonstrates elegant problem-solving through the combination of complementary data structures. The hash map provides fast access while the doubly-linked list enables efficient reordering. This design pattern appears throughout computer systems, from CPU caches to distributed systems.

**Key Takeaways**:

1. **Dual data structures** can solve complex time complexity requirements
2. **Dummy nodes** simplify edge case handling in linked structures
3. **Memory management** varies significantly across programming languages
4. **Real-world systems** often use LRU approximations for better performance
5. **Testing** must cover both correctness and performance characteristics

The LRU cache serves as an excellent introduction to system design principles that scale from individual algorithms to distributed architectures.
