# Bloom Filter - C++ Implementation

This directory contains a high-performance C++ implementation of a Bloom filter using modern C++ features and optimized bit manipulation.

## Features

- **High-performance implementation** with minimal overhead and optimized bit operations
- **Multiple hash functions** including MurmurHash3, FNV, DJB2, SDBM, and std::hash
- **Thread-safe operations** using mutex protection and atomic counters
- **Memory-efficient bit array** with custom implementation
- **Template-based design** for flexibility and performance
- **Modern C++ features** (C++17 and later)
- **RAII and smart pointers** for automatic memory management
- **Comprehensive statistics** and monitoring capabilities

## Architecture

The implementation consists of several key classes:

- **BloomFilter**: Main filter class with optimized operations
- **DynamicBitArray**: Custom bit array implementation with thread safety
- **HashFunctions**: Collection of high-performance hash functions
- **BloomFilterStats**: Statistics and monitoring data
- **BloomFilterBuilder**: Builder pattern for flexible construction

## Compilation and Execution

```bash
# Compile with C++17 support and optimizations
g++ -std=c++17 -O3 -pthread bloom_filter.cpp -o bloom_filter

# Run the demonstration
./bloom_filter
```

### Compiler Requirements

- **C++17 or later** for std::optional and other modern features
- **pthread support** for threading capabilities
- **Recommended compilers**: GCC 7+, Clang 5+, MSVC 2017+

### Build Options

```bash
# Debug build with symbols
g++ -std=c++17 -g -pthread -DDEBUG bloom_filter.cpp -o bloom_filter_debug

# Release build with maximum optimizations
g++ -std=c++17 -O3 -DNDEBUG -pthread -march=native bloom_filter.cpp -o bloom_filter_release

# With additional warnings and static analysis
g++ -std=c++17 -O2 -pthread -Wall -Wextra -Wpedantic -Wconversion bloom_filter.cpp -o bloom_filter
```

## Usage

### Basic Example

```cpp
#include "bloom_filter.cpp"

int main() {
    // Create filter for 10,000 elements with 1% false positive rate
    BloomFilter bf(10000, 0.01);
    
    // Add elements
    bf.add("example.com");
    bf.add("google.com");
    
    // Test membership
    bool exists = bf.contains("example.com");  // true (definitely added)
    bool maybe = bf.contains("unknown.com");   // false (definitely not) or true (false positive)
    
    return 0;
}
```

### Advanced Configuration

```cpp
// Use builder pattern for custom configuration
auto bf = BloomFilterBuilder()
    .withExpectedElements(50000)
    .withFalsePositiveRate(0.001)  // 0.1% false positive rate
    .build();

// Get comprehensive statistics
BloomFilterStats stats = bf->getStats();
std::cout << "Memory usage: " << stats.memoryUsage << " bytes" << std::endl;
std::cout << "Fill ratio: " << std::fixed << std::setprecision(2) 
          << stats.fillRatio * 100 << "%" << std::endl;
```

### Thread-Safe Usage

```cpp
#include <thread>
#include <vector>

// The implementation is thread-safe
BloomFilter bf(10000, 0.01);

// Multiple threads can safely add and query
std::vector<std::thread> threads;

for (int i = 0; i < 10; ++i) {
    threads.emplace_back([&bf, i]() {
        for (int j = 0; j < 100; ++j) {
            std::string element = "element-" + std::to_string(i * 100 + j);
            bf.add(element);
            bool exists = bf.contains(element);
        }
    });
}

for (auto& t : threads) {
    t.join();
}
```

## Performance Characteristics

- **Time Complexity**: O(k) for both add and contains operations, where k is the number of hash functions
- **Space Complexity**: O(m) where m is the bit array size
- **Memory Efficiency**: Custom bit array with minimal overhead
- **Thread Safety**: Fine-grained locking with atomic counters
- **Cache Efficiency**: Optimized memory layout and access patterns

## Hash Functions

The implementation includes multiple high-performance hash functions:

1. **MurmurHash3**: Industry-standard fast hash with excellent distribution
2. **FNV-1a**: Fast hash function with good avalanche properties
3. **DJB2**: Simple and effective hash function
4. **SDBM**: Hash function from SDBM database project
5. **std::hash**: Standard library hash function as fallback

