/**
 * Bloom Filter Implementation in Java
 * 
 * This implementation provides a space-efficient probabilistic data structure
 * for membership testing with configurable false positive rates.
 * 
 * Features:
 * - Configurable false positive rate with optimal parameter calculation
 * - Multiple hash functions for better distribution
 * - Thread-safe operations using BitSet
 * - Memory-efficient implementation
 * - Comprehensive statistics and monitoring
 */

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/**
 * Collection of hash functions for Bloom filter
 */
class HashFunctions {
    
    /**
     * MurmurHash3 implementation
     */
    public static int murmurHash3(String data, int seed) {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return murmurHash3(bytes, seed);
    }
    
    private static int murmurHash3(byte[] data, int seed) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        final int r1 = 15;
        final int r2 = 13;
        final int m = 5;
        final int n = 0xe6546b64;
        
        int hash = seed;
        int length = data.length;
        int roundedEnd = (length & 0xfffffffc);
        
        for (int i = 0; i < roundedEnd; i += 4) {
            int k = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) |
                   ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k *= c1;
            k = (k << r1) | (k >>> (32 - r1));
            k *= c2;
            hash ^= k;
            hash = ((hash << r2) | (hash >>> (32 - r2))) * m + n;
        }
        
        int k1 = 0;
        switch (length & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = (k1 << r1) | (k1 >>> (32 - r1));
                k1 *= c2;
                hash ^= k1;
        }
        
        hash ^= length;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);
        
        return Math.abs(hash);
    }
    
    /**
     * FNV-1a hash function
     */
    public static int fnvHash(String data) {
        int hash = 0x811c9dc5; // FNV offset basis
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x01000193; // FNV prime
        }
        
        return Math.abs(hash);
    }
    
    /**
     * DJB2 hash function
     */
    public static int djb2Hash(String data) {
        int hash = 5381;
        
        for (char c : data.toCharArray()) {
            hash = ((hash << 5) + hash) + c;
        }
        
        return Math.abs(hash);
    }
    
    /**
     * SDBM hash function
     */
    public static int sdbmHash(String data) {
        int hash = 0;
        
        for (char c : data.toCharArray()) {
            hash = c + (hash << 6) + (hash << 16) - hash;
        }
        
        return Math.abs(hash);
    }
    
    /**
     * SHA1-based hash function
     */
    public static int sha1Hash(String data, int seed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((data + seed).getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            
            int hash = 0;
            for (int i = 0; i < 4 && i < digest.length; i++) {
                hash = (hash << 8) | (digest[i] & 0xff);
            }
            
            return Math.abs(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to DJB2
            return djb2Hash(data + seed);
        }
    }
}

/**
 * Statistics for Bloom filter
 */
class BloomFilterStats {
    private final int bitArraySize;
    private final int numHashFunctions;
    private final int numElements;
    private final int expectedElements;
    private final double falsePositiveRate;
    private final int memoryUsage;
    private double fillRatio;
    
    public BloomFilterStats(int bitArraySize, int numHashFunctions, int numElements,
                           int expectedElements, double falsePositiveRate) {
        this.bitArraySize = bitArraySize;
        this.numHashFunctions = numHashFunctions;
        this.numElements = numElements;
        this.expectedElements = expectedElements;
        this.falsePositiveRate = falsePositiveRate;
        this.memoryUsage = (bitArraySize + 7) / 8; // bytes
        this.fillRatio = 0.0;
    }
    
    public void updateFillRatio(int setBits) {
        this.fillRatio = bitArraySize > 0 ? (double) setBits / bitArraySize : 0.0;
    }
    
    public double getActualFalsePositiveRate() {
        if (numElements == 0) return 0.0;
        
        // p = (1 - e^(-kn/m))^k
        double exponent = -(double) numHashFunctions * numElements / bitArraySize;
        return Math.pow(1 - Math.exp(exponent), numHashFunctions);
    }
    
    // Getters
    public int getBitArraySize() { return bitArraySize; }
    public int getNumHashFunctions() { return numHashFunctions; }
    public int getNumElements() { return numElements; }
    public int getExpectedElements() { return expectedElements; }
    public double getFalsePositiveRate() { return falsePositiveRate; }
    public int getMemoryUsage() { return memoryUsage; }
    public double getFillRatio() { return fillRatio; }
    
