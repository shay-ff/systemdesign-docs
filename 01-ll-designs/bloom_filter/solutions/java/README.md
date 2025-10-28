# Bloom Filter - Java Implementation

This directory contains a Java implementation of a Bloom filter using BitSet and concurrent utilities for thread-safe operations.

## Features

- **Thread-safe operations** using synchronized blocks and AtomicInteger
- **Multiple hash functions** including MurmurHash3, FNV, DJB2, SDBM, and SHA1
- **Optimal parameter calculation** for bit array size and hash function count
- **Memory-efficient implementation** using BitSet
- **Builder pattern** for flexible filter construction
- **Comprehensive statistics** and monitoring capabilities
- **Type safety** with generics and proper error handling

## Architecture

The implementation consists of several key classes:

- **BloomFilter**: Main filter class with add/contains operations
- **HashFunctions**: Collection of hash function implementations
- **BloomFilterStats**: Statistics and monitoring data
- **BloomFilterBuilder**: Builder pattern for flexible construction
- **BloomFilterDemo**: Demonstration and testing class

## Compilation and Execution

```bash
# Compile all Java files
javac *.java

# Run the demonstration
java BloomFilterDemo
```

### Java Version Requirements

- **Java 8 or later** for lambda expressions and modern features
- **Standard library only** - no external dependencies required

## Usage

### Basic Example

```java
// Create filter for 10,000 elements with 1% false positive rate
BloomFilter bf = new BloomFilter(10000, 0.01);

// Add elements
bf.add("example.com");
bf.add("google.com");

// Test membership
boolean exists = bf.contains("example.com");  // true (definitely added)
boolean maybe = bf.contains("unknown.com");   // false (definitely not) or true (false positive)
```

### Advanced Configuration

```java
// Use builder pattern
BloomFilter bf = new BloomFilterBuilder()
    .withExpectedElements(50000)
    .withFalsePositiveRate(0.001)  // 0.1% false positive rate
    .build();

// Get comprehensive statistics
BloomFilterStats stats = bf.getStats();
System.out.println("Memory usage: " + stats.getMemoryUsage() + " bytes");
System.out.println("Fill ratio: " + String.format("%.2f%%", stats.getFillRatio() * 100));
```

### Thread-Safe Usage

```java
// The implementation is thread-safe for concurrent access
BloomFilter bf = new BloomFilter(10000, 0.01);

// Multiple threads can safely add and query
ExecutorService executor = Executors.newFixedThreadPool(10);

for (int i = 0; i < 1000; i++) {
    final int index = i;
    executor.submit(() -> {
        bf.add("element-" + index);
        boolean exists = bf.contains("element-" + index);
    });
}
```

## Performance Characteristics

- **Time Complexity**: O(k) for both add and contains operations, where k is the number of hash functions
- **Space Complexity**: O(m) where m is the bit array size
- **Thread Safety**: Synchronized operations with minimal lock contention
- **Memory Efficiency**: Uses BitSet for optimal memory usage
- **Throughput**: Suitable for high-throughput applications

## Hash Functions

The implementation includes multiple hash functions:

1. **MurmurHash3**: High-quality, fast hash function
2. **FNV-1a**: Fast hash with good distribution properties
3. **DJB2**: Simple and effective hash function
4. **SDBM**: Hash function from SDBM database
5. **SHA1**: Cryptographic hash for high-quality distribution

## Mathematical Foundation

### Optimal Parameters

The implementation automatically calculates optimal parameters:

```java
// Bit array size: m = -(n × ln(p)) / (ln(2)²)
private static int calculateBitArraySize(int expectedElements, double falsePositiveRate) {
    double size = -(expectedElements * Math.log(falsePositiveRate)) / (Math.log(2) * Math.log(2));
    return Math.max(1, (int) Math.ceil(size));
}

// Number of hash functions: k = (m / n) × ln(2)
private static int calculateNumHashFunctions(int bitArraySize, int expectedElements) {
    double k = ((double) bitArraySize / expectedElements) * Math.log(2);
    return Math.max(1, (int) Math.round(k));
}
```

## Memory Usage Comparison

For 1 million elements:
- **HashSet<String>**: ~80 MB (assuming 20-char average strings)
- **Bloom filter (1% FP)**: ~1.2 MB
- **Bloom filter (0.1% FP)**: ~1.9 MB
- **Memory savings**: 40-60x reduction

## Thread Safety

The implementation provides thread safety through:
- **Synchronized blocks**: Protecting BitSet operations
- **AtomicInteger**: Thread-safe element counting
- **Immutable parameters**: Configuration cannot be changed after creation
- **Defensive copying**: Safe access to internal state

## Error Handling

- **Null checks**: Proper handling of null inputs
- **Parameter validation**: Validates constructor parameters
- **Exception handling**: Graceful fallback for hash function failures
- **Overflow protection**: Handles large numbers safely

## Limitations

- **No element removal**: Bloom filters don't support deletion
- **False positives**: Small probability of false positive results
- **Fixed size**: Cannot resize after creation
- **No element enumeration**: Cannot list stored elements
- **Memory overhead**: BitSet has some overhead compared to raw bit arrays

## Use Cases

- **Database optimization**: Reduce expensive disk lookups
- **Web crawling**: Avoid revisiting URLs
- **Caching systems**: Quick cache hit/miss determination
- **Network security**: Malicious content detection
- **Distributed systems**: Reduce network calls
- **Content filtering**: Spam and malware detection

## Extensions

Possible enhancements for production use:
- **Counting Bloom Filter**: Support for element removal
- **Scalable Bloom Filter**: Dynamic resizing capability
- **Persistent storage**: Serialize/deserialize filter state
- **JMX monitoring**: Integration with Java management
- **Custom hash functions**: Pluggable hash function interface
- **Compressed representation**: Further memory optimization

## Performance Tuning

### JVM Options

```bash
# For high-throughput applications
java -XX:+UseG1GC -XX:MaxGCPauseMillis=100 BloomFilterDemo

# For low-latency applications
java -XX:+UseZGC -XX:+UnlockExperimentalVMOptions BloomFilterDemo

# For memory-constrained environments
java -Xmx512m -XX:+UseSerialGC BloomFilterDemo
```

### Monitoring

```java
// Monitor performance
BloomFilterStats stats = bf.getStats();
System.out.println("Fill ratio: " + stats.getFillRatio());
System.out.println("Actual FP rate: " + stats.getActualFalsePositiveRate());

// JMX integration (custom implementation)
MBeanServer server = ManagementFactory.getPlatformMBeanServer();
ObjectName name = new ObjectName("com.example:type=BloomFilter");
server.registerMBean(new BloomFilterMBean(bf), name);
```

## Dependencies

This implementation uses only standard Java libraries:
- `java.util.BitSet` for bit array operations
- `java.util.concurrent.atomic.AtomicInteger` for thread-safe counters
- `java.security.MessageDigest` for SHA1 hash function
- `java.nio.charset.StandardCharsets` for consistent string encoding

No external dependencies are required, making it easy to integrate into existing projects.