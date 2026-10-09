package main

import (
	"crypto/md5"
	"encoding/binary"
	"errors"
	"fmt"
	"sort"
	"sync"
)

// hashRingEntry represents a single entry in the hash ring
type hashRingEntry struct {
	hash   uint64
	nodeID string
}

// ConsistentHash represents a consistent hashing ring with virtual nodes support
type ConsistentHash struct {
	virtualNodes int
	ring         []hashRingEntry // sorted by hash value
	nodes        map[string]bool // active nodes
	mutex        sync.RWMutex    // read-write mutex for thread safety
}

// NewConsistentHash creates a new consistent hash ring
func NewConsistentHash(virtualNodes int) *ConsistentHash {
	return &ConsistentHash{
		virtualNodes: virtualNodes,
		ring:         make([]hashRingEntry, 0),
		nodes:        make(map[string]bool),
	}
}

// hash generates a hash value for a key using MD5
func (ch *ConsistentHash) hash(key string) uint64 {
	hasher := md5.New()
	hasher.Write([]byte(key))
	digest := hasher.Sum(nil)
	
	// Convert first 8 bytes of MD5 hash to uint64
	return binary.BigEndian.Uint64(digest[:8])
}

// AddNode adds a node to the hash ring
func (ch *ConsistentHash) AddNode(nodeID string) {
	ch.mutex.Lock()
	defer ch.mutex.Unlock()
	
	if ch.nodes[nodeID] {
		return // Node already exists
	}
	
	ch.nodes[nodeID] = true
	
	// Add virtual nodes to the ring
	for i := 0; i < ch.virtualNodes; i++ {
		virtualKey := fmt.Sprintf("%s:%d", nodeID, i)
		hashValue := ch.hash(virtualKey)
		
		entry := hashRingEntry{
			hash:   hashValue,
			nodeID: nodeID,
		}
		
		ch.ring = append(ch.ring, entry)
	}
	
	// Keep ring sorted by hash value
	sort.Slice(ch.ring, func(i, j int) bool {
		return ch.ring[i].hash < ch.ring[j].hash
	})
}

// RemoveNode removes a node from the hash ring
func (ch *ConsistentHash) RemoveNode(nodeID string) {
	ch.mutex.Lock()
	defer ch.mutex.Unlock()
	
	if !ch.nodes[nodeID] {
		return // Node doesn't exist
	}
	
	delete(ch.nodes, nodeID)
	
	// Remove virtual nodes from the ring
	newRing := make([]hashRingEntry, 0, len(ch.ring))
	for _, entry := range ch.ring {
		if entry.nodeID != nodeID {
			newRing = append(newRing, entry)
		}
	}
	ch.ring = newRing
}

// GetNode returns the node responsible for a given key
func (ch *ConsistentHash) GetNode(key string) (string, error) {
	ch.mutex.RLock()
	defer ch.mutex.RUnlock()
	
	if len(ch.ring) == 0 {
		return "", errors.New("no nodes available")
	}
	
	hashValue := ch.hash(key)
	
	// Find the first node clockwise from the key's hash using binary search
	idx := sort.Search(len(ch.ring), func(i int) bool {
		return ch.ring[i].hash >= hashValue
	})
	
	// Wrap around to the beginning if we're past the end
	if idx == len(ch.ring) {
		idx = 0
	}
	
	return ch.ring[idx].nodeID, nil
}

// GetNodes returns all active nodes in the system
func (ch *ConsistentHash) GetNodes() []string {
	ch.mutex.RLock()
	defer ch.mutex.RUnlock()
	
	nodes := make([]string, 0, len(ch.nodes))
	for nodeID := range ch.nodes {
		nodes = append(nodes, nodeID)
	}
	
	sort.Strings(nodes) // Return sorted for consistency
	return nodes
}

