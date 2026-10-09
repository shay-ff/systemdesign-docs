# Consistent Hashing - Python Implementation

## Overview

This Python implementation provides a consistent hashing system with virtual nodes support for improved load distribution.

## Features

- Hash ring with configurable virtual nodes
- MD5 hashing for key and node distribution
- Dynamic node addition and removal
- Load distribution statistics
- Thread-safe operations

## Usage

```python
from consistent_hash import ConsistentHash

# Create hash ring with 3 virtual nodes per physical node
ch = ConsistentHash(virtual_nodes=3)

# Add nodes
ch.add_node("server1")
ch.add_node("server2")
ch.add_node("server3")

# Get node for a key
node = ch.get_node("user:12345")
print(f"Key 'user:12345' maps to {node}")

# Remove a node
ch.remove_node("server2")

# Check load distribution
distribution = ch.get_load_distribution(["key1", "key2", "key3", "key4", "key5"])
print("Load distribution:", distribution)
```

## Running the Example

```bash
python consistent_hash.py
```

## Time Complexity

- `add_node()`: O(V log N) where V is virtual nodes, N is total virtual nodes
- `remove_node()`: O(V log N)
- `get_node()`: O(log N)
- Space: O(N) where N is total virtual nodes

## Performance Characteristics

- Minimal key redistribution when nodes change (approximately 1/N of keys)
- Good load distribution with virtual nodes
- Logarithmic lookup time
- Memory efficient storage