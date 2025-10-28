# Message Queue - Java Implementation

This directory contains a Java implementation of a simple in-memory message queue system using concurrent collections and thread-safe operations.

## Features

- **Thread-safe operations** using `ConcurrentHashMap`, `BlockingQueue`, and synchronization
- **Multiple topics** with independent message queues
- **Producer-Consumer pattern** with multiple subscribers per topic
- **FIFO message ordering** within each topic using `ArrayBlockingQueue`
- **Asynchronous message delivery** using `CompletableFuture`
- **Subscription management** with dynamic subscribe/unsubscribe
- **Statistics and monitoring** for topics and overall system
- **Bounded queues** with configurable maximum sizes
- **Error handling** for message processing failures

## Architecture

The implementation consists of several key classes:

- **Message**: Immutable data structure representing a message with metadata
- **MessageHandler**: Interface for processing messages
- **Consumer**: Subscribes to topics and processes messages
- **Topic**: Manages messages and subscribers for a specific topic
- **MessageQueue**: Central broker managing all topics and routing
- **Producer**: Publishes messages to topics

## Compilation and Execution

```bash
# Compile all Java files
javac *.java

# Run the demonstration
java MessageQueueDemo
```

## Usage

### Basic Example

```java
// Create a custom message handler
MessageHandler handler = new MessageHandler() {
    @Override
    public void handleMessage(Message message) {
        System.out.println("Processing: " + message.getPayload());
    }
};

// Set up the system
MessageQueue mq = new MessageQueue();
Consumer consumer = new Consumer("my-consumer", handler);
Producer producer = new Producer("my-producer", mq);

// Subscribe and publish
mq.subscribe(consumer, "events");
producer.publish("events", "Hello, World!");
```

### Advanced Features

```java
// Create topic with custom size limit
mq.createTopic("high-volume", 5000);

// Publish with headers
Map<String, String> headers = new HashMap<>();
headers.put("priority", "high");
headers.put("source", "api");
producer.publish("events", "Important message", headers);

// Get topic statistics
Map<String, Object> stats = mq.getTopicStats("events");
System.out.println("Messages processed: " + stats.get("messageCount"));
```

## Performance Characteristics

- **Time Complexity**: O(1) for publish, O(n) for delivery to n subscribers
- **Space Complexity**: O(m) where m is the total number of messages across all topics
- **Concurrency**: Thread-safe with minimal lock contention using concurrent collections
- **Throughput**: Suitable for thousands of messages per second

## Thread Safety

The implementation uses:
- `ConcurrentHashMap` for topic storage
- `ArrayBlockingQueue` for thread-safe message queues
- `synchronized` blocks for protecting subscriber lists
- `CompletableFuture` for asynchronous message delivery
- Atomic operations for counters

## Key Design Decisions

1. **Immutable Messages**: Messages are immutable to prevent concurrent modification issues
2. **Bounded Queues**: Uses `ArrayBlockingQueue` with configurable limits to prevent memory issues
3. **Asynchronous Delivery**: Messages are delivered to consumers asynchronously to avoid blocking producers
4. **Fail-Fast**: Inactive consumers are automatically removed from subscriber lists
5. **Copy-on-Read**: Defensive copying of collections when returning to clients

## Limitations

- **In-memory only**: Messages are not persisted to disk
- **No delivery guarantees**: Messages may be lost if consumers fail
- **No message acknowledgment**: Fire-and-forget delivery model
- **Single JVM**: Cannot distribute across multiple JVMs without additional networking

## Extensions

Possible enhancements for production use:
- Persistent storage with database or file system
- Message acknowledgment and retry mechanisms
- Dead letter queues for failed messages
- JMX metrics and monitoring integration
- Network protocol support (HTTP, TCP, WebSocket)
- Message filtering and routing rules
- Clustering support for distributed deployment

## Dependencies

This implementation uses only standard Java libraries:
- `java.util.concurrent.*` for thread-safe collections and async operations
- `java.util.*` for basic collections and utilities
- `java.time.*` for timestamp handling