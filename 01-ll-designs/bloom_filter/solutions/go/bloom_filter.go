/*
Bloom Filter Implementation in Go

This implementation provides a space-efficient probabilistic data structure
for membership testing with configurable false positive rates.

Features:
- Idiomatic Go implementation using channels and goroutines
- Multiple hash functions for better distribution
- Concurrent-safe operations using sync primitives
- Memory-efficient bit manipulation
- Comprehensive statistics and monitoring
- Builder pattern for flexible configuration
*/

package main

import (
	"crypto/sha1"
	"encoding/binary"
	"fmt"
	"hash/fnv"
	"math"
	"math/rand"
	"sync"
	"sync/atomic"
	"time"
)

// HashFunction represents a hash function interface
type HashFunction func(data []byte, seed uint32) uint32

// Collection of hash functions
var (
	// MurmurHash3 implementation
	murmurHash3 HashFunction = func(data []byte, seed uint32) uint32 {
		const (
			c1 = 0xcc9e2d51
			c2 = 0x1b873593
			r1 = 15
			r2 = 13
			m  = 5
			n  = 0xe6546b64
		)

		hash := seed
		length := len(data)
		roundedEnd := (length >> 2) << 2

		for i := 0; i < roundedEnd; i += 4 {
			k := binary.LittleEndian.Uint32(data[i : i+4])
			k *= c1
			k = (k << r1) | (k >> (32 - r1))
			k *= c2
			hash ^= k
			hash = ((hash << r2) | (hash >> (32 - r2)))*m + n
		}

		k1 := uint32(0)
		switch length & 3 {
		case 3:
			k1 ^= uint32(data[roundedEnd+2]) << 16
			fallthrough
		case 2:
			k1 ^= uint32(data[roundedEnd+1]) << 8
			fallthrough
		case 1:
			k1 ^= uint32(data[roundedEnd])
			k1 *= c1
			k1 = (k1 << r1) | (k1 >> (32 - r1))
			k1 *= c2
			hash ^= k1
		}

		hash ^= uint32(length)
		hash ^= hash >> 16
		hash *= 0x85ebca6b
		hash ^= hash >> 13
		hash *= 0xc2b2ae35
		hash ^= hash >> 16

		return hash
	}

	// FNV-1a hash function
	fnvHash HashFunction = func(data []byte, seed uint32) uint32 {
		h := fnv.New32a()
		h.Write(data)
		h.Write([]byte{byte(seed), byte(seed >> 8), byte(seed >> 16), byte(seed >> 24)})
		return h.Sum32()
	}

	// DJB2 hash function
	djb2Hash HashFunction = func(data []byte, seed uint32) uint32 {
		hash := uint32(5381) + seed
		for _, b := range data {
			hash = ((hash << 5) + hash) + uint32(b)
		}
		return hash
	}

	// SDBM hash function
	sdbmHash HashFunction = func(data []byte, seed uint32) uint32 {
		hash := seed
		for _, b := range data {
			hash = uint32(b) + (hash << 6) + (hash << 16) - hash
		}
		return hash
	}

	// SHA1-based hash function
	sha1Hash HashFunction = func(data []byte, seed uint32) uint32 {
		h := sha1.New()
		h.Write(data)
		seedBytes := make([]byte, 4)
		binary.LittleEndian.PutUint32(seedBytes, seed)
		h.Write(seedBytes)
		digest := h.Sum(nil)
		return binary.LittleEndian.Uint32(digest[:4])
	}
)

// BitArray represents a thread-safe bit array
type BitArray struct {
	bits []uint64
	size uint32
	mu   sync.RWMutex
}

// NewBitArray creates a new bit array
func NewBitArray(size uint32) *BitArray {
	numWords := (size + 63) / 64
	return &BitArray{
		bits: make([]uint64, numWords),
		size: size,
	}
}

// SetBit sets a bit at the given index
func (ba *BitArray) SetBit(index uint32) {
	if index >= ba.size {
		return
	}

	ba.mu.Lock()
	defer ba.mu.Unlock()

	wordIndex := index / 64
	bitIndex := index % 64
	ba.bits[wordIndex] |= 1 << bitIndex
}

