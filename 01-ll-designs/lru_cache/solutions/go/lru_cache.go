/*
LRU Cache Implementation in Go

A Least Recently Used (LRU) cache implementation using a doubly-linked list
and map for O(1) get and put operations.

Time Complexity:
- Get(): O(1)
- Put(): O(1)

Space Complexity: O(capacity)
*/

package main

import "fmt"

// Node represents a doubly-linked list node for LRU cache
type Node struct {
	key   int
	value int
	prev  *Node
	next  *Node
}

// NewNode creates a new node with given key and value
func NewNode(key, value int) *Node {
	return &Node{
		key:   key,
		value: value,
	}
}

// LRUCache represents an LRU cache with O(1) operations
//
// Uses a combination of:
// - Map for O(1) key lookup
// - Doubly-linked list for O(1) insertion/deletion
type LRUCache struct {
	capacity int
	cache    map[int]*Node
	head     *Node
	tail     *Node
}

// NewLRUCache initializes LRU cache with given capacity
func NewLRUCache(capacity int) *LRUCache {
	cache := &LRUCache{
		capacity: capacity,
		cache:    make(map[int]*Node),
		head:     &Node{}, // dummy head
		tail:     &Node{}, // dummy tail
	}
	
	// Connect dummy nodes
	cache.head.next = cache.tail
	cache.tail.prev = cache.head
	
	return cache
}

// removeNode removes node from doubly-linked list
func (lru *LRUCache) removeNode(node *Node) {
	node.prev.next = node.next
	node.next.prev = node.prev
}

// addToHead adds node right after head (most recently used position)
func (lru *LRUCache) addToHead(node *Node) {
	node.prev = lru.head
	node.next = lru.head.next
	lru.head.next.prev = node
	lru.head.next = node
}

// moveToHead moves existing node to head (mark as recently used)
func (lru *LRUCache) moveToHead(node *Node) {
	lru.removeNode(node)
	lru.addToHead(node)
}

// removeTail removes and returns the last node (least recently used)
func (lru *LRUCache) removeTail() *Node {
	lastNode := lru.tail.prev
	lru.removeNode(lastNode)
	return lastNode
}

// Get retrieves value by key and marks as recently used
//
// Returns value if key exists, -1 otherwise
func (lru *LRUCache) Get(key int) int {
	if node, exists := lru.cache[key]; exists {
		// Move to head (mark as recently used)
		lru.moveToHead(node)
		return node.value
	}
	return -1
}

// Put inserts or updates key-value pair
func (lru *LRUCache) Put(key, value int) {
	if node, exists := lru.cache[key]; exists {
		// Update existing key
		node.value = value
		lru.moveToHead(node)
	} else {
		// Insert new key
		newNode := NewNode(key, value)
		
		if len(lru.cache) >= lru.capacity {
			// Remove least recently used item
			tailNode := lru.removeTail()
			delete(lru.cache, tailNode.key)
		}
		
		// Add new node
		lru.cache[key] = newNode
		lru.addToHead(newNode)
	}
}

// Size returns current number of items in cache
func (lru *LRUCache) Size() int {
	return len(lru.cache)
}

// main function to test the LRU cache implementation
func main() {
	fmt.Println("Testing LRU Cache Implementation")
	fmt.Println("========================================")
	
	cache := NewLRUCache(2)
	
	fmt.Println("Creating cache with capacity 2")
	
	cache.Put(1, 1)
	fmt.Println("Put(1, 1)")
	
	cache.Put(2, 2)
	fmt.Println("Put(2, 2)")
	
	result := cache.Get(1)
	fmt.Printf("Get(1) = %d\n", result) // Should return 1
	
	cache.Put(3, 3) // Evicts key 2
	fmt.Println("Put(3, 3) - evicts key 2")
	
	result = cache.Get(2)
	fmt.Printf("Get(2) = %d\n", result) // Should return -1 (not found)
	
	cache.Put(4, 4) // Evicts key 1
	fmt.Println("Put(4, 4) - evicts key 1")
	
	result = cache.Get(1)
	fmt.Printf("Get(1) = %d\n", result) // Should return -1 (not found)
	
	result = cache.Get(3)
	fmt.Printf("Get(3) = %d\n", result) // Should return 3
	
	result = cache.Get(4)
	fmt.Printf("Get(4) = %d\n", result) // Should return 4
	
	fmt.Printf("\nFinal cache size: %d\n", cache.Size())
}