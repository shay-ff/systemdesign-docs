# Bloom Filter - Go Implementation

This directory contains a Go implementation of a Bloom filter using idiomatic Go patterns, goroutines, and efficient bit manipulation.

## Features

- **Idiomatic Go implementation** using interfaces, channels, and Go conventions
- **Multiple hash functions** including MurmurHash3, FNV, DJB2, SDBM, and SHA1
- **Concurrent-safe operations** using sync.RWMutex and atomic operations
- **Memory-efficient bit array** with optimized 64-bit word operations
- **Builder pattern** for flexible filter construction
- **Comprehensive statistics** and JSON serialization support
- **Zero external dependencies** using only Go standard library

## Architecture

The implementation consists of several key types:

- **BloomFilter**: Main filter struct with optimized operations
- **BitArray**: Thread-safe bit array with 64-bit word operations
- **BloomFilterStats**: Statistics with JSON tags for serialization
- **BloomFilterBuilder**: Builder pattern for flexible construction
- **HashFunction**: Function type for pluggable hash functions

## Compilation and Execution

```bash
# Run directly
go run bloom_filter.go

# Build executable
go build -o bloom_filter bloom_filter.go

# Run the executable
./bloom_filter
```

### Go Version Requirements

- **Go 1.13 or later** for module support and error wrapping
- **Standard library only** - no external dependencies

## Usage

### Basic Example

```go
package main

import (
    "fmt"
    "log"
)

func main() {
    // Create filter for 10,000 elements with 1% false positive rate
    bf, err := NewBloomFilter(10000, 0.01)
    if err != nil {
        log.Fatal(err)
    }
    
    // Add elements
    bf.Add("example.com")
    bf.Add("google.com")
    
    // Test membership
    exists := bf.Contains("example.com")  // true (definitely added)
    maybe := bf.Contains("unknown.com")   // false (definitely not) or true (false positive)
    
    fmt.Printf("example.com exists: %t\n", exists)
    fmt.Printf("unknown.com maybe exists: %t\n", maybe)
}
```

### Advanced Configuration

```go
// Use builder pattern for custom configuration
bf, err := NewBloomFilterBuilder().
    WithExpectedElements(50000).
    WithFalsePositiveRate(0.001).  // 0.1% false positive rate
    Build()

if err != nil {
    log.Fatal(err)
}

// Get comprehensive statistics
stats := bf.GetStats()
fmt.Printf("Memory usage: %d bytes\n", stats.MemoryUsage)
fmt.Printf("Fill ratio: %.2f%%\n", stats.FillRatio*100)
```

### Concurrent Usage

```go
import (
    "sync"
)

// The implementation is concurrent-safe
bf, _ := NewBloomFilter(10000, 0.01)

var wg sync.WaitGroup

// Multiple goroutines can safely add and query
for i := 0; i < 10; i++ {
    wg.Add(1)
    go func(id int) {
        defer wg.Done()
        for j := 0; j < 100; j++ {
            element := fmt.Sprintf("element-%d-%d", id, j)
            bf.Add(element)
            exists := bf.Contains(element)
            _ = exists
        }
    }(i)
}

wg.Wait()
```

## Performance Characteristics

- **Time Complexity**: O(k) for both Add and Contains operations, where k is the number of hash functions
- **Space Complexity**: O(m) where m is the bit array size
- **Memory Efficiency**: Uses 64-bit words for optimal memory usage
- **Concurrency**: Excellent concurrent performance with RWMutex
- **Cache Efficiency**: Optimized memory layout and access patterns

## Hash Functions

The implementation includes multiple high-quality hash functions:

1. **MurmurHash3**: Industry-standard fast hash with excellent distribution
2. **FNV**: Go's built-in FNV hash function
3. **DJB2**: Simple and effective hash function
4. **SDBM**: Hash function from SDBM database project
5. **SHA1**: Cryptographic hash for high-quality distribution

## Mathematical Foundation

### Optimal Parameters

The implementation automatically calculates optimal parameters:

```go
// Bit array size: m = -(n × ln(p)) / (ln(2)²)
func calculateBitArraySize(expectedElements uint32, falsePositiveRate float64) uint32 {
    size := -(float64(expectedElements) * math.Log(falsePositiveRate)) / (math.Log(2) * math.Log(2))
    return uint32(math.Max(1, math.Ceil(size)))
}

// Number of hash functions: k = (m / n) × ln(2)
func calculateNumHashFunctions(bitArraySize, expectedElements uint32) uint32 {
    k := (float64(bitArraySize) / float64(expectedElements)) * math.Log(2)
    return uint32(math.Max(1, math.Round(k)))
}
```

