/**
 * Bloom Filter Implementation in C++
 * 
 * This implementation provides a space-efficient probabilistic data structure
 * for membership testing with configurable false positive rates.
 * 
 * Features:
 * - High-performance implementation with minimal overhead
 * - Multiple hash functions for better distribution
 * - Template-based design for flexibility
 * - Memory-efficient bit manipulation
 * - Thread-safe operations with mutex protection
 * - Comprehensive statistics and monitoring
 */

#include <iostream>
#include <string>
#include <vector>
#include <bitset>
#include <cmath>
#include <functional>
#include <memory>
#include <mutex>
#include <atomic>
#include <algorithm>
#include <random>
#include <sstream>
#include <iomanip>

/**
 * Collection of hash functions for Bloom filter
 */
class HashFunctions {
public:
    /**
     * MurmurHash3 implementation
     */
    static uint32_t murmurHash3(const std::string& data, uint32_t seed) {
        const uint8_t* key = reinterpret_cast<const uint8_t*>(data.c_str());
        const int len = static_cast<int>(data.length());
        const uint32_t c1 = 0xcc9e2d51;
        const uint32_t c2 = 0x1b873593;
        const uint32_t r1 = 15;
        const uint32_t r2 = 13;
        const uint32_t m = 5;
        const uint32_t n = 0xe6546b64;
        
        uint32_t hash = seed;
        
        const int nblocks = len / 4;
        const uint32_t* blocks = reinterpret_cast<const uint32_t*>(key);
        
        for (int i = 0; i < nblocks; i++) {
            uint32_t k = blocks[i];
            k *= c1;
            k = (k << r1) | (k >> (32 - r1));
            k *= c2;
            
            hash ^= k;
            hash = ((hash << r2) | (hash >> (32 - r2))) * m + n;
        }
        
        const uint8_t* tail = reinterpret_cast<const uint8_t*>(key + nblocks * 4);
        uint32_t k1 = 0;
        
        switch (len & 3) {
            case 3: k1 ^= tail[2] << 16;
            case 2: k1 ^= tail[1] << 8;
            case 1: k1 ^= tail[0];
                k1 *= c1;
                k1 = (k1 << r1) | (k1 >> (32 - r1));
                k1 *= c2;
                hash ^= k1;
        }
        
        hash ^= len;
        hash ^= (hash >> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >> 16);
        
        return hash;
    }
    
    /**
     * FNV-1a hash function
     */
    static uint32_t fnvHash(const std::string& data) {
        uint32_t hash = 2166136261u; // FNV offset basis
        
        for (char c : data) {
            hash ^= static_cast<uint8_t>(c);
            hash *= 16777619u; // FNV prime
        }
        
        return hash;
    }
    
    /**
     * DJB2 hash function
     */
    static uint32_t djb2Hash(const std::string& data) {
        uint32_t hash = 5381;
        
        for (char c : data) {
            hash = ((hash << 5) + hash) + static_cast<uint8_t>(c);
        }
        
        return hash;
    }
    
    /**
     * SDBM hash function
     */
    static uint32_t sdbmHash(const std::string& data) {
        uint32_t hash = 0;
        
        for (char c : data) {
            hash = static_cast<uint8_t>(c) + (hash << 6) + (hash << 16) - hash;
        }
        
        return hash;
    }
    
    /**
     * Simple hash function using std::hash
     */
    static uint32_t stdHash(const std::string& data, uint32_t seed) {
        std::hash<std::string> hasher;
        return static_cast<uint32_t>(hasher(data + std::to_string(seed)));
    }
};

/**
 * Statistics for Bloom filter
 */
struct BloomFilterStats {
    size_t bitArraySize;
    size_t numHashFunctions;
    size_t numElements;
    size_t expectedElements;
    double falsePositiveRate;
    size_t memoryUsage;
    double fillRatio;
    
