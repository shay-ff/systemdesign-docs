/**
 * LRU Cache Implementation in Java
 * 
 * A Least Recently Used (LRU) cache implementation using a doubly-linked list
 * and HashMap for O(1) get and put operations.
 * 
 * Time Complexity:
 * - get(): O(1)
 * - put(): O(1)
 * 
 * Space Complexity: O(capacity)
 */

import java.util.HashMap;
import java.util.Map;

/**
 * Doubly-linked list node for LRU cache.
 */
class Node {
    int key;
    int value;
    Node prev;
    Node next;
    
    public Node() {}
    
    public Node(int key, int value) {
        this.key = key;
        this.value = value;
    }
}

/**
 * LRU Cache implementation with O(1) operations.
 * 
 * Uses a combination of:
 * - HashMap for O(1) key lookup
 * - Doubly-linked list for O(1) insertion/deletion
 */
public class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> cache;
    private final Node head;
    private final Node tail;
    
    /**
     * Initialize LRU cache with given capacity.
     * 
     * @param capacity Maximum number of key-value pairs to store
     */
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        
        // Create dummy head and tail nodes
        this.head = new Node();
        this.tail = new Node();
        this.head.next = this.tail;
        this.tail.prev = this.head;
    }
    
    /**
     * Remove node from doubly-linked list.
     * 
     * @param node Node to remove
     */
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    /**
     * Add node right after head (most recently used position).
     * 
     * @param node Node to add
     */
    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    /**
     * Move existing node to head (mark as recently used).
     * 
     * @param node Node to move
     */
    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }
    
    /**
     * Remove and return the last node (least recently used).
     * 
     * @return The removed node
     */
    private Node removeTail() {
        Node lastNode = tail.prev;
        removeNode(lastNode);
        return lastNode;
    }
    
    /**
     * Get value by key and mark as recently used.
     * 
     * @param key Key to look up
     * @return Value if key exists, -1 otherwise
     */
    public int get(int key) {
        Node node = cache.get(key);
        if (node == null) {
            return -1;
        }
        
        // Move to head (mark as recently used)
        moveToHead(node);
        return node.value;
    }
    
    /**
     * Insert or update key-value pair.
     * 
     * @param key Key to insert/update
     * @param value Value to store
     */
    public void put(int key, int value) {
        Node node = cache.get(key);
        
        if (node == null) {
            // Insert new key
            Node newNode = new Node(key, value);
            
            if (cache.size() >= capacity) {
                // Remove least recently used item
                Node tailNode = removeTail();
                cache.remove(tailNode.key);
            }
            
            // Add new node
            cache.put(key, newNode);
            addToHead(newNode);
        } else {
            // Update existing key
            node.value = value;
            moveToHead(node);
        }
    }
    
    /**
     * Test the LRU cache implementation.
     */
    public static void main(String[] args) {
        System.out.println("Testing LRU Cache Implementation");
        System.out.println("========================================");
        
        LRUCache cache = new LRUCache(2);
        
        System.out.println("Creating cache with capacity 2");
        
        cache.put(1, 1);
        System.out.println("put(1, 1)");
        
        cache.put(2, 2);
        System.out.println("put(2, 2)");
        
        int result = cache.get(1);
        System.out.println("get(1) = " + result);  // Should return 1
        
        cache.put(3, 3);  // Evicts key 2
        System.out.println("put(3, 3) - evicts key 2");
        
        result = cache.get(2);
        System.out.println("get(2) = " + result);  // Should return -1 (not found)
        
        cache.put(4, 4);  // Evicts key 1
        System.out.println("put(4, 4) - evicts key 1");
        
        result = cache.get(1);
        System.out.println("get(1) = " + result);  // Should return -1 (not found)
        
        result = cache.get(3);
        System.out.println("get(3) = " + result);  // Should return 3
        
        result = cache.get(4);
        System.out.println("get(4) = " + result);  // Should return 4
    }
}