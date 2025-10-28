import hashlib
import bisect
from typing import Dict, List, Optional, Set
from collections import defaultdict
import threading


class ConsistentHash:
    """
    Consistent Hashing implementation with virtual nodes support.
    
    This implementation uses MD5 hashing to distribute keys and nodes
    across a hash ring, with virtual nodes to improve load distribution.
    """
    
    def __init__(self, virtual_nodes: int = 3):
        """
        Initialize the consistent hash ring.
        
        Args:
            virtual_nodes: Number of virtual nodes per physical node
        """
        self.virtual_nodes = virtual_nodes
        self.ring: Dict[int, str] = {}  # hash -> node_id
        self.sorted_hashes: List[int] = []  # sorted hash values
        self.nodes: Set[str] = set()  # active nodes
        self.lock = threading.RLock()  # thread safety
    
    def _hash(self, key: str) -> int:
        """Generate hash value for a key using MD5."""
        return int(hashlib.md5(key.encode('utf-8')).hexdigest(), 16)
    
    def add_node(self, node_id: str) -> None:
        """
        Add a node to the hash ring.
        
        Args:
            node_id: Unique identifier for the node
        """
        with self.lock:
            if node_id in self.nodes:
                return
            
            self.nodes.add(node_id)
            
            # Add virtual nodes to the ring
            for i in range(self.virtual_nodes):
                virtual_key = f"{node_id}:{i}"
                hash_value = self._hash(virtual_key)
                
                self.ring[hash_value] = node_id
                bisect.insort(self.sorted_hashes, hash_value)
    
    def remove_node(self, node_id: str) -> None:
        """
        Remove a node from the hash ring.
        
        Args:
            node_id: Identifier of the node to remove
        """
        with self.lock:
            if node_id not in self.nodes:
                return
            
            self.nodes.remove(node_id)
            
            # Remove virtual nodes from the ring
            for i in range(self.virtual_nodes):
                virtual_key = f"{node_id}:{i}"
                hash_value = self._hash(virtual_key)
                
                if hash_value in self.ring:
                    del self.ring[hash_value]
                    self.sorted_hashes.remove(hash_value)
    
    def get_node(self, key: str) -> Optional[str]:
        """
        Get the node responsible for a given key.
        
        Args:
            key: The key to look up
            
        Returns:
            Node ID that should handle the key, or None if no nodes available
        """
        with self.lock:
            if not self.sorted_hashes:
                return None
            
            hash_value = self._hash(key)
            
            # Find the first node clockwise from the key's hash
            idx = bisect.bisect_right(self.sorted_hashes, hash_value)
            
            # Wrap around to the beginning if we're past the end
            if idx == len(self.sorted_hashes):
                idx = 0
            
            return self.ring[self.sorted_hashes[idx]]
    
    def get_nodes(self) -> List[str]:
        """Get all active nodes in the system."""
        with self.lock:
            return list(self.nodes)
    
    def get_load_distribution(self, keys: List[str]) -> Dict[str, int]:
        """
        Analyze load distribution for a set of keys.
        
        Args:
            keys: List of keys to analyze
            
        Returns:
            Dictionary mapping node_id to number of keys assigned
        """
        distribution = defaultdict(int)
        
        for key in keys:
            node = self.get_node(key)
            if node:
                distribution[node] += 1
        
        return dict(distribution)
    
    def get_ring_info(self) -> Dict:
        """Get information about the current ring state."""
        with self.lock:
            return {
                'total_nodes': len(self.nodes),
                'total_virtual_nodes': len(self.sorted_hashes),
                'virtual_nodes_per_node': self.virtual_nodes,
                'nodes': list(self.nodes)
            }


def demonstrate_consistent_hashing():
    """Demonstrate consistent hashing functionality."""
    print("=== Consistent Hashing Demo ===\n")
    
    # Create hash ring
    ch = ConsistentHash(virtual_nodes=3)
    
    # Add initial nodes
    nodes = ["server1", "server2", "server3"]
    for node in nodes:
        ch.add_node(node)
    
    print(f"Added nodes: {nodes}")
    print(f"Ring info: {ch.get_ring_info()}\n")
    
    # Test key distribution
    test_keys = [f"user:{i}" for i in range(1, 11)]
    
    print("Initial key distribution:")
    distribution = ch.get_load_distribution(test_keys)
    for node, count in distribution.items():
        print(f"  {node}: {count} keys")
    
    # Show specific key mappings
    print("\nKey mappings:")
    for key in test_keys[:5]:
        node = ch.get_node(key)
        print(f"  {key} -> {node}")
    
    # Remove a node and show redistribution
    print(f"\nRemoving 'server2'...")
    ch.remove_node("server2")
    
    print("New key distribution:")
    new_distribution = ch.get_load_distribution(test_keys)
    for node, count in new_distribution.items():
        print(f"  {node}: {count} keys")
    
    # Calculate redistribution
    redistributed = 0
    for key in test_keys:
        old_node = None
        for node, count in distribution.items():
            if node == "server2":
                continue
        new_node = ch.get_node(key)
        # This is a simplified check - in practice you'd track the changes
    
    print(f"\nRing info after removal: {ch.get_ring_info()}")
    
    # Add a new node
    print(f"\nAdding 'server4'...")
    ch.add_node("server4")
    
    final_distribution = ch.get_load_distribution(test_keys)
    print("Final key distribution:")
    for node, count in final_distribution.items():
        print(f"  {node}: {count} keys")


if __name__ == "__main__":
    demonstrate_consistent_hashing()