    @Override
    public String toString() {
        return String.format("BloomFilterStats{size=%d, hashFunctions=%d, elements=%d/%d, " +
                           "falsePositiveRate=%.4f, fillRatio=%.4f, memory=%d bytes}",
                           bitArraySize, numHashFunctions, numElements, expectedElements,
                           falsePositiveRate, fillRatio, memoryUsage);
    }
}

/**
 * Main Bloom Filter implementation
 */
public class BloomFilter {
    private final BitSet bitArray;
    private final int bitArraySize;
    private final int numHashFunctions;
    private final int expectedElements;
    private final double falsePositiveRate;
    private final AtomicInteger numElements;
    private final int[] hashSeeds;
    
    /**
     * Create Bloom filter with optimal parameters
     */
    public BloomFilter(int expectedElements, double falsePositiveRate) {
        if (expectedElements <= 0) {
            throw new IllegalArgumentException("Expected elements must be positive");
        }
        if (falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new IllegalArgumentException("False positive rate must be between 0 and 1");
        }
        
        this.expectedElements = expectedElements;
        this.falsePositiveRate = falsePositiveRate;
        this.numElements = new AtomicInteger(0);
        
        // Calculate optimal parameters
        this.bitArraySize = calculateBitArraySize(expectedElements, falsePositiveRate);
        this.numHashFunctions = calculateNumHashFunctions(bitArraySize, expectedElements);
        
        // Initialize bit array and hash seeds
        this.bitArray = new BitSet(bitArraySize);
        this.hashSeeds = new int[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) {
            this.hashSeeds[i] = i;
        }
    }
    
    /**
     * Calculate optimal bit array size
     */
    private static int calculateBitArraySize(int expectedElements, double falsePositiveRate) {
        // m = -(n * ln(p)) / (ln(2)^2)
        double size = -(expectedElements * Math.log(falsePositiveRate)) / (Math.log(2) * Math.log(2));
        return Math.max(1, (int) Math.ceil(size));
    }
    
    /**
     * Calculate optimal number of hash functions
     */
    private static int calculateNumHashFunctions(int bitArraySize, int expectedElements) {
        // k = (m / n) * ln(2)
        double k = ((double) bitArraySize / expectedElements) * Math.log(2);
        return Math.max(1, (int) Math.round(k));
    }
    
    /**
     * Get hash values for an element
     */
    private int[] getHashValues(String element) {
        int[] hashes = new int[numHashFunctions];
        
        for (int i = 0; i < numHashFunctions; i++) {
            int hashValue;
            switch (i % 5) {
                case 0:
                    hashValue = HashFunctions.murmurHash3(element, hashSeeds[i]);
                    break;
                case 1:
                    hashValue = HashFunctions.fnvHash(element + hashSeeds[i]);
                    break;
                case 2:
                    hashValue = HashFunctions.djb2Hash(element + hashSeeds[i]);
                    break;
                case 3:
                    hashValue = HashFunctions.sdbmHash(element + hashSeeds[i]);
                    break;
                default:
                    hashValue = HashFunctions.sha1Hash(element, hashSeeds[i]);
                    break;
            }
            hashes[i] = hashValue % bitArraySize;
        }
        
        return hashes;
    }
    
    /**
     * Add an element to the Bloom filter
     */
    public void add(String element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        
        int[] hashValues = getHashValues(element);
        
        synchronized (bitArray) {
            for (int hashValue : hashValues) {
                bitArray.set(hashValue);
            }
        }
        
        numElements.incrementAndGet();
    }
    
