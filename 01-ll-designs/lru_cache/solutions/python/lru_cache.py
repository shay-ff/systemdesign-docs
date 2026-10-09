"""
LRU Cache Implementation in Python

A Least Recently Used (LRU) cache implementation using a doubly-linked list
and hash map for O(1) get and put operations.

Time Complexity:
- get(): O(1)
- put(): O(1)

Space Complexity: O(capacity)
"""

class Node:
    """Doubly-linked list node for LRU cache."""
    
    def __init__(self, key: int = 0, value: int = 0):
        self.key = key
        self.value = value
        self.prev = None
        self.next = None


class LRUCache:
    """
    LRU Cache implementation with O(1) operations.
    
    Uses a combination of:
    - Hash map for O(1) key lookup
    - Doubly-linked list for O(1) insertion/deletion
    """
    
    def __init__(self, capacity: int):
        """
        Initialize LRU cache with given capacity.
        
        Args:
            capacity: Maximum number of key-value pairs to store
        """
        self.capacity = capacity
        self.cache = {}  # key -> node mapping
        
        # Create dummy head and tail nodes
        self.head = Node()
        self.tail = Node()
        self.head.next = self.tail
        self.tail.prev = self.head
    
    def _remove_node(self, node: Node) -> None:
        """Remove node from doubly-linked list."""
        node.prev.next = node.next
        node.next.prev = node.prev
    
    def _add_to_head(self, node: Node) -> None:
        """Add node right after head (most recently used position)."""
        node.prev = self.head
        node.next = self.head.next
        self.head.next.prev = node
        self.head.next = node
    
    def _move_to_head(self, node: Node) -> None:
        """Move existing node to head (mark as recently used)."""
        self._remove_node(node)
        self._add_to_head(node)
    
    def _remove_tail(self) -> Node:
        """Remove and return the last node (least recently used)."""
        last_node = self.tail.prev
        self._remove_node(last_node)
        return last_node
    
    def get(self, key: int) -> int:
        """
        Get value by key and mark as recently used.
        
        Args:
            key: Key to look up
            
        Returns:
            Value if key exists, -1 otherwise
        """
        if key in self.cache:
            node = self.cache[key]
            # Move to head (mark as recently used)
            self._move_to_head(node)
            return node.value
        return -1
    
    def put(self, key: int, value: int) -> None:
        """
        Insert or update key-value pair.
        
        Args:
            key: Key to insert/update
            value: Value to store
        """
        if key in self.cache:
            # Update existing key
            node = self.cache[key]
            node.value = value
            self._move_to_head(node)
        else:
            # Insert new key
            new_node = Node(key, value)
            
            if len(self.cache) >= self.capacity:
                # Remove least recently used item
                tail_node = self._remove_tail()
                del self.cache[tail_node.key]
            
            # Add new node
            self.cache[key] = new_node
            self._add_to_head(new_node)


def main():
    """Test the LRU cache implementation."""
    print("Testing LRU Cache Implementation")
    print("=" * 40)
    
    cache = LRUCache(2)
    
    print("Creating cache with capacity 2")
    
    cache.put(1, 1)
    print("put(1, 1)")
    
    cache.put(2, 2)
    print("put(2, 2)")
    
    result = cache.get(1)
    print(f"get(1) = {result}")  # Should return 1
    
    cache.put(3, 3)  # Evicts key 2
    print("put(3, 3) - evicts key 2")
    
    result = cache.get(2)
    print(f"get(2) = {result}")  # Should return -1 (not found)
    
    cache.put(4, 4)  # Evicts key 1
    print("put(4, 4) - evicts key 1")
    
    result = cache.get(1)
    print(f"get(1) = {result}")  # Should return -1 (not found)
    
    result = cache.get(3)
    print(f"get(3) = {result}")  # Should return 3
    
    result = cache.get(4)
    print(f"get(4) = {result}")  # Should return 4


if __name__ == "__main__":
    main()