    BloomFilterStats(size_t bitArraySize, size_t numHashFunctions, size_t numElements,
                    size_t expectedElements, double falsePositiveRate)
        : bitArraySize(bitArraySize), numHashFunctions(numHashFunctions),
          numElements(numElements), expectedElements(expectedElements),
          falsePositiveRate(falsePositiveRate), memoryUsage((bitArraySize + 7) / 8),
          fillRatio(0.0) {}
    
    void updateFillRatio(size_t setBits) {
        fillRatio = bitArraySize > 0 ? static_cast<double>(setBits) / bitArraySize : 0.0;
    }
    
    double getActualFalsePositiveRate() const {
        if (numElements == 0) return 0.0;
        
        // p = (1 - e^(-kn/m))^k
        double exponent = -static_cast<double>(numHashFunctions * numElements) / bitArraySize;
        return std::pow(1.0 - std::exp(exponent), numHashFunctions);
    }
    
    std::string toString() const {
        std::ostringstream oss;
        oss << "BloomFilterStats{size=" << bitArraySize
            << ", hashFunctions=" << numHashFunctions
            << ", elements=" << numElements << "/" << expectedElements
            << ", falsePositiveRate=" << std::fixed << std::setprecision(4) << falsePositiveRate
            << ", fillRatio=" << std::fixed << std::setprecision(4) << fillRatio
            << ", memory=" << memoryUsage << " bytes}";
        return oss.str();
    }
};

/**
 * Dynamic bit array implementation
 */
class DynamicBitArray {
private:
    std::vector<uint8_t> bits_;
    size_t size_;
    mutable std::mutex mutex_;

public:
    explicit DynamicBitArray(size_t size) : size_(size) {
        bits_.resize((size + 7) / 8, 0);
    }
    
    void setBit(size_t index) {
        if (index >= size_) return;
        
        std::lock_guard<std::mutex> lock(mutex_);
        size_t byteIndex = index / 8;
        size_t bitIndex = index % 8;
        bits_[byteIndex] |= (1 << bitIndex);
    }
    
    bool getBit(size_t index) const {
        if (index >= size_) return false;
        
        std::lock_guard<std::mutex> lock(mutex_);
        size_t byteIndex = index / 8;
        size_t bitIndex = index % 8;
        return (bits_[byteIndex] & (1 << bitIndex)) != 0;
    }
    
    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        std::fill(bits_.begin(), bits_.end(), 0);
    }
    
    size_t countSetBits() const {
        std::lock_guard<std::mutex> lock(mutex_);
        size_t count = 0;
        for (uint8_t byte : bits_) {
            count += __builtin_popcount(byte);
        }
        return count;
    }
    
    size_t getMemoryUsage() const {
        return bits_.size();
    }
    
    size_t size() const { return size_; }
};

/**
 * Main Bloom Filter implementation
 */
class BloomFilter {
private:
    std::unique_ptr<DynamicBitArray> bitArray_;
    size_t bitArraySize_;
    size_t numHashFunctions_;
    size_t expectedElements_;
    double falsePositiveRate_;
    std::atomic<size_t> numElements_;
    std::vector<uint32_t> hashSeeds_;

public:
    /**
     * Create Bloom filter with optimal parameters
     */
    BloomFilter(size_t expectedElements, double falsePositiveRate)
        : expectedElements_(expectedElements), falsePositiveRate_(falsePositiveRate),
          numElements_(0) {
        
        if (expectedElements == 0) {
            throw std::invalid_argument("Expected elements must be positive");
        }
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0) {
            throw std::invalid_argument("False positive rate must be between 0 and 1");
        }
        
        // Calculate optimal parameters
        bitArraySize_ = calculateBitArraySize(expectedElements, falsePositiveRate);
        numHashFunctions_ = calculateNumHashFunctions(bitArraySize_, expectedElements);
        