// GetLoadDistribution analyzes load distribution for a set of keys
func (ch *ConsistentHash) GetLoadDistribution(keys []string) map[string]int {
	distribution := make(map[string]int)
	
	for _, key := range keys {
		node, err := ch.GetNode(key)
		if err == nil {
			distribution[node]++
		}
	}
	
	return distribution
}

// GetRingInfo returns information about the current ring state
func (ch *ConsistentHash) GetRingInfo() map[string]interface{} {
	ch.mutex.RLock()
	defer ch.mutex.RUnlock()
	
	return map[string]interface{}{
		"totalNodes":           len(ch.nodes),
		"totalVirtualNodes":    len(ch.ring),
		"virtualNodesPerNode":  ch.virtualNodes,
		"nodes":               ch.GetNodes(),
	}
}

// PrintRing prints the current state of the hash ring (for debugging)
func (ch *ConsistentHash) PrintRing() {
	ch.mutex.RLock()
	defer ch.mutex.RUnlock()
	
	fmt.Println("Hash Ring State:")
	for _, entry := range ch.ring {
		fmt.Printf("  Hash: %016x -> Node: %s\n", entry.hash, entry.nodeID)
	}
}

// demonstrateConsistentHashing shows the functionality of consistent hashing
func demonstrateConsistentHashing() {
	fmt.Println("=== Consistent Hashing Demo ===\n")
	
	// Create hash ring
	ch := NewConsistentHash(3)
	
	// Add initial nodes
	nodes := []string{"server1", "server2", "server3"}
	for _, node := range nodes {
		ch.AddNode(node)
	}
	
	fmt.Printf("Added nodes: %v\n", nodes)
	fmt.Printf("Ring info: %v\n\n", ch.GetRingInfo())
	
	// Test key distribution
	testKeys := make([]string, 10)
	for i := 1; i <= 10; i++ {
		testKeys[i-1] = fmt.Sprintf("user:%d", i)
	}
	
	fmt.Println("Initial key distribution:")
	distribution := ch.GetLoadDistribution(testKeys)
	for node, count := range distribution {
		fmt.Printf("  %s: %d keys\n", node, count)
	}
	
	// Show specific key mappings
	fmt.Println("\nKey mappings:")
	for i := 0; i < 5 && i < len(testKeys); i++ {
		key := testKeys[i]
		node, err := ch.GetNode(key)
		if err != nil {
			fmt.Printf("  %s -> ERROR: %v\n", key, err)
		} else {
			fmt.Printf("  %s -> %s\n", key, node)
		}
	}
	
	// Remove a node and show redistribution
	fmt.Println("\nRemoving 'server2'...")
	ch.RemoveNode("server2")
	
	fmt.Println("New key distribution:")
	newDistribution := ch.GetLoadDistribution(testKeys)
	for node, count := range newDistribution {
		fmt.Printf("  %s: %d keys\n", node, count)
	}
	
	fmt.Printf("\nRing info after removal: %v\n", ch.GetRingInfo())
	
	// Add a new node
	fmt.Println("\nAdding 'server4'...")
	ch.AddNode("server4")
	
	finalDistribution := ch.GetLoadDistribution(testKeys)
	fmt.Println("Final key distribution:")
	for node, count := range finalDistribution {
		fmt.Printf("  %s: %d keys\n", node, count)
	}
	
	// Demonstrate load balancing with more keys
	fmt.Println("\n=== Load Balancing Test ===")
	manyKeys := make([]string, 1000)
	for i := 1; i <= 1000; i++ {
		manyKeys[i-1] = fmt.Sprintf("key:%d", i)
	}
	
	loadTest := ch.GetLoadDistribution(manyKeys)
	fmt.Println("Distribution of 1000 keys:")
	for node, count := range loadTest {
		percentage := float64(count) * 100.0 / float64(len(manyKeys))
		fmt.Printf("  %s: %d keys (%.1f%%)\n", node, count, percentage)
	}
}

func main() {
	demonstrateConsistentHashing()
}