## Concurrency Model

The implementation leverages Go's concurrency features:
- **sync.RWMutex**: Reader-writer locks for bit array operations
- **atomic operations**: Lock-free counters for element count
- **Goroutine-safe**: All public methods are safe for concurrent use
- **No channels**: Direct synchronization for maximum performance

## Memory Management

- **Garbage collection**: Automatic memory management by Go runtime
- **Efficient bit packing**: Uses uint64 words for optimal memory usage
- **Memory alignment**: Proper alignment for cache efficiency
- **Bounded memory**: Configurable limits prevent unbounded growth

## Error Handling

- **Idiomatic errors**: Returns error values following Go conventions
- **Input validation**: Comprehensive parameter validation
- **Graceful degradation**: Handles edge cases gracefully
- **No panics**: All errors are returned, no runtime panics

## JSON Serialization

```go
import (
    "encoding/json"
    "fmt"
)

// Statistics can be serialized to JSON
stats := bf.GetStats()
jsonData, err := json.Marshal(stats)
if err != nil {
    log.Fatal(err)
}

fmt.Printf("Stats JSON: %s\n", jsonData)
```

## Performance Optimizations

### Bit Operations

```go
// Optimized popcount using bit manipulation
func popcount(x uint64) int {
    count := 0
    for x != 0 {
        count++
        x &= x - 1 // Clear the lowest set bit (Brian Kernighan's algorithm)
    }
    return count
}
```

### Hash Function Cycling

```go
// Cycle through different hash functions for better distribution
for i := uint32(0); i < bf.numHashFunctions; i++ {
    hashFunc := bf.hashFunctions[i%uint32(len(bf.hashFunctions))]
    hashValue := hashFunc(data, bf.hashSeeds[i])
    hashes[i] = hashValue % bf.bitArraySize
}
```

## Memory Usage Comparison

For 1 million elements:
- **map[string]bool**: ~80 MB
- **Bloom filter (1% FP)**: ~1.2 MB
- **Bloom filter (0.1% FP)**: ~1.9 MB
- **Memory savings**: 40-60x reduction

## Limitations

- **No element removal**: Bloom filters don't support deletion
- **False positives**: Small probability of false positive results
- **Fixed size**: Cannot resize after creation
- **No element enumeration**: Cannot list stored elements
- **Memory overhead**: Some overhead from Go's runtime

## Use Cases

- **Web services**: Fast cache hit/miss determination
- **Microservices**: Reduce network calls between services
- **Database optimization**: Avoid expensive disk lookups
- **Content delivery**: Cache presence checking
- **Real-time systems**: Low-latency membership testing
- **Distributed systems**: Reduce inter-node communication

## Extensions

Possible enhancements for production use:
- **Persistent storage**: Save/load filter state to disk
- **gRPC/HTTP API**: Network-accessible Bloom filter service
- **Metrics integration**: Prometheus metrics export
- **Custom hash functions**: Pluggable hash function interface
- **Compressed representation**: Further memory optimization
- **Clustering support**: Distributed Bloom filter operations

## Testing

```go
// Example test function
func TestBloomFilter(t *testing.T) {
    bf, err := NewBloomFilter(1000, 0.01)
    if err != nil {
        t.Fatal(err)
    }
    
    // Test basic operations
    bf.Add("test")
    if !bf.Contains("test") {
        t.Error("Expected element to be found")
    }
    
    if bf.Contains("nonexistent") {
        // This might be a false positive, which is acceptable
        t.Log("False positive detected (acceptable)")
    }
}
```

## Benchmarking

```go
// Example benchmark
func BenchmarkBloomFilterAdd(b *testing.B) {
    bf, _ := NewBloomFilter(uint32(b.N), 0.01)
    
    b.ResetTimer()
    for i := 0; i < b.N; i++ {
        bf.Add(fmt.Sprintf("element-%d", i))
    }
}

func BenchmarkBloomFilterContains(b *testing.B) {
    bf, _ := NewBloomFilter(1000, 0.01)
    
    // Pre-populate
    for i := 0; i < 1000; i++ {
        bf.Add(fmt.Sprintf("element-%d", i))
    }
    
    b.ResetTimer()
    for i := 0; i < b.N; i++ {
        bf.Contains(fmt.Sprintf("element-%d", i%1000))
    }
}
```

## Dependencies

This implementation uses only Go standard library packages:
- `sync` and `sync/atomic` for concurrency
- `math` for mathematical functions
- `crypto/sha1` and `hash/fnv` for hash functions
- `encoding/binary` for byte operations
- `fmt` for string formatting

No external dependencies are required, making it easy to integrate into existing Go projects.