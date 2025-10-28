/**
 * LRU Cache Implementation in C++
 * 
 * A Least Recently Used (LRU) cache implementation using a doubly-linked list
 * and unordered_map for O(1) get and put operations.
 * 
 * Time Complexity:
 * - get(): O(1)
 * - put(): O(1)
 * 
 * Space Complexity: O(capacity)
 * 
 * Compile: g++ -std=c++17 lru_cache.cpp -o lru && ./lru
 */

#include <iostream>
#include <unordered_map>
using namespace std;

/**
 * LRU Cache implementation with O(1) operations.
 * 
 * Uses a combination of:
 * - unordered_map for O(1) key lookup
 * - Doubly-linked list for O(1) insertion/deletion
 */
class LRUCache {
private:
    /**
     * Doubly-linked list node for LRU cache.
     */
    struct Node {
        int key;
        int val;
        Node *prev, *next;
        
        Node(int k, int v) : key(k), val(v), prev(nullptr), next(nullptr) {}
    };
    
    int capacity;
    unordered_map<int, Node*> mp;  // key -> node mapping
    Node *head, *tail;  // dummy head and tail nodes
    
    /**
     * Remove node from doubly-linked list.
     * 
     * @param n Node to remove
     */
    void remove(Node* n) {
        n->prev->next = n->next;
        n->next->prev = n->prev;
    }
    
    /**
     * Insert node right after head (most recently used position).
     * 
     * @param n Node to insert
     */
    void insert_front(Node* n) {
        n->next = head->next;
        n->prev = head;
        head->next->prev = n;
        head->next = n;
    }

public:
    /**
     * Initialize LRU cache with given capacity.
     * 
     * @param cap Maximum number of key-value pairs to store
     */
    LRUCache(int cap) : capacity(cap) {
        // Create dummy head and tail nodes
        head = new Node(-1, -1);
        tail = new Node(-1, -1);
        head->next = tail;
        tail->prev = head;
    }
    
    /**
     * Destructor to clean up allocated memory.
     */
    ~LRUCache() {
        Node* current = head;
        while (current != nullptr) {
            Node* next = current->next;
            delete current;
            current = next;
        }
    }
    
    /**
     * Get value by key and mark as recently used.
     * 
     * @param key Key to look up
     * @return Value if key exists, -1 otherwise
     */
    int get(int key) {
        if (mp.find(key) == mp.end()) {
            return -1;
        }
        
        Node* n = mp[key];
        // Move to front (mark as recently used)
        remove(n);
        insert_front(n);
        return n->val;
    }
    
    /**
     * Insert or update key-value pair.
     * 
     * @param key Key to insert/update
     * @param value Value to store
     */
    void put(int key, int value) {
        if (mp.find(key) != mp.end()) {
            // Update existing key
            Node* n = mp[key];
            n->val = value;
            remove(n);
            insert_front(n);
            return;
        }
        
        // Check capacity and evict if necessary
        if ((int)mp.size() == capacity) {
            Node* lru = tail->prev;  // least recently used
            remove(lru);
            mp.erase(lru->key);
            delete lru;
        }
        
        // Insert new key
        Node* n = new Node(key, value);
        insert_front(n);
        mp[key] = n;
    }
    
    /**
     * Get current size of cache.
     * 
     * @return Number of items in cache
     */
    int size() const {
        return mp.size();
    }
};

/**
 * Test the LRU cache implementation.
 */
int main() {
    cout << "Testing LRU Cache Implementation" << endl;
    cout << "========================================" << endl;
    
    LRUCache cache(2);
    
    cout << "Creating cache with capacity 2" << endl;
    
    cache.put(1, 1);
    cout << "put(1, 1)" << endl;
    
    cache.put(2, 2);
    cout << "put(2, 2)" << endl;
    
    cout << "get(1) = " << cache.get(1) << endl;  // Should return 1
    
    cache.put(3, 3);  // Evicts key 2
    cout << "put(3, 3) - evicts key 2" << endl;
    
    cout << "get(2) = " << cache.get(2) << endl;  // Should return -1 (not found)
    
    cache.put(4, 4);  // Evicts key 1
    cout << "put(4, 4) - evicts key 1" << endl;
    
    cout << "get(1) = " << cache.get(1) << endl;  // Should return -1 (not found)
    cout << "get(3) = " << cache.get(3) << endl;  // Should return 3
    cout << "get(4) = " << cache.get(4) << endl;  // Should return 4
    
    cout << "\nFinal cache size: " << cache.size() << endl;
    
    return 0;
}