    /**
     * Test if an element might be in the set
     */
    public boolean contains(String element) {
        if (element == null) {
            return false;
        }
        
        int[] hashValues = getHashValues(element);
        
        synchronized (bitArray) {
            for (int hashValue : hashValues) {
                if (!bitArray.get(hashValue)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Clear all elements from the filter
     */
    public void clear() {
        synchronized (bitArray) {
            bitArray.clear();
        }
        numElements.set(0);
    }
    
    /**
     * Get current statistics
     */
    public BloomFilterStats getStats() {
        BloomFilterStats stats = new BloomFilterStats(
            bitArraySize, numHashFunctions, numElements.get(),
            expectedElements, falsePositiveRate
        );
        
        synchronized (bitArray) {
            stats.updateFillRatio(bitArray.cardinality());
        }
        
        return stats;
    }
    
    /**
     * Get actual false positive rate
     */
    public double getFalsePositiveRate() {
        return getStats().getActualFalsePositiveRate();
    }
    
    /**
     * Get memory usage in bytes
     */
    public int getMemoryUsage() {
        return (bitArraySize + 7) / 8;
    }
    
    /**
     * Get number of elements added
     */
    public int size() {
        return numElements.get();
    }
    
    // Getters
    public int getBitArraySize() { return bitArraySize; }
    public int getNumHashFunctions() { return numHashFunctions; }
    public int getExpectedElements() { return expectedElements; }
}

/**
 * Builder pattern for creating Bloom filters
 */
class BloomFilterBuilder {
    private Integer expectedElements;
    private double falsePositiveRate = 0.01;
    
    public BloomFilterBuilder withExpectedElements(int n) {
        this.expectedElements = n;
        return this;
    }
    
    public BloomFilterBuilder withFalsePositiveRate(double rate) {
        this.falsePositiveRate = rate;
        return this;
    }
    
    public BloomFilter build() {
        if (expectedElements == null) {
            throw new IllegalStateException("Expected elements must be specified");
        }
        return new BloomFilter(expectedElements, falsePositiveRate);
    }
}

/**
 * Demonstration class
 */
class BloomFilterDemo {
    public static void main(String[] args) {
        System.out.println("=== Bloom Filter Demo ===\n");
        
        // Create Bloom filter for 10,000 elements with 1% false positive rate
        BloomFilter bf = new BloomFilter(10000, 0.01);
        
        System.out.println("Created Bloom filter: " + bf.getStats() + "\n");
        
        // Add some elements
        String[] websites = {
            "google.com", "facebook.com", "twitter.com", "github.com",
            "stackoverflow.com", "reddit.com", "youtube.com", "amazon.com",
            "netflix.com", "spotify.com"
        };
        
        System.out.println("Adding websites to filter...");
        for (String website : websites) {
            bf.add(website);
            System.out.println("Added: " + website);
        }
        
        System.out.println("\nFilter stats after adding " + websites.length + " elements:");
        System.out.println(bf.getStats());
        
        // Test membership
        System.out.println("\n=== Membership Tests ===");
        
        // Test existing elements
        System.out.println("Testing existing elements:");
        for (int i = 0; i < Math.min(5, websites.length); i++) {
            boolean result = bf.contains(websites[i]);
            System.out.println("'" + websites[i] + "' in filter: " + result);
        }
        
        // Test non-existing elements
        System.out.println("\nTesting non-existing elements:");
        String[] testSites = {"nonexistent.com", "fake-site.org", "not-real.net"};
        for (String site : testSites) {
            boolean result = bf.contains(site);
            System.out.println("'" + site + "' in filter: " + result);
        }
        
        // Performance comparison
        System.out.println("\n=== Performance Comparison ===");
        
        // Create a regular set for comparison
        Set<String> regularSet = new HashSet<>(Arrays.asList(websites));
        
        System.out.println("Bloom filter memory usage: " + bf.getMemoryUsage() + " bytes");
        System.out.println("Regular set memory usage: ~" + (regularSet.toString().length() * 2) + " bytes (approximate)");
        System.out.println("Memory savings: ~" + ((regularSet.toString().length() * 2) / bf.getMemoryUsage()) + "x");
        
        // Test false positive rate
        System.out.println("\n=== False Positive Rate Test ===");
        int falsePositives = 0;
        int testCount = 1000;
        Set<String> websiteSet = new HashSet<>(Arrays.asList(websites));
        
        for (int i = 0; i < testCount; i++) {
            String testElement = "test-element-" + i;
            if (!websiteSet.contains(testElement) && bf.contains(testElement)) {
                falsePositives++;
            }
        }
        
        double actualFpRate = (double) falsePositives / testCount;
        double expectedFpRate = bf.getFalsePositiveRate();
        
        System.out.printf("Expected false positive rate: %.4f%n", expectedFpRate);
        System.out.printf("Actual false positive rate: %.4f%n", actualFpRate);
        System.out.println("False positives in " + testCount + " tests: " + falsePositives);
        
        System.out.println("\nDemo completed!");
    }
}