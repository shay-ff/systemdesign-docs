# Message Queue - C++ Implementation

This directory contains a C++ implementation of a simple in-memory message queue system using modern C++ features and thread-safe operations.

## Features

- **Thread-safe operations** using `std::mutex`, `std::atomic`, and proper locking
- **Multiple topics** with independent message queues
- **Producer-Consumer pattern** with multiple subscribers per topic
- **FIFO message ordering** within each topic using `std::queue`
- **Asynchronous message delivery** using `std::async`
- **Subscription management** with dynamic subscribe/unsubscribe
- **Statistics and monitoring** for topics and overall system
- **Bounded queues** with configurable maximum sizes
- **Error handling** for message processing failures
- **Modern C++ features** (C++17 and later)

## Architecture

The implementation consists of several key classes:

- **Message**: Struct representing a message with metadata
- **MessageHandler**: Abstract interface for processing messages
- **Consumer**: Subscribes to topics and processes messages
- **Topic**: Manages messages and subscribers for a specific topic
- **MessageQueue**: Central broker managing all topics and routing
- **Producer**: Publishes messages to topics

## Compilation and Execution

```bash
# Compile with C++17 support
g++ -std=c++17 -pthread -O2 message_queue.cpp -o message_queue

# Run the demonstration
./message_queue
```

### Compiler Requirements

- **C++17 or later** for `std::optional` and other modern features
- **pthread support** for threading capabilities
- **Recommended compilers**: GCC 7+, Clang 5+, MSVC 2017+

## Usage

### Basic Example

```cpp
#include "message_queue.cpp"

// Create a custom message handler
class MyHandler : public MessageHandler {
public:
    void handleMessage(const Message& message) override {
        std::cout << "Processing: " << message.payload << std::endl;
    }
};

// Set up the system
auto mq = std::make_shared<MessageQueue>();
auto consumer = std::make_shared<Consumer>("my-consumer", 
    std::make_unique<MyHandler>());
Producer producer("my-producer", mq);

// Subscribe and publish
mq->subscribe(consumer, "events");
producer.publish("events", "Hello, World!");
```

### Advanced Features

```cpp
// Create topic with custom size limit
mq->createTopic("high-volume", 5000);

// Publish with headers
std::unordered_map<std::string, std::string> headers = {
    {"priority", "high"},
    {"source", "api"}
};
producer.publish("events", "Important message", headers);

// Get topic statistics
auto stats = mq->getTopicStats("events");
if (stats) {
    std::cout << "Messages processed: " << stats->messageCount << std::endl;
}
```

## Performance Characteristics

- **Time Complexity**: O(1) for publish, O(n) for delivery to n subscribers
- **Space Complexity**: O(m) where m is the total number of messages across all topics
- **Concurrency**: Thread-safe with minimal lock contention using fine-grained locking
- **Throughput**: Suitable for thousands of messages per second

## Thread Safety

The implementation uses:
- `std::mutex` for protecting shared data structures
- `std::atomic` for counters and flags
- `std::shared_ptr` for safe memory management across threads
- `std::async` for asynchronous message delivery
- Fine-grained locking to minimize contention

## Key Design Decisions

1. **Smart Pointers**: Uses `std::shared_ptr` and `std::unique_ptr` for automatic memory management
2. **RAII**: Proper resource management with constructors and destructors
3. **Move Semantics**: Efficient transfer of ownership where possible
4. **Exception Safety**: Proper exception handling in message processing
5. **Modern C++**: Leverages C++17 features like `std::optional`

## Memory Management

- **Automatic cleanup**: Smart pointers handle memory deallocation
- **No memory leaks**: RAII ensures proper resource cleanup
- **Thread-safe sharing**: `std::shared_ptr` allows safe sharing across threads
- **Bounded memory**: Configurable queue sizes prevent unbounded growth

## Limitations

- **In-memory only**: Messages are not persisted to disk
- **No delivery guarantees**: Messages may be lost if consumers fail
- **No message acknowledgment**: Fire-and-forget delivery model
- **Single process**: Cannot distribute across multiple processes without additional IPC

## Extensions

Possible enhancements for production use:
- Persistent storage with file system or database
- Message acknowledgment and retry mechanisms
- Dead letter queues for failed messages
- Metrics and monitoring integration
- Network protocol support (TCP, HTTP, WebSocket)
- Message filtering and routing rules
- Serialization support for complex message types

## Dependencies

This implementation uses only standard C++ libraries:
- `<thread>`, `<mutex>`, `<atomic>` for concurrency
- `<memory>` for smart pointers
- `<queue>`, `<vector>`, `<unordered_map>` for containers
- `<chrono>` for timestamps
- `<future>` for asynchronous operations

## Build Options

```bash
# Debug build with symbols
g++ -std=c++17 -pthread -g -DDEBUG message_queue.cpp -o message_queue_debug

# Release build with optimizations
g++ -std=c++17 -pthread -O3 -DNDEBUG message_queue.cpp -o message_queue_release

# With additional warnings
g++ -std=c++17 -pthread -Wall -Wextra -Wpedantic message_queue.cpp -o message_queue
```