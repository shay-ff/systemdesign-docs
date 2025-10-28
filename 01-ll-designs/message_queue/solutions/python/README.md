# Message Queue - Python Implementation

This directory contains a Python implementation of a simple in-memory message queue system.

## Features

- **Thread-safe operations** using locks and thread-safe collections
- **Multiple topics** with independent message queues
- **Producer-Consumer pattern** with multiple subscribers per topic
- **FIFO message ordering** within each topic
- **Asynchronous message delivery** using threading
- **Subscription management** with dynamic subscribe/unsubscribe
- **Statistics and monitoring** for topics and overall system
- **Bounded queues** with configurable maximum sizes
- **Error handling** for message processing failures

## Architecture

The implementation consists of several key classes:

- **Message**: Data structure representing a message with metadata
- **MessageHandler**: Abstract interface for processing messages
- **Consumer**: Subscribes to topics and processes messages
- **Topic**: Manages messages and subscribers for a specific topic
- **MessageQueue**: Central broker managing all topics and routing
- **Producer**: Publishes messages to topics

## Usage

### Basic Example

```python
from message_queue import MessageQueue, Producer, Consumer, MessageHandler

# Create a custom message handler
class MyHandler(MessageHandler):
    def handle_message(self, message):
        print(f"Processing: {message.payload}")

# Set up the system
mq = MessageQueue()
consumer = Consumer("my-consumer", MyHandler())
producer = Producer("my-producer", mq)

# Subscribe and publish
mq.subscribe(consumer, "events")
producer.publish("events", "Hello, World!")
```

### Advanced Features

```python
# Create topic with custom size limit
mq.create_topic("high-volume", max_size=5000)

# Publish with headers
producer.publish("events", "Important message", 
                 headers={"priority": "high", "source": "api"})

# Get topic statistics
stats = mq.get_topic_stats("events")
print(f"Messages processed: {stats['message_count']}")
```

## Running the Demo

```bash
python message_queue.py
```

This will run a demonstration showing:
- Multiple consumers subscribing to topics
- Message publishing and delivery
- Statistics reporting
- Dynamic subscription management

## Performance Characteristics

- **Time Complexity**: O(1) for publish, O(n) for delivery to n subscribers
- **Space Complexity**: O(m) where m is the total number of messages across all topics
- **Concurrency**: Thread-safe with minimal lock contention
- **Throughput**: Suitable for thousands of messages per second

## Thread Safety

The implementation uses:
- `threading.RLock()` for protecting shared data structures
- Thread-safe collections (`deque`, `list`, `set`)
- Separate threads for message delivery to avoid blocking
- Proper cleanup of inactive consumers

## Limitations

- **In-memory only**: Messages are not persisted to disk
- **No delivery guarantees**: Messages may be lost if consumers fail
- **No message acknowledgment**: Fire-and-forget delivery model
- **Single process**: Cannot distribute across multiple machines

## Extensions

Possible enhancements for production use:
- Persistent storage with database or file system
- Message acknowledgment and retry mechanisms
- Dead letter queues for failed messages
- Metrics and monitoring integration
- Network protocol support (HTTP, TCP, WebSocket)
- Message filtering and routing rules