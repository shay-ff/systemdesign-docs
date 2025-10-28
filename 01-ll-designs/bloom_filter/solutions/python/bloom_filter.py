"""
Bloom Filter Implementation in Python

This implementation provides a space-efficient probabilistic data structure
for membership testing with configurable false positive rates.

Features:
- Configurable false positive rate
- Multiple hash functions for better distribution
- Optimal parameter calculation
- Memory-efficient bit array implementation
- Statistics and monitoring capabilities
"""

import math
import hashlib
import mmh3  # MurmurHash3 - install with: pip install mmh3
from typing import Union, List, Optional
import struct


class BitArray:
    """Memory-efficient bit array implementation"""
    
    def __init__(self, size: int):
        self.size = size
        # Use bytearray for memory efficiency
        self.array = bytearray((size + 7) // 8)  # Round up to nearest byte
    
    def set_bit(self, index: int) -> None:
        """Set bit at given index to 1"""
        if 0 <= index < self.size:
            byte_index = index // 8
            bit_index = index % 8
            self.array[byte_index] |= (1 << bit_index)
    
    def get_bit(self, index: int) -> bool:
        """Get bit value at given index"""
        if 0 <= index < self.size:
            byte_index = index // 8
            bit_index = index % 8
            return bool(self.array[byte_index] & (1 << bit_index))
        return False
    
    def clear(self) -> None:
        """Clear all bits"""
        for i in range(len(self.array)):
            self.array[i] = 0
    
    def count_set_bits(self) -> int:
        """Count number of set bits"""
        count = 0
        for byte in self.array:
            count += bin(byte).count('1')
        return count
    
    def get_memory_usage(self) -> int:
        """Get memory usage in bytes"""
        return len(self.array)


class HashFunctions:
    """Collection of hash functions for Bloom filter"""
    
    @staticmethod
    def murmur_hash(data: str, seed: int) -> int:
        """MurmurHash3 implementation"""
        try:
            return mmh3.hash(data, seed) & 0x7FFFFFFF  # Ensure positive
        except ImportError:
            # Fallback if mmh3 is not available
            return HashFunctions.djb2_hash(data + str(seed))
    
    @staticmethod
    def fnv_hash(data: str) -> int:
        """FNV-1a hash function"""
        hash_value = 2166136261  # FNV offset basis
        for byte in data.encode('utf-8'):
            hash_value ^= byte
            hash_value *= 16777619  # FNV prime
            hash_value &= 0xFFFFFFFF  # Keep 32-bit
        return hash_value
    
    @staticmethod
    def djb2_hash(data: str) -> int:
        """DJB2 hash function"""
        hash_value = 5381
        for char in data:
            hash_value = ((hash_value << 5) + hash_value + ord(char)) & 0xFFFFFFFF
        return hash_value
    
    @staticmethod
    def sdbm_hash(data: str) -> int:
        """SDBM hash function"""
        hash_value = 0
        for char in data:
            hash_value = (ord(char) + (hash_value << 6) + (hash_value << 16) - hash_value) & 0xFFFFFFFF
        return hash_value
    
    @staticmethod
    def sha1_hash(data: str, seed: int) -> int:
        """SHA1-based hash function"""
        sha1 = hashlib.sha1()
        sha1.update((data + str(seed)).encode('utf-8'))
        return int(sha1.hexdigest()[:8], 16)


class BloomFilterStats:
    """Statistics for Bloom filter"""
    
    def __init__(self, bit_array_size: int, num_hash_functions: int, 
                 num_elements: int, expected_elements: int, 
                 false_positive_rate: float):
        self.bit_array_size = bit_array_size
        self.num_hash_functions = num_hash_functions
        self.num_elements = num_elements
        self.expected_elements = expected_elements
        self.false_positive_rate = false_positive_rate
        self.memory_usage = (bit_array_size + 7) // 8  # bytes
        self.fill_ratio = 0.0
    
    def update_fill_ratio(self, set_bits: int) -> None:
        """Update fill ratio based on set bits"""
        self.fill_ratio = set_bits / self.bit_array_size if self.bit_array_size > 0 else 0.0
    
    def get_actual_false_positive_rate(self) -> float:
        """Calculate actual false positive rate based on current state"""
        if self.num_elements == 0:
            return 0.0
        
        # p = (1 - e^(-kn/m))^k
        exponent = -self.num_hash_functions * self.num_elements / self.bit_array_size
        return (1 - math.exp(exponent)) ** self.num_hash_functions
    
    def __str__(self) -> str:
        return (f"BloomFilterStats(size={self.bit_array_size}, "
                f"hash_functions={self.num_hash_functions}, "
                f"elements={self.num_elements}/{self.expected_elements}, "
                f"false_positive_rate={self.false_positive_rate:.4f}, "
                f"fill_ratio={self.fill_ratio:.4f}, "
                f"memory={self.memory_usage} bytes)")


class BloomFilter:
    """
    Bloom Filter implementation with configurable parameters
    """
    
    def __init__(self, expected_elements: int, false_positive_rate: float = 0.01):
        """
        Initialize Bloom filter with optimal parameters
        
        Args:
            expected_elements: Expected number of elements to be added
            false_positive_rate: Desired false positive probability (0 < rate < 1)
        """
        if expected_elements <= 0:
            raise ValueError("Expected elements must be positive")
        if not (0 < false_positive_rate < 1):
            raise ValueError("False positive rate must be between 0 and 1")
        
        self.expected_elements = expected_elements
        self.false_positive_rate = false_positive_rate
        self.num_elements = 0
        
        # Calculate optimal parameters
        self.bit_array_size = self._calculate_bit_array_size()
        self.num_hash_functions = self._calculate_num_hash_functions()
        
        # Initialize bit array
        self.bit_array = BitArray(self.bit_array_size)
        
        # Hash function seeds
        self.hash_seeds = list(range(self.num_hash_functions))
    
    def _calculate_bit_array_size(self) -> int:
        """Calculate optimal bit array size"""
        # m = -(n * ln(p)) / (ln(2)^2)
        size = -(self.expected_elements * math.log(self.false_positive_rate)) / (math.log(2) ** 2)
        return max(1, int(math.ceil(size)))
    
    def _calculate_num_hash_functions(self) -> int:
        """Calculate optimal number of hash functions"""
        # k = (m / n) * ln(2)
        k = (self.bit_array_size / self.expected_elements) * math.log(2)
        return max(1, int(round(k)))
    
    def _get_hash_values(self, element: str) -> List[int]:
        """Get hash values for an element using multiple hash functions"""
        hashes = []
        
        # Use different hash functions with seeds
        for i in range(self.num_hash_functions):
            if i == 0:
                hash_val = HashFunctions.murmur_hash(element, self.hash_seeds[i])
            elif i == 1:
                hash_val = HashFunctions.fnv_hash(element + str(self.hash_seeds[i]))
            elif i == 2:
                hash_val = HashFunctions.djb2_hash(element + str(self.hash_seeds[i]))
            elif i == 3:
                hash_val = HashFunctions.sdbm_hash(element + str(self.hash_seeds[i]))
            else:
                hash_val = HashFunctions.sha1_hash(element, self.hash_seeds[i])
            
            # Map to bit array index
            hashes.append(hash_val % self.bit_array_size)
        
        return hashes
    
    def add(self, element: Union[str, bytes]) -> None:
        """
        Add an element to the Bloom filter
        
        Args:
            element: Element to add (string or bytes)
        """
        if isinstance(element, bytes):
            element = element.decode('utf-8', errors='ignore')
        
        hash_values = self._get_hash_values(element)
        
        for hash_val in hash_values:
            self.bit_array.set_bit(hash_val)
        
        self.num_elements += 1
    
    def contains(self, element: Union[str, bytes]) -> bool:
        """
        Test if an element might be in the set
        
        Args:
            element: Element to test (string or bytes)
            
        Returns:
            True if element might be in set (possible false positive)
            False if element is definitely not in set (no false negatives)
        """
        if isinstance(element, bytes):
            element = element.decode('utf-8', errors='ignore')
        
        hash_values = self._get_hash_values(element)
        
        for hash_val in hash_values:
            if not self.bit_array.get_bit(hash_val):
                return False
        
        return True
    
    def clear(self) -> None:
        """Clear all elements from the filter"""
        self.bit_array.clear()
        self.num_elements = 0
    
    def get_stats(self) -> BloomFilterStats:
        """Get current statistics"""
        stats = BloomFilterStats(
            self.bit_array_size,
            self.num_hash_functions,
            self.num_elements,
            self.expected_elements,
            self.false_positive_rate
        )
        stats.update_fill_ratio(self.bit_array.count_set_bits())
        return stats
    
    def get_false_positive_rate(self) -> float:
        """Get actual false positive rate based on current state"""
        return self.get_stats().get_actual_false_positive_rate()
    
    def get_memory_usage(self) -> int:
        """Get memory usage in bytes"""
        return self.bit_array.get_memory_usage()
    
    def __len__(self) -> int:
        """Return number of elements added (not necessarily unique)"""
        return self.num_elements
    
    def __contains__(self, element: Union[str, bytes]) -> bool:
        """Support 'in' operator"""
        return self.contains(element)


class BloomFilterBuilder:
    """Builder pattern for creating optimized Bloom filters"""
    
    def __init__(self):
        self._expected_elements: Optional[int] = None
        self._false_positive_rate: float = 0.01
        self._bit_array_size: Optional[int] = None
        self._num_hash_functions: Optional[int] = None
    
    def with_expected_elements(self, n: int) -> 'BloomFilterBuilder':
        """Set expected number of elements"""
        self._expected_elements = n
        return self
    
    def with_false_positive_rate(self, rate: float) -> 'BloomFilterBuilder':
        """Set desired false positive rate"""
        self._false_positive_rate = rate
        return self
    
    def with_bit_array_size(self, size: int) -> 'BloomFilterBuilder':
        """Manually set bit array size (overrides calculated size)"""
        self._bit_array_size = size
        return self
    
    def with_num_hash_functions(self, k: int) -> 'BloomFilterBuilder':
        """Manually set number of hash functions (overrides calculated number)"""
        self._num_hash_functions = k
        return self
    
    def build(self) -> BloomFilter:
        """Build the Bloom filter with specified parameters"""
        if self._expected_elements is None:
            raise ValueError("Expected elements must be specified")
        
        bloom_filter = BloomFilter(self._expected_elements, self._false_positive_rate)
        
        # Override calculated parameters if specified
        if self._bit_array_size is not None:
            bloom_filter.bit_array_size = self._bit_array_size
            bloom_filter.bit_array = BitArray(self._bit_array_size)
        
        if self._num_hash_functions is not None:
            bloom_filter.num_hash_functions = self._num_hash_functions
            bloom_filter.hash_seeds = list(range(self._num_hash_functions))
        
        return bloom_filter


def demo():
    """Demonstrate Bloom filter functionality"""
    print("=== Bloom Filter Demo ===\n")
    
    # Create Bloom filter for 10,000 elements with 1% false positive rate
    bf = BloomFilter(expected_elements=10000, false_positive_rate=0.01)
    
    print(f"Created Bloom filter: {bf.get_stats()}\n")
    
    # Add some elements
    websites = [
        "google.com", "facebook.com", "twitter.com", "github.com",
        "stackoverflow.com", "reddit.com", "youtube.com", "amazon.com",
        "netflix.com", "spotify.com"
    ]
    
    print("Adding websites to filter...")
    for website in websites:
        bf.add(website)
        print(f"Added: {website}")
    
    print(f"\nFilter stats after adding {len(websites)} elements:")
    print(bf.get_stats())
    
    # Test membership
    print("\n=== Membership Tests ===")
    
    # Test existing elements
    print("Testing existing elements:")
    for website in websites[:5]:
        result = bf.contains(website)
        print(f"'{website}' in filter: {result}")
    
    # Test non-existing elements
    print("\nTesting non-existing elements:")
    test_sites = ["nonexistent.com", "fake-site.org", "not-real.net"]
    for site in test_sites:
        result = bf.contains(site)
        print(f"'{site}' in filter: {result}")
    
    # Performance comparison
    print("\n=== Performance Comparison ===")
    
    # Create a regular set for comparison
    regular_set = set(websites)
    
    print(f"Bloom filter memory usage: {bf.get_memory_usage()} bytes")
    print(f"Regular set memory usage: ~{len(str(regular_set))} bytes (approximate)")
    print(f"Memory savings: ~{len(str(regular_set)) / bf.get_memory_usage():.1f}x")
    
    # Test false positive rate
    print("\n=== False Positive Rate Test ===")
    false_positives = 0
    test_count = 1000
    
    for i in range(test_count):
        test_element = f"test-element-{i}"
        if test_element not in websites and bf.contains(test_element):
            false_positives += 1
    
    actual_fp_rate = false_positives / test_count
    expected_fp_rate = bf.get_false_positive_rate()
    
    print(f"Expected false positive rate: {expected_fp_rate:.4f}")
    print(f"Actual false positive rate: {actual_fp_rate:.4f}")
    print(f"False positives in {test_count} tests: {false_positives}")


if __name__ == "__main__":
    demo()