// GetBit gets the value of a bit at the given index
func (ba *BitArray) GetBit(index uint32) bool {
	if index >= ba.size {
		return false
	}

	ba.mu.RLock()
	defer ba.mu.RUnlock()

	wordIndex := index / 64
	bitIndex := index % 64
	return (ba.bits[wordIndex] & (1 << bitIndex)) != 0
}

// Clear clears all bits
func (ba *BitArray) Clear() {
	ba.mu.Lock()
	defer ba.mu.Unlock()

	for i := range ba.bits {
		ba.bits[i] = 0
	}
}

// CountSetBits counts the number of set bits
func (ba *BitArray) CountSetBits() uint32 {
	ba.mu.RLock()
	defer ba.mu.RUnlock()

	count := uint32(0)
	for _, word := range ba.bits {
		count += uint32(popcount(word))
	}
	return count
}

// GetMemoryUsage returns memory usage in bytes
func (ba *BitArray) GetMemoryUsage() uint32 {
	return uint32(len(ba.bits) * 8)
}

// popcount counts the number of set bits in a uint64
func popcount(x uint64) int {
	count := 0
	for x != 0 {
		count++
		x &= x - 1 // Clear the lowest set bit
	}
	return count
}

// BloomFilterStats represents statistics for a Bloom filter
type BloomFilterStats struct {
	BitArraySize       uint32  `json:"bitArraySize"`
	NumHashFunctions   uint32  `json:"numHashFunctions"`
	NumElements        uint32  `json:"numElements"`
	ExpectedElements   uint32  `json:"expectedElements"`
	FalsePositiveRate  float64 `json:"falsePositiveRate"`
	MemoryUsage        uint32  `json:"memoryUsage"`
	FillRatio          float64 `json:"fillRatio"`
}

// UpdateFillRatio updates the fill ratio based on set bits
func (s *BloomFilterStats) UpdateFillRatio(setBits uint32) {
	if s.BitArraySize > 0 {
		s.FillRatio = float64(setBits) / float64(s.BitArraySize)
	} else {
		s.FillRatio = 0.0
	}
}

// GetActualFalsePositiveRate calculates the actual false positive rate
func (s *BloomFilterStats) GetActualFalsePositiveRate() float64 {
	if s.NumElements == 0 {
		return 0.0
	}

	// p = (1 - e^(-kn/m))^k
	exponent := -float64(s.NumHashFunctions*s.NumElements) / float64(s.BitArraySize)
	return math.Pow(1.0-math.Exp(exponent), float64(s.NumHashFunctions))
}

// String returns a string representation of the stats
func (s *BloomFilterStats) String() string {
	return fmt.Sprintf("BloomFilterStats{size=%d, hashFunctions=%d, elements=%d/%d, "+
		"falsePositiveRate=%.4f, fillRatio=%.4f, memory=%d bytes}",
		s.BitArraySize, s.NumHashFunctions, s.NumElements, s.ExpectedElements,
		s.FalsePositiveRate, s.FillRatio, s.MemoryUsage)
}

// BloomFilter represents the main Bloom filter structure
type BloomFilter struct {
	bitArray          *BitArray
	bitArraySize      uint32
	numHashFunctions  uint32
	expectedElements  uint32
	falsePositiveRate float64
	numElements       uint32
	hashFunctions     []HashFunction
	hashSeeds         []uint32
}

// NewBloomFilter creates a new Bloom filter with optimal parameters
func NewBloomFilter(expectedElements uint32, falsePositiveRate float64) (*BloomFilter, error) {
	if expectedElements == 0 {
		return nil, fmt.Errorf("expected elements must be positive")
	}
	if falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0 {
		return nil, fmt.Errorf("false positive rate must be between 0 and 1")
	}

	// Calculate optimal parameters
	bitArraySize := calculateBitArraySize(expectedElements, falsePositiveRate)
	numHashFunctions := calculateNumHashFunctions(bitArraySize, expectedElements)

	// Initialize hash functions and seeds
	hashFunctions := []HashFunction{murmurHash3, fnvHash, djb2Hash, sdbmHash, sha1Hash}
	hashSeeds := make([]uint32, numHashFunctions)
	for i := uint32(0); i < numHashFunctions; i++ {
		hashSeeds[i] = i
	}

	return &BloomFilter{
		bitArray:          NewBitArray(bitArraySize),
		bitArraySize:      bitArraySize,
		numHashFunctions:  numHashFunctions,
		expectedElements:  expectedElements,
		falsePositiveRate: falsePositiveRate,
		numElements:       0,
		hashFunctions:     hashFunctions,
		hashSeeds:         hashSeeds,
	}, nil
}

