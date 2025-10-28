# Message Queue

## Problem Statement

Design and implement a simple in-memory message queue system that supports producer-consumer patterns with multiple subscribers. The system should handle message publishing, subscription management, and reliable message delivery.

## Requirements

### Functional Requirements
1. **Message Publishing**: Producers can publish messages to named topics
2. **Topic Subscription**: Consumers can subscribe to specific topics
3. **Message Delivery**: Messages are delivered to all active subscribers of a topic
4. **Multiple Subscribers**: Support multiple consumers per topic
5. **Message Ordering**: Messages within a topic maintain FIFO order
6. **Topic Management**: Dynamic creation and management of topics

### Non-Functional Requirements
1. **Performance**: Handle thousands of messages per second
2. **Memory Efficiency**: Bounded memory usage with configurable limits
3. **Thread Safety**: Support concurrent producers and consumers
4. **Reliability**: Ensure message delivery to active subscribers
5. **Scalability**: Support multiple topics and subscribers

## Key Components

1. **Message**: Basic unit of data with payload and metadata
2. **Topic**: Named channel for message routing
3. **Producer**: Client that publishes messages to topics
4. **Consumer**: Client that subscribes to and receives messages from topics
5. **Message Queue**: Central broker managing topics and message routing
6. **Subscription**: Relationship between consumer and topic

## Use Cases

- **Event-Driven Architecture**: Decoupling services through asynchronous messaging
- **Task Distribution**: Distributing work items across multiple workers
- **Notification Systems**: Broadcasting updates to multiple subscribers
- **Log Aggregation**: Collecting logs from multiple sources
- **Real-time Updates**: Pushing updates to connected clients

## Design Considerations

- **Message Persistence**: In-memory storage with optional durability
- **Delivery Guarantees**: At-least-once delivery to active subscribers
- **Flow Control**: Backpressure handling when consumers are slow
- **Error Handling**: Dead letter queues for failed message processing
- **Monitoring**: Metrics for queue depth, throughput, and consumer lag

## Implementation Languages

This design is implemented in multiple programming languages:

- **Python**: `solutions/python/` - Using threading and queues
- **Java**: `solutions/java/` - Using concurrent collections and executors
- **C++**: `solutions/cpp/` - Using STL containers and threading
- **Go**: `solutions/go/` - Using channels and goroutines

Each implementation demonstrates language-specific patterns and best practices for concurrent programming and message handling.