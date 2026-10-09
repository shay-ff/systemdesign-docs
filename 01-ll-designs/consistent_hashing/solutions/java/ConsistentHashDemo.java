import java.util.*;

/**
 * Demonstration of the ConsistentHash implementation.
 */
public class ConsistentHashDemo {
    
    public static void main(String[] args) {
        demonstrateConsistentHashing();
    }
    
    /**
     * Demonstrate consistent hashing functionality.
     */
    public static void demonstrateConsistentHashing() {
        System.out.println("=== Consistent Hashing Demo ===\n");
        
        // Create hash ring
        ConsistentHash ch = new ConsistentHash(3);
        
        // Add initial nodes
        List<String> nodes = Arrays.asList("server1", "server2", "server3");
        for (String node : nodes) {
            ch.addNode(node);
        }
        
        System.out.println("Added nodes: " + nodes);
        System.out.println("Ring info: " + ch.getRingInfo() + "\n");
        
        // Test key distribution
        List<String> testKeys = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            testKeys.add("user:" + i);
        }
        
        System.out.println("Initial key distribution:");
        Map<String, Integer> distribution = ch.getLoadDistribution(testKeys);
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " keys");
        }
        
        // Show specific key mappings
        System.out.println("\nKey mappings:");
        for (int i = 0; i < Math.min(5, testKeys.size()); i++) {
            String key = testKeys.get(i);
            String node = ch.getNode(key);
            System.out.println("  " + key + " -> " + node);
        }
        
        // Remove a node and show redistribution
        System.out.println("\nRemoving 'server2'...");
        ch.removeNode("server2");
        
        System.out.println("New key distribution:");
        Map<String, Integer> newDistribution = ch.getLoadDistribution(testKeys);
        for (Map.Entry<String, Integer> entry : newDistribution.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " keys");
        }
        
        System.out.println("\nRing info after removal: " + ch.getRingInfo());
        
        // Add a new node
        System.out.println("\nAdding 'server4'...");
        ch.addNode("server4");
        
        Map<String, Integer> finalDistribution = ch.getLoadDistribution(testKeys);
        System.out.println("Final key distribution:");
        for (Map.Entry<String, Integer> entry : finalDistribution.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " keys");
        }
        
        // Demonstrate load balancing with more keys
        System.out.println("\n=== Load Balancing Test ===");
        List<String> manyKeys = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            manyKeys.add("key:" + i);
        }
        
        Map<String, Integer> loadTest = ch.getLoadDistribution(manyKeys);
        System.out.println("Distribution of 1000 keys:");
        for (Map.Entry<String, Integer> entry : loadTest.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / manyKeys.size();
            System.out.printf("  %s: %d keys (%.1f%%)\n", 
                entry.getKey(), entry.getValue(), percentage);
        }
    }
}