// calculateBitArraySize calculates the optimal bit array size
func calculateBitArraySize(expectedElements uint32, falsePositiveRate float64) uint32 {
	// m = -(n * ln(p)) / (ln(2)^2)
	size := -(float64(expectedElements) * math.Log(falsePositiveRate)) / (math.Log(2) * math.Log(2))
	return uint32(math.Max(1, math.Ceil(size)))
}

// calculateNumHashFunctions calculates the optimal number of hash functions
func calculateNumHashFunctions(bitArraySize, expectedElements uint32) uint32 {
	// k = (m / n) * ln(2)
	k := (float64(bitArraySize) / float64(expectedElements)) * math.Log(2)
	return uint32(math.Max(1, math.Round(k)))
}

// getHashValues gets hash values for an element
func (bf *BloomFilter) getHashValues(element string) []uint32 {
	data := []byte(element)
	hashes := make([]uint32, bf.numHashFunctions)

	for i := uint32(0); i < bf.numHashFunctions; i++ {
		hashFunc := bf.hashFunctions[i%uint32(len(bf.hashFunctions))]
		hashValue := hashFunc(data, bf.hashSeeds[i])
		hashes[i] = hashValue % bf.bitArraySize
	}

	return hashes
}

// Add adds an element to the Bloom filter
func (bf *BloomFilter) Add(element string) {
	hashValues := bf.getHashValues(element)

	for _, hashValue := range hashValues {
		bf.bitArray.SetBit(hashValue)
	}

	atomic.AddUint32(&bf.numElements, 1)
}

// Contains tests if an element might be in the set
func (bf *BloomFilter) Contains(element string) bool {
	hashValues := bf.getHashValues(element)

	for _, hashValue := range hashValues {
		if !bf.bitArray.GetBit(hashValue) {
			return false
		}
	}

	return true
}

// Clear clears all elements from the filter
func (bf *BloomFilter) Clear() {
	bf.bitArray.Clear()
	atomic.StoreUint32(&bf.numElements, 0)
}

// GetStats returns current statistics
func (bf *BloomFilter) GetStats() *BloomFilterStats {
	stats := &BloomFilterStats{
		BitArraySize:      bf.bitArraySize,
		NumHashFunctions:  bf.numHashFunctions,
		NumElements:       atomic.LoadUint32(&bf.numElements),
		ExpectedElements:  bf.expectedElements,
		FalsePositiveRate: bf.falsePositiveRate,
		MemoryUsage:       bf.bitArray.GetMemoryUsage(),
	}

	stats.UpdateFillRatio(bf.bitArray.CountSetBits())
	return stats
}

// GetFalsePositiveRate returns the actual false positive rate
func (bf *BloomFilter) GetFalsePositiveRate() float64 {
	return bf.GetStats().GetActualFalsePositiveRate()
}

// GetMemoryUsage returns memory usage in bytes
func (bf *BloomFilter) GetMemoryUsage() uint32 {
	return bf.bitArray.GetMemoryUsage()
}

// Size returns the number of elements added
func (bf *BloomFilter) Size() uint32 {
	return atomic.LoadUint32(&bf.numElements)
}

// Getters
func (bf *BloomFilter) GetBitArraySize() uint32      { return bf.bitArraySize }
func (bf *BloomFilter) GetNumHashFunctions() uint32  { return bf.numHashFunctions }
func (bf *BloomFilter) GetExpectedElements() uint32  { return bf.expectedElements }

// BloomFilterBuilder implements the builder pattern
type BloomFilterBuilder struct {
	expectedElements  *uint32
	falsePositiveRate float64
}

// NewBloomFilterBuilder creates a new builder
func NewBloomFilterBuilder() *BloomFilterBuilder {
	return &BloomFilterBuilder{
		falsePositiveRate: 0.01,
	}
}

