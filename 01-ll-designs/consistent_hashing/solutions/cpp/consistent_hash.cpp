#include <iostream>
#include <map>
#include <set>
#include <vector>
#include <string>
#include <unordered_map>
#include <algorithm>
#include <sstream>
#include <iomanip>

#ifndef USE_STD_HASH
#include <openssl/md5.h>
#endif

/**
 * Consistent Hashing implementation with virtual nodes support.
 * 
 * This implementation uses MD5 hashing (or std::hash as fallback) to distribute 
 * keys and nodes across a hash ring, with virtual nodes to improve load distribution.
 */
class ConsistentHash {
private:
    int virtualNodes;
    std::map<uint64_t, std::string> ring;  // hash -> node_id
    std::set<std::string> nodes;           // active nodes
    
    /**
     * Generate hash value for a key.
     * Uses MD5 if available, otherwise falls back to std::hash.
     */
    uint64_t hash(const std::string& key) const {
#ifndef USE_STD_HASH
        // Use MD5 for better distribution
        unsigned char digest[MD5_DIGEST_LENGTH];
        MD5(reinterpret_cast<const unsigned char*>(key.c_str()), key.length(), digest);
        
        uint64_t hashValue = 0;
        for (int i = 0; i < 8; i++) {
            hashValue = (hashValue << 8) | digest[i];
        }
        return hashValue;
#else
        // Fallback to std::hash
        return std::hash<std::string>{}(key);
#endif
    }
    
public:
    /**
     * Initialize the consistent hash ring.
     * 
     * @param virtualNodes Number of virtual nodes per physical node
     */
    explicit ConsistentHash(int virtualNodes = 3) : virtualNodes(virtualNodes) {}
    
    /**
     * Add a node to the hash ring.
     * 
     * @param nodeId Unique identifier for the node
     */
    void addNode(const std::string& nodeId) {
        if (nodes.find(nodeId) != nodes.end()) {
            return;  // Node already exists
        }
        
        nodes.insert(nodeId);
        
        // Add virtual nodes to the ring
        for (int i = 0; i < virtualNodes; i++) {
            std::string virtualKey = nodeId + ":" + std::to_string(i);
            uint64_t hashValue = hash(virtualKey);
            ring[hashValue] = nodeId;
        }
    }
    
    /**
     * Remove a node from the hash ring.
     * 
     * @param nodeId Identifier of the node to remove
     */
    void removeNode(const std::string& nodeId) {
        if (nodes.find(nodeId) == nodes.end()) {
            return;  // Node doesn't exist
        }
        
        nodes.erase(nodeId);
        
        // Remove virtual nodes from the ring
        for (int i = 0; i < virtualNodes; i++) {
            std::string virtualKey = nodeId + ":" + std::to_string(i);
            uint64_t hashValue = hash(virtualKey);
            ring.erase(hashValue);
        }
    }
    
    /**
     * Get the node responsible for a given key.
     * 
     * @param key The key to look up
     * @return Node ID that should handle the key, or empty string if no nodes available
     */
    std::string getNode(const std::string& key) const {
        if (ring.empty()) {
            return "";
        }
        
        uint64_t hashValue = hash(key);
        
        // Find the first node clockwise from the key's hash
        auto it = ring.lower_bound(hashValue);
        
        // Wrap around to the beginning if we're past the end
        if (it == ring.end()) {
            it = ring.begin();
        }
        
        return it->second;
    }
    
    /**
     * Get all active nodes in the system.
     * 
     * @return Vector of active node IDs
     */
    std::vector<std::string> getNodes() const {
        return std::vector<std::string>(nodes.begin(), nodes.end());
    }
    
    /**
     * Analyze load distribution for a set of keys.
     * 
     * @param keys Vector of keys to analyze
     * @return Map of node_id to number of keys assigned
     */
    std::unordered_map<std::string, int> getLoadDistribution(const std::vector<std::string>& keys) const {
        std::unordered_map<std::string, int> distribution;
        
        for (const auto& key : keys) {
            std::string node = getNode(key);
            if (!node.empty()) {
                distribution[node]++;
            }
        }
        
        return distribution;
    }
    
