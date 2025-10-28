import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe Consistent Hashing implementation with virtual nodes support.
 * 
 * This implementation uses MD5 hashing to distribute keys and nodes
 * across a hash ring, with virtual nodes to improve load distribution.
 */
public class ConsistentHash {
    private final int virtualNodes;
    private final TreeMap<Long, String> ring;
    private final Set<String> nodes;
    private final ReentrantReadWriteLock lock;
    private final MessageDigest md5;
    
    /**
     * Initialize the consistent hash ring.
     * 
     * @param virtualNodes Number of virtual nodes per physical node
     */
    public ConsistentHash(int virtualNodes) {
        this.virtualNodes = virtualNodes;
        this.ring = new TreeMap<>();
        this.nodes = new HashSet<>();
        this.lock = new ReentrantReadWriteLock();
        
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * Generate hash value for a key using MD5.
     * 
     * @param key The key to hash
     * @return Hash value as long
     */
    private long hash(String key) {
        md5.reset();
        md5.update(key.getBytes());
        byte[] digest = md5.digest();
        
        long hash = 0;
        for (int i = 0; i < 8; i++) {
            hash = (hash << 8) | (digest[i] & 0xFF);
        }
        return hash;
    }
    
    /**
     * Add a node to the hash ring.
     * 
     * @param nodeId Unique identifier for the node
     */
    public void addNode(String nodeId) {
        lock.writeLock().lock();
        try {
            if (nodes.contains(nodeId)) {
                return;
            }
            
            nodes.add(nodeId);
            
            // Add virtual nodes to the ring
            for (int i = 0; i < virtualNodes; i++) {
                String virtualKey = nodeId + ":" + i;
                long hashValue = hash(virtualKey);
                ring.put(hashValue, nodeId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove a node from the hash ring.
     * 
     * @param nodeId Identifier of the node to remove
     */
    public void removeNode(String nodeId) {
        lock.writeLock().lock();
        try {
            if (!nodes.contains(nodeId)) {
                return;
            }
            
            nodes.remove(nodeId);
            
            // Remove virtual nodes from the ring
            for (int i = 0; i < virtualNodes; i++) {
                String virtualKey = nodeId + ":" + i;
                long hashValue = hash(virtualKey);
                ring.remove(hashValue);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the node responsible for a given key.
     * 
     * @param key The key to look up
     * @return Node ID that should handle the key, or null if no nodes available
     */
    public String getNode(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }
            
            long hashValue = hash(key);
            
            // Find the first node clockwise from the key's hash
            Map.Entry<Long, String> entry = ring.ceilingEntry(hashValue);
            
            // Wrap around to the beginning if we're past the end
            if (entry == null) {
                entry = ring.firstEntry();
            }
            
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get all active nodes in the system.
     * 
     * @return List of active node IDs
     */
    public List<String> getNodes() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(nodes);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Analyze load distribution for a set of keys.
     * 
     * @param keys List of keys to analyze
     * @return Map of node_id to number of keys assigned
     */
    public Map<String, Integer> getLoadDistribution(List<String> keys) {
        Map<String, Integer> distribution = new HashMap<>();
        
        for (String key : keys) {
            String node = getNode(key);
            if (node != null) {
                distribution.put(node, distribution.getOrDefault(node, 0) + 1);
            }
        }
        
        return distribution;
    }
    
    /**
     * Get information about the current ring state.
     * 
     * @return Map containing ring statistics
     */
    public Map<String, Object> getRingInfo() {
        lock.readLock().lock();
        try {
            Map<String, Object> info = new HashMap<>();
            info.put("totalNodes", nodes.size());
            info.put("totalVirtualNodes", ring.size());
            info.put("virtualNodesPerNode", virtualNodes);
            info.put("nodes", new ArrayList<>(nodes));
            return info;
        } finally {
            lock.readLock().unlock();
        }
    }
}