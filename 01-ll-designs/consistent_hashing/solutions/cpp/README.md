# Consistent Hashing - C++ Implementation

## Overview

This C++ implementation provides a high-performance consistent hashing system using std::map for the hash ring and MD5 hashing for key distribution.

## Features

- STL-based implementation using std::map for O(log N) operations
- MD5 hashing for uniform key distribution
- Configurable virtual nodes per physical node
- Load distribution analysis
- Modern C++11/14 features

## Usage

```cpp
#include "consistent_hash.h"

// Create hash ring with 3 virtual nodes per physical node
ConsistentHash ch(3);

// Add nodes
ch.addNode("server1");
ch.addNode("server2");
ch.addNode("server3");

// Get node for a key
std::string node = ch.getNode("user:12345");
std::cout << "Key 'user:12345' maps to " << node << std::endl;

// Remove a node
ch.removeNode("server2");

// Check load distribution
std::vector<std::string> keys = {"key1", "key2", "key3", "key4", "key5"};
auto distribution = ch.getLoadDistribution(keys);
```

## Compilation

```bash
# Compile with OpenSSL for MD5 support
g++ -std=c++11 -o consistent_hash_demo consistent_hash.cpp -lssl -lcrypto

# Or compile with built-in hash function (less uniform distribution)
g++ -std=c++11 -DUSE_STD_HASH -o consistent_hash_demo consistent_hash.cpp
```

## Dependencies

- OpenSSL (for MD5 hashing) - recommended for better distribution
- Alternative: std::hash (compile with -DUSE_STD_HASH)

## Time Complexity

- `addNode()`: O(V log N) where V is virtual nodes, N is total virtual nodes
- `removeNode()`: O(V log N)
- `getNode()`: O(log N)
- Space: O(N) where N is total virtual nodes

## Performance Characteristics

- Optimized for high-throughput scenarios
- Memory efficient with minimal overhead
- Cache-friendly data structures