    /**
     * Get information about the current ring state.
     * 
     * @return String containing ring statistics
     */
    std::string getRingInfo() const {
        std::ostringstream oss;
        oss << "Total nodes: " << nodes.size()
            << ", Total virtual nodes: " << ring.size()
            << ", Virtual nodes per node: " << virtualNodes;
        return oss.str();
    }
    
    /**
     * Print the current state of the hash ring (for debugging).
     */
    void printRing() const {
        std::cout << "Hash Ring State:\n";
        for (const auto& entry : ring) {
            std::cout << "  Hash: " << std::hex << entry.first 
                      << " -> Node: " << entry.second << std::dec << "\n";
        }
    }
};

/**
 * Demonstrate consistent hashing functionality.
 */
void demonstrateConsistentHashing() {
    std::cout << "=== Consistent Hashing Demo ===\n\n";
    
    // Create hash ring
    ConsistentHash ch(3);
    
    // Add initial nodes
    std::vector<std::string> nodes = {"server1", "server2", "server3"};
    for (const auto& node : nodes) {
        ch.addNode(node);
    }
    
    std::cout << "Added nodes: ";
    for (const auto& node : nodes) {
        std::cout << node << " ";
    }
    std::cout << "\n";
    std::cout << "Ring info: " << ch.getRingInfo() << "\n\n";
    
    // Test key distribution
    std::vector<std::string> testKeys;
    for (int i = 1; i <= 10; i++) {
        testKeys.push_back("user:" + std::to_string(i));
    }
    
    std::cout << "Initial key distribution:\n";
    auto distribution = ch.getLoadDistribution(testKeys);
    for (const auto& entry : distribution) {
        std::cout << "  " << entry.first << ": " << entry.second << " keys\n";
    }
    
    // Show specific key mappings
    std::cout << "\nKey mappings:\n";
    for (int i = 0; i < std::min(5, static_cast<int>(testKeys.size())); i++) {
        const auto& key = testKeys[i];
        std::string node = ch.getNode(key);
        std::cout << "  " << key << " -> " << node << "\n";
    }
    
    // Remove a node and show redistribution
    std::cout << "\nRemoving 'server2'...\n";
    ch.removeNode("server2");
    
    std::cout << "New key distribution:\n";
    auto newDistribution = ch.getLoadDistribution(testKeys);
    for (const auto& entry : newDistribution) {
        std::cout << "  " << entry.first << ": " << entry.second << " keys\n";
    }
    
    std::cout << "\nRing info after removal: " << ch.getRingInfo() << "\n";
    
    // Add a new node
    std::cout << "\nAdding 'server4'...\n";
    ch.addNode("server4");
    
    auto finalDistribution = ch.getLoadDistribution(testKeys);
    std::cout << "Final key distribution:\n";
    for (const auto& entry : finalDistribution) {
        std::cout << "  " << entry.first << ": " << entry.second << " keys\n";
    }
    
    // Demonstrate load balancing with more keys
    std::cout << "\n=== Load Balancing Test ===\n";
    std::vector<std::string> manyKeys;
    for (int i = 1; i <= 1000; i++) {
        manyKeys.push_back("key:" + std::to_string(i));
    }
    
    auto loadTest = ch.getLoadDistribution(manyKeys);
    std::cout << "Distribution of 1000 keys:\n";
    for (const auto& entry : loadTest) {
        double percentage = (entry.second * 100.0) / manyKeys.size();
        std::cout << "  " << entry.first << ": " << entry.second 
                  << " keys (" << std::fixed << std::setprecision(1) 
                  << percentage << "%)\n";
    }
}

int main() {
    demonstrateConsistentHashing();
    return 0;
}