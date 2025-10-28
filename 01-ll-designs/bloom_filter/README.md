# Bloom Filter

## Problem Statement

Design and implement a Bloom filter, a space-efficient probabilistic data structure that is used to test whether an element is a member of a set. False positive matches are possible, but false negatives are not – in other words, a query returns either "possibly in set" or "definitely not in set".

## Requirements

### Functional Requirements
1. **Element Addition**: Add elements to the filter
2. **Membership Testing**: Test if an element might be in the set
3. **Configurable Parameters**: Set expected number of elements and desired false positive rate
4. **Multiple Hash Functions**: Use multiple independent hash functions
5. **Bit Array Management**: Efficiently manage the underlying bit array

### Non-Functional Requirements
1. **Space Efficiency**: Use significantly less memory than storing actual elements
2. **Time Efficiency**: O(k) time complexity for both insertion and lookup, where k is the number of hash functions
3. **False Positive Control**: Configurable false positive probability
4. **No False Negatives**: Never return false negative results
5. **Thread Safety**: Support concurrent access (implementation-dependent)

## Key Components

1. **Bit Array**: Fixed-size array of bits, initially all set to 0
2. **Hash Functions**: Multiple independent hash functions to map elements to bit positions
3. **Parameters**: Number of hash functions (k) and bit array size (m)
4. **False Positive Rate**: Probability of false positives based on parameters

## Mathematical Foundation

### Optimal Parameters

For a Bloom filter with:
- `n` = expected number of elements
- `p` = desired false positive probability
- `m` = size of bit array
- `k` = number of hash functions

**Optimal bit array size:**
```
m = -(n * ln(p)) / (ln(2)^2)
```

**Optimal number of hash functions:**
```
k = (m / n) * ln(2)
```

**Actual false positive probability:**
```
p = (1 - e^(-kn/m))^k
```

## Use Cases

- **Web Crawling**: Avoid revisiting URLs
- **Database Systems**: Reduce disk lookups for non-existent keys
- **Caching**: Check if data might be in cache before expensive lookup
- **Network Security**: Detect malicious URLs or IP addresses
- **Distributed Systems**: Reduce network calls for non-existent data
- **Content Filtering**: Block known spam or malicious content

## Design Considerations

- **Memory vs. Accuracy Trade-off**: Larger bit arrays reduce false positive rate
- **Hash Function Quality**: Good hash functions distribute elements uniformly
- **Parameter Tuning**: Balance between memory usage and false positive rate
- **Scalability**: Consider memory constraints for large datasets
- **Persistence**: Whether to store filter state across restarts

## Implementation Languages

This design is implemented in multiple programming languages:

- **Python**: `solutions/python/` - Using bitarray and multiple hash functions
- **Java**: `solutions/java/` - Using BitSet and custom hash implementations
- **C++**: `solutions/cpp/` - Using std::bitset and hash functions
- **Go**: `solutions/go/` - Using bit manipulation and hash functions

Each implementation demonstrates language-specific optimizations and best practices for bit manipulation and hashing.