// WithExpectedElements sets the expected number of elements
func (b *BloomFilterBuilder) WithExpectedElements(n uint32) *BloomFilterBuilder {
	b.expectedElements = &n
	return b
}

// WithFalsePositiveRate sets the desired false positive rate
func (b *BloomFilterBuilder) WithFalsePositiveRate(rate float64) *BloomFilterBuilder {
	b.falsePositiveRate = rate
	return b
}

// Build creates the Bloom filter
func (b *BloomFilterBuilder) Build() (*BloomFilter, error) {
	if b.expectedElements == nil {
		return nil, fmt.Errorf("expected elements must be specified")
	}
	return NewBloomFilter(*b.expectedElements, b.falsePositiveRate)
}

// demo demonstrates the Bloom filter functionality
func demo() {
	fmt.Println("=== Bloom Filter Demo ===\n")

	// Create Bloom filter for 10,000 elements with 1% false positive rate
	bf, err := NewBloomFilter(10000, 0.01)
	if err != nil {
		fmt.Printf("Error creating Bloom filter: %v\n", err)
		return
	}

	fmt.Printf("Created Bloom filter: %s\n\n", bf.GetStats())

	// Add some elements
	websites := []string{
		"google.com", "facebook.com", "twitter.com", "github.com",
		"stackoverflow.com", "reddit.com", "youtube.com", "amazon.com",
		"netflix.com", "spotify.com",
	}

	fmt.Println("Adding websites to filter...")
	for _, website := range websites {
		bf.Add(website)
		fmt.Printf("Added: %s\n", website)
	}

	fmt.Printf("\nFilter stats after adding %d elements:\n", len(websites))
	fmt.Println(bf.GetStats())

	// Test membership
	fmt.Println("\n=== Membership Tests ===")

	// Test existing elements
	fmt.Println("Testing existing elements:")
	for i := 0; i < 5 && i < len(websites); i++ {
		result := bf.Contains(websites[i])
		fmt.Printf("'%s' in filter: %t\n", websites[i], result)
	}

	// Test non-existing elements
	fmt.Println("\nTesting non-existing elements:")
	testSites := []string{"nonexistent.com", "fake-site.org", "not-real.net"}
	for _, site := range testSites {
		result := bf.Contains(site)
		fmt.Printf("'%s' in filter: %t\n", site, result)
	}

	// Performance comparison
	fmt.Println("\n=== Performance Comparison ===")

	// Create a regular map for comparison
	regularSet := make(map[string]bool)
	for _, website := range websites {
		regularSet[website] = true
	}

	fmt.Printf("Bloom filter memory usage: %d bytes\n", bf.GetMemoryUsage())

	// Estimate regular set memory usage
	setMemoryEstimate := 0
	for website := range regularSet {
		setMemoryEstimate += len(website) + 16 // rough estimate for map overhead
	}

	fmt.Printf("Regular set memory usage: ~%d bytes (estimate)\n", setMemoryEstimate)
	if bf.GetMemoryUsage() > 0 {
		fmt.Printf("Memory savings: ~%.1fx\n", float64(setMemoryEstimate)/float64(bf.GetMemoryUsage()))
	}

	// Test false positive rate
	fmt.Println("\n=== False Positive Rate Test ===")
	falsePositives := 0
	testCount := 1000

	websiteSet := make(map[string]bool)
	for _, website := range websites {
		websiteSet[website] = true
	}

	for i := 0; i < testCount; i++ {
		testElement := fmt.Sprintf("test-element-%d", i)
		if !websiteSet[testElement] && bf.Contains(testElement) {
			falsePositives++
		}
	}

	actualFpRate := float64(falsePositives) / float64(testCount)
	expectedFpRate := bf.GetFalsePositiveRate()

	fmt.Printf("Expected false positive rate: %.4f\n", expectedFpRate)
	fmt.Printf("Actual false positive rate: %.4f\n", actualFpRate)
	fmt.Printf("False positives in %d tests: %d\n", testCount, falsePositives)

	fmt.Println("\nDemo completed!")
}

func main() {
	rand.Seed(time.Now().UnixNano())
	demo()
}