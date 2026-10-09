# LRU Cache - Python Implementation

## Overview

This directory contains a Python implementation of an LRU (Least Recently Used) cache using a doubly-linked list and dictionary for O(1) operations.

## Files

- `lru_cache.py` - Complete implementation with test cases

## Requirements

- Python 3.6+ (uses type hints)

## Running the Code

```bash
# Navigate to the python directory
cd 01-ll-designs/lru_cache/solutions/python/

# Run the implementation
python lru_cache.py
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

- **Type hints** for better code documentation
- **Comprehensive docstrings** explaining each method
- **Clean separation** of concerns with helper methods
- **Memory efficient** implementation using `__slots__` could be added for optimization

## Usage Example

```python
from lru_cache import LRUCache

# Create cache with capacity 3
cache = LRUCache(3)

# Add items
cache.put("user:123", {"name": "Alice", "age": 30})
cache.put("user:456", {"name": "Bob", "age": 25})

# Retrieve items
user_data = cache.get("user:123")  # Returns user data
missing = cache.get("user:999")    # Returns -1
```

## Performance Characteristics

- **Time Complexity**: O(1) for both get and put operations
- **Space Complexity**: O(capacity)
- **Memory Overhead**: ~48-64 bytes per cached item (Python object overhead)

## Testing

The implementation includes basic test cases in the `main()` function. For production use, consider adding:

- Unit tests with pytest
- Property-based testing with hypothesis
- Performance benchmarks
- Memory usage profiling