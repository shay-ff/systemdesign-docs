# Bloom Filter - Python Implementation

This directory contains a Python implementation of a Bloom filter with configurable parameters and multiple hash functions.

## Features

- **Configurable false positive rate** with automatic parameter optimization
- **Multiple hash functions** including MurmurHash3, FNV, DJB2, SDBM, and SHA1
- **Memory-efficient bit array** using bytearray for optimal memory usage
- **Builder pattern** for flexible filter construction
- **Comprehensive statistics** and monitoring capabilities
- **Performance benchmarking** and comparison tools
- **Thread-safe operations** (with external synchronization)

## Installation

```bash
# Optional: Install MurmurHash3 for better performance
pip install mmh3

# The implementation includes fallback hash functions if mmh3 is not available
```

## Architecture

The implementation consists of several key classes:

- **BloomFilter**: Main filter class with add/contains operations
- **BitArray**: Memory-efficient bit storage using bytearray
- **HashFunctions**: Collection of hash function implementations
- **BloomFilterStats**: Statistics and monitoring data
- **BloomFilterBuilder**: Builder pattern for flexible construction

## Usage

### Basic Example

```python
from bloom_filter import BloomFilter

# Create filter for 10,000 elements with 1% false positive rate
bf = BloomFilter(expected_elements=10000, false_positive_rate=0.01)

# Add elements
bf.add("example.com")
bf.add("google.com")

# Test membership
print("example.com" in bf)  # True (definitely added)
print("unknown.com" in bf)  # False (definitely not added) or True (false positive)
```

### Advanced Configuration

```python
from bloom_filter import BloomFilterBuilder

# Use builder pattern for custom configuration
bf = (BloomFilterBuilder()
      .with_expected_elements(50000)
      .with_false_positive_rate(0.001)  # 0.1% false positive rate
      .build())

# Manual parameter override
bf_custom = (BloomFilterBuilder()
             .with_expected_elements(10000)
             .with_bit_array_size(100000)  # Custom size
             .with_num_hash_functions(7)   # Custom hash count
             .build())
```

### Statistics and Monitoring

```python
# Get comprehensive statistics
stats = bf.get_stats()
print(f"Memory usage: {stats.memory_usage} bytes")
print(f"Fill ratio: {stats.fill_ratio:.2%}")
print(f"Actual false positive rate: {stats.get_actual_false_positive_rate():.4f}")

# Monitor performance
print(f"Elements added: {len(bf)}")
print(f"Expected false positives: {bf.get_false_positive_rate():.4f}")
```

## Running the Demo

```bash
python bloom_filter.py
```

This will demonstrate:
- Filter creation with optimal parameters
- Adding elements and testing membership
- False positive rate analysis
- Memory usage comparison with regular sets
- Performance characteristics

## Performance Characteristics

- **Time Complexity**: O(k) for both add and contains operations, where k is the number of hash functions
- **Space Complexity**: O(m) where m is the bit array size, typically much smaller than storing actual elements
- **Memory Efficiency**: 60-80% memory savings compared to storing actual elements
- **False Positive Rate**: Configurable, typically 0.1% to 5%

## Hash Functions

The implementation includes multiple hash functions for better distribution:

1. **MurmurHash3**: Fast, high-quality hash (requires mmh3 package)
2. **FNV-1a**: Fast hash with good distribution
3. **DJB2**: Simple and effective hash function
4. **SDBM**: Hash function from SDBM database
5. **SHA1**: Cryptographic hash for high-quality distribution

## Mathematical Foundation

### Optimal Parameters

The implementation automatically calculates optimal parameters:

```python
# Bit array size: m = -(n × ln(p)) / (ln(2)²)
bit_array_size = -(expected_elements * math.log(false_positive_rate)) / (math.log(2) ** 2)

# Number of hash functions: k = (m / n) × ln(2)
num_hash_functions = (bit_array_size / expected_elements) * math.log(2)

# Actual false positive rate: p = (1 - e^(-kn/m))^k
actual_rate = (1 - math.exp(-num_hash_functions * num_elements / bit_array_size)) ** num_hash_functions
```

## Memory Usage

For 1 million elements:
- **Regular set**: ~80 MB (assuming 20-char average strings)
- **Bloom filter (1% FP)**: ~1.2 MB
- **Bloom filter (0.1% FP)**: ~1.9 MB
- **Memory savings**: 40-60x reduction

## Thread Safety

The basic implementation is not thread-safe. For concurrent access:

```python
import threading

class ThreadSafeBloomFilter:
    def __init__(self, *args, **kwargs):
        self.bf = BloomFilter(*args, **kwargs)
        self.lock = threading.RLock()
    
    def add(self, element):
        with self.lock:
            self.bf.add(element)
    
    def contains(self, element):
        with self.lock:
            return self.bf.contains(element)
```

## Limitations

- **No element removal**: Bloom filters don't support deletion
- **False positives**: Small probability of false positive results
- **Fixed size**: Cannot resize after creation
- **No element enumeration**: Cannot list stored elements

## Use Cases

- **Web crawling**: Avoid revisiting URLs
- **Caching**: Quick cache hit/miss determination
- **Database optimization**: Reduce expensive disk lookups
- **Network security**: Malicious URL/IP detection
- **Distributed systems**: Reduce network calls
- **Content filtering**: Spam and malware detection

## Extensions

Possible enhancements:
- **Counting Bloom Filter**: Support for element removal
- **Scalable Bloom Filter**: Dynamic resizing capability
- **Compressed Bloom Filter**: Further memory optimization
- **Persistent storage**: Save/load filter state
- **Network protocol**: Distributed Bloom filter operations

## Dependencies

- **Python 3.6+**: For type hints and modern features
- **mmh3** (optional): For MurmurHash3 implementation
- **Standard library only**: No required external dependencies

The implementation gracefully falls back to built-in hash functions if mmh3 is not available.