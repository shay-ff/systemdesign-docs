# Consistent Hashing - Java Implementation

## Overview

This Java implementation provides a thread-safe consistent hashing system using TreeMap for efficient ring operations and MD5 hashing for key distribution.

## Features

- Thread-safe implementation using ReentrantReadWriteLock
- TreeMap-based hash ring for O(log N) lookups
- Configurable virtual nodes per physical node
- Load distribution analysis
- Comprehensive error handling

## Usage

```java
// Create hash ring with 3 virtual nodes per physical node
ConsistentHash ch = new ConsistentHash(3);

// Add nodes
ch.addNode("server1");
ch.addNode("server2");
ch.addNode("server3");

// Get node for a key
String node = ch.getNode("user:12345");
System.out.println("Key 'user:12345' maps to " + node);

// Remove a node
ch.removeNode("server2");

// Check load distribution
List<String> keys = Arrays.asList("key1", "key2", "key3", "key4", "key5");
Map<String, Integer> distribution = ch.getLoadDistribution(keys);
System.out.println("Load distribution: " + distribution);
```

## Compilation and Execution

```bash
javac *.java
java ConsistentHashDemo
```

## Time Complexity

- `addNode()`: O(V log N) where V is virtual nodes, N is total virtual nodes
- `removeNode()`: O(V log N)
- `getNode()`: O(log N)
- Space: O(N) where N is total virtual nodes

## Thread Safety

The implementation uses ReentrantReadWriteLock to ensure thread safety:
- Read operations (getNode) use read locks for concurrent access
- Write operations (addNode, removeNode) use write locks for exclusive access