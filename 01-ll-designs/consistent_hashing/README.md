# Consistent Hashing

## Problem Statement

Design and implement a consistent hashing system that can distribute keys across multiple nodes while minimizing redistribution when nodes are added or removed from the system.

## Key Requirements

1. **Minimal Redistribution**: When a node is added or removed, only a small fraction of keys should need to be redistributed
2. **Load Distribution**: Keys should be distributed roughly evenly across all nodes
3. **Virtual Nodes**: Support virtual nodes to improve load distribution
4. **Dynamic Scaling**: Support adding and removing nodes at runtime

## Use Cases

- **Distributed Caching**: Distribute cache keys across multiple cache servers
- **Database Sharding**: Distribute data across multiple database shards
- **Load Balancing**: Distribute requests across multiple servers
- **Content Delivery Networks**: Route requests to geographically distributed servers

## Core Operations

1. `addNode(nodeId)` - Add a new node to the hash ring
2. `removeNode(nodeId)` - Remove a node from the hash ring
3. `getNode(key)` - Find which node should handle a given key
4. `getNodes()` - Get all active nodes in the system

## Architecture Overview

The consistent hashing implementation uses a hash ring where both nodes and keys are mapped to points on the ring. Each key is assigned to the first node encountered when moving clockwise around the ring.

Virtual nodes (replicas) are used to improve load distribution by placing multiple points for each physical node on the ring.

See `design.puml` for the visual architecture diagram.