# Low-Level Design — Hands-On Component Implementations

> 🧭 **Navigation**: [← Foundations](../00-foundations/) | [📍 Full Navigation](../NAVIGATION.md) | [Next: High-Level Designs →](../02-hl-designs/)

Build your understanding through hands-on implementation of core system components. Each design includes multi-language solutions, architectural diagrams, and detailed explanations.

## 🏗️ Available Designs

### Core Components
- **[LRU Cache](lru_cache/)** — Least Recently Used cache with O(1) operations
- **[Rate Limiter](rate_limiter/)** — Token bucket and sliding window algorithms  
- **[Consistent Hashing](consistent_hashing/)** — Distributed hash ring with virtual nodes
- **[Message Queue](message_queue/)** — Producer-consumer pattern with multiple subscribers
- **[Bloom Filter](bloom_filter/)** — Probabilistic data structure for membership testing

### 🌐 Multi-Language Support

Each design includes implementations in:
- **Python** — Clean, readable implementations perfect for interviews
- **Java** — Enterprise-grade solutions with proper OOP design
- **Go** — Concurrent, performant implementations
- **C++** — High-performance solutions with memory optimization

## 📁 Structure

Each folder contains:
- **`README.md`** — Problem definition, requirements, and overview
- **`design.puml`** — PlantUML architecture and sequence diagrams
- **`explanation.md`** — Design decisions, trade-offs, and complexity analysis
- **`solutions/`** — Multi-language implementations
  - `python/` — Python implementation with tests
  - `java/` — Java implementation with proper packaging
  - `go/` — Go implementation with modules
  - `cpp/` — C++ implementation with modern standards

## 🚀 Quick Start

### Choose Your Language

**Python** (Great for interviews):
```bash
cd lru_cache/solutions/python
python lru_cache.py
```

**Java** (Enterprise focus):
```bash
cd lru_cache/solutions/java
javac LRUCache.java && java LRUCache
```

**Go** (Concurrency focus):
```bash
cd lru_cache/solutions/go
go run lru_cache.go
```

**C++** (Performance focus):
```bash
cd lru_cache/solutions/cpp
g++ -std=c++17 lru_cache.cpp -o lru && ./lru
```

### Recommended Learning Order

1. **[LRU Cache](lru_cache/)** — Start here for fundamental data structure design
2. **[Rate Limiter](rate_limiter/)** — Learn about system protection and algorithms
3. **[Consistent Hashing](consistent_hashing/)** — Understand distributed system concepts
4. **[Message Queue](message_queue/)** — Explore asynchronous communication patterns
5. **[Bloom Filter](bloom_filter/)** — Dive into probabilistic data structures

## 📊 Visual Learning

All designs include:
- **Architecture Diagrams** — System component relationships
- **Sequence Diagrams** — Operation flow and interactions
- **Complexity Analysis** — Time and space trade-offs
- **Visual Examples** — Step-by-step operation illustrations

## 🔧 Build & Run

### Prerequisites
- **Python 3.8+** for Python examples
- **Java 11+** for Java examples  
- **Go 1.19+** for Go examples
- **GCC/Clang** with C++17 support for C++ examples

### Diagrams
View PlantUML diagrams:
```bash
# Install PlantUML
npm install -g @plantuml/plantuml

# Generate diagrams
plantuml 01-ll-designs/*/design.puml
```

Or use online viewers like [PlantText](https://www.planttext.com/)

## 💡 Learning Tips

- **Start Simple**: Begin with LRU Cache to understand the pattern
- **Compare Languages**: See how the same concept is implemented differently
- **Trace Operations**: Walk through the sequence diagrams step by step
- **Modify Code**: Try changing parameters and see the effects
- **Time Yourself**: Practice implementing from scratch for interview prep

## 🎯 Next Steps

After mastering low-level designs:

- **Production Systems**: Try [runnable implementations](../03-implementations/) with Docker
- **System Architecture**: Explore [high-level designs](../02-hl-designs/) like Twitter and Uber
- **Interview Practice**: Use these as building blocks in [interview prep](../04-interview-prep/)
- **Advanced Topics**: Check the [study plan](../05-study-plan/) for structured progression

## 🔗 Related Resources

- **Foundations**: Review [core concepts](../00-foundations/concepts.md) if you need background
- **Cheatsheet**: Use [formulas and calculations](../00-foundations/cheatsheet.md) for complexity analysis
- **Patterns**: See [design patterns](../00-foundations/design-patterns.md) for architectural guidance

---

*💻 **Pro Tip**: Try implementing each design in your preferred language first, then compare with the provided solutions to learn different approaches!*