        // Initialize bit array and hash seeds
        bitArray_ = std::make_unique<DynamicBitArray>(bitArraySize_);
        hashSeeds_.resize(numHashFunctions_);
        for (size_t i = 0; i < numHashFunctions_; ++i) {
            hashSeeds_[i] = static_cast<uint32_t>(i);
        }
    }
    
    /**
     * Calculate optimal bit array size
     */
    static size_t calculateBitArraySize(size_t expectedElements, double falsePositiveRate) {
        // m = -(n * ln(p)) / (ln(2)^2)
        double size = -(static_cast<double>(expectedElements) * std::log(falsePositiveRate)) / 
                      (std::log(2.0) * std::log(2.0));
        return std::max(1UL, static_cast<size_t>(std::ceil(size)));
    }
    
    /**
     * Calculate optimal number of hash functions
     */
    static size_t calculateNumHashFunctions(size_t bitArraySize, size_t expectedElements) {
        // k = (m / n) * ln(2)
        double k = (static_cast<double>(bitArraySize) / expectedElements) * std::log(2.0);
        return std::max(1UL, static_cast<size_t>(std::round(k)));
    }
    
    /**
     * Get hash values for an element
     */
    std::vector<size_t> getHashValues(const std::string& element) const {
        std::vector<size_t> hashes;
        hashes.reserve(numHashFunctions_);
        
        for (size_t i = 0; i < numHashFunctions_; ++i) {
            uint32_t hashValue;
            
            switch (i % 5) {
                case 0:
                    hashValue = HashFunctions::murmurHash3(element, hashSeeds_[i]);
                    break;
                case 1:
                    hashValue = HashFunctions::fnvHash(element + std::to_string(hashSeeds_[i]));
                    break;
                case 2:
                    hashValue = HashFunctions::djb2Hash(element + std::to_string(hashSeeds_[i]));
                    break;
                case 3:
                    hashValue = HashFunctions::sdbmHash(element + std::to_string(hashSeeds_[i]));
                    break;
                default:
                    hashValue = HashFunctions::stdHash(element, hashSeeds_[i]);
                    break;
            }
            
            hashes.push_back(hashValue % bitArraySize_);
        }
        
        return hashes;
    }
    
    /**
     * Add an element to the Bloom filter
     */
    void add(const std::string& element) {
        auto hashValues = getHashValues(element);
        
        for (size_t hashValue : hashValues) {
            bitArray_->setBit(hashValue);
        }
        
        numElements_.fetch_add(1);
    }
    
    /**
     * Test if an element might be in the set
     */
    bool contains(const std::string& element) const {
        auto hashValues = getHashValues(element);
        
        for (size_t hashValue : hashValues) {
            if (!bitArray_->getBit(hashValue)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Clear all elements from the filter
     */
    void clear() {
        bitArray_->clear();
        numElements_.store(0);
    }
    
    /**
     * Get current statistics
     */
    BloomFilterStats getStats() const {
        BloomFilterStats stats(bitArraySize_, numHashFunctions_, numElements_.load(),
                              expectedElements_, falsePositiveRate_);
        stats.updateFillRatio(bitArray_->countSetBits());
        return stats;
    }
    
    /**
     * Get actual false positive rate
     */
    double getFalsePositiveRate() const {
        return getStats().getActualFalsePositiveRate();
    }
    
    /**
     * Get memory usage in bytes
     */
    size_t getMemoryUsage() const {
        return bitArray_->getMemoryUsage();
    }
    
    /**
     * Get number of elements added
     */
    size_t size() const {
        return numElements_.load();
    }
    
    // Getters
    size_t getBitArraySize() const { return bitArraySize_; }
    size_t getNumHashFunctions() const { return numHashFunctions_; }
    size_t getExpectedElements() const { return expectedElements_; }
};

/**
 * Builder pattern for creating Bloom filters
 */
class BloomFilterBuilder {
private:
    std::optional<size_t> expectedElements_;
    double falsePositiveRate_ = 0.01;

public:
    BloomFilterBuilder& withExpectedElements(size_t n) {
        expectedElements_ = n;
        return *this;
    }
    
    BloomFilterBuilder& withFalsePositiveRate(double rate) {
        falsePositiveRate_ = rate;
        return *this;
    }
    
    std::unique_ptr<BloomFilter> build() {
        if (!expectedElements_.has_value()) {
            throw std::runtime_error("Expected elements must be specified");
        }
        return std::make_unique<BloomFilter>(expectedElements_.value(), falsePositiveRate_);
    }
};

/**
 * Demonstration function
 */
void demo() {
    std::cout << "=== Bloom Filter Demo ===" << std::endl << std::endl;
    
    // Create Bloom filter for 10,000 elements with 1% false positive rate
    auto bf = std::make_unique<BloomFilter>(10000, 0.01);
    
    std::cout << "Created Bloom filter: " << bf->getStats().toString() << std::endl << std::endl;
    
    // Add some elements
    std::vector<std::string> websites = {
        "google.com", "facebook.com", "twitter.com", "github.com",
        "stackoverflow.com", "reddit.com", "youtube.com", "amazon.com",
        "netflix.com", "spotify.com"
    };
    
    std::cout << "Adding websites to filter..." << std::endl;
    for (const auto& website : websites) {
        bf->add(website);
        std::cout << "Added: " << website << std::endl;
    }
    
    std::cout << std::endl << "Filter stats after adding " << websites.size() << " elements:" << std::endl;
    std::cout << bf->getStats().toString() << std::endl;
    
    // Test membership
    std::cout << std::endl << "=== Membership Tests ===" << std::endl;
    
    // Test existing elements
    std::cout << "Testing existing elements:" << std::endl;
    for (size_t i = 0; i < std::min(5UL, websites.size()); ++i) {
        bool result = bf->contains(websites[i]);
        std::cout << "'" << websites[i] << "' in filter: " << std::boolalpha << result << std::endl;
    }
    
    // Test non-existing elements
    std::cout << std::endl << "Testing non-existing elements:" << std::endl;
    std::vector<std::string> testSites = {"nonexistent.com", "fake-site.org", "not-real.net"};
    for (const auto& site : testSites) {
        bool result = bf->contains(site);
        std::cout << "'" << site << "' in filter: " << std::boolalpha << result << std::endl;
    }
    
    // Performance comparison
    std::cout << std::endl << "=== Performance Comparison ===" << std::endl;
    
    std::cout << "Bloom filter memory usage: " << bf->getMemoryUsage() << " bytes" << std::endl;
    
    // Estimate regular set memory usage
    size_t setMemoryEstimate = 0;
    for (const auto& website : websites) {
        setMemoryEstimate += website.length() + sizeof(std::string) + 8; // rough estimate
    }
    
    std::cout << "Regular set memory usage: ~" << setMemoryEstimate << " bytes (estimate)" << std::endl;
    std::cout << "Memory savings: ~" << std::fixed << std::setprecision(1) 
              << static_cast<double>(setMemoryEstimate) / bf->getMemoryUsage() << "x" << std::endl;
    
    // Test false positive rate
    std::cout << std::endl << "=== False Positive Rate Test ===" << std::endl;
    int falsePositives = 0;
    int testCount = 1000;
    
    std::set<std::string> websiteSet(websites.begin(), websites.end());
    
    for (int i = 0; i < testCount; ++i) {
        std::string testElement = "test-element-" + std::to_string(i);
        if (websiteSet.find(testElement) == websiteSet.end() && bf->contains(testElement)) {
            falsePositives++;
        }
    }
    
    double actualFpRate = static_cast<double>(falsePositives) / testCount;
    double expectedFpRate = bf->getFalsePositiveRate();
    
    std::cout << "Expected false positive rate: " << std::fixed << std::setprecision(4) 
              << expectedFpRate << std::endl;
    std::cout << "Actual false positive rate: " << std::fixed << std::setprecision(4) 
              << actualFpRate << std::endl;
    std::cout << "False positives in " << testCount << " tests: " << falsePositives << std::endl;
    
    std::cout << std::endl << "Demo completed!" << std::endl;
}

int main() {
    try {
        demo();
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
    
    return 0;
}