## Mathematical Foundation

### Optimal Parameters

The implementation automatically calculates optimal parameters:

```cpp
// Bit array size: m = -(n × ln(p)) / (ln(2)²)
static size_t calculateBitArraySize(size_t expectedElements, double falsePositiveRate) {
    double size = -(static_cast<double>(expectedElements) * std::log(falsePositiveRate)) / 
                  (std::log(2.0) * std::log(2.0));
    return std::max(1UL, static_cast<size_t>(std::ceil(size)));
}

// Number of hash functions: k = (m / n) × ln(2)
static size_t calculateNumHashFunctions(size_t bitArraySize, size_t expectedElements) {
    double k = (static_cast<double>(bitArraySize) / expectedElements) * std::log(2.0);
    return std::max(1UL, static_cast<size_t>(std::round(k)));
}
```

## Memory Management

- **RAII**: Automatic resource management with constructors/destructors
- **Smart pointers**: `std::unique_ptr` for safe memory management
- **Custom allocators**: Optimized memory allocation for bit arrays
- **Memory alignment**: Proper alignment for cache efficiency
- **Leak prevention**: No manual memory management required

## Thread Safety

The implementation provides thread safety through:
- **Mutex protection**: Fine-grained locking for bit array operations
- **Atomic counters**: Lock-free element counting
- **Immutable configuration**: Parameters cannot be changed after creation
- **Exception safety**: Strong exception safety guarantees

## Performance Optimizations

### Bit Manipulation

```cpp
// Optimized bit operations using builtin functions
size_t countSetBits() const {
    size_t count = 0;
    for (uint8_t byte : bits_) {
        count += __builtin_popcount(byte);  // Hardware popcount if available
    }
    return count;
}
```

### Hash Function Selection

```cpp
// Cycle through different hash functions for better distribution
switch (i % 5) {
    case 0: hashValue = HashFunctions::murmurHash3(element, hashSeeds_[i]); break;
    case 1: hashValue = HashFunctions::fnvHash(element + std::to_string(hashSeeds_[i])); break;
    // ... other cases
}
```

## Memory Usage Comparison

For 1 million elements:
- **std::unordered_set<std::string>**: ~80 MB
- **Bloom filter (1% FP)**: ~1.2 MB
- **Bloom filter (0.1% FP)**: ~1.9 MB
- **Memory savings**: 40-60x reduction

## Limitations

- **No element removal**: Bloom filters don't support deletion
- **False positives**: Small probability of false positive results
- **Fixed size**: Cannot resize after creation
- **No element enumeration**: Cannot list stored elements
- **Platform dependencies**: Some optimizations are compiler/platform specific

## Use Cases

- **High-frequency trading**: Ultra-low latency lookups
- **Game engines**: Fast collision detection and caching
- **Database systems**: Reduce expensive disk I/O operations
- **Network applications**: Packet filtering and routing
- **Embedded systems**: Memory-constrained environments
- **Real-time systems**: Predictable performance characteristics

## Extensions

Possible enhancements for production use:
- **SIMD optimizations**: Vectorized bit operations
- **Memory mapping**: Large datasets with mmap
- **Persistent storage**: Serialize/deserialize to disk
- **Compressed representation**: Further memory optimization
- **Custom allocators**: Specialized memory management
- **Template specializations**: Type-specific optimizations

## Benchmarking

```cpp
#include <chrono>

// Benchmark add operations
auto start = std::chrono::high_resolution_clock::now();
for (int i = 0; i < 1000000; ++i) {
    bf.add("element-" + std::to_string(i));
}
auto end = std::chrono::high_resolution_clock::now();

auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);
std::cout << "Add operations: " << duration.count() << " microseconds" << std::endl;
```

## Dependencies

This implementation uses only standard C++ libraries:
- `<vector>`, `<string>` for containers
- `<mutex>`, `<atomic>` for thread safety
- `<cmath>` for mathematical functions
- `<memory>` for smart pointers
- `<functional>` for hash functions
- `<optional>` for optional values (C++17)

No external dependencies are required, making it easy to integrate into existing C++ projects.