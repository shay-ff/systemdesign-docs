# Message Queue - Go Implementation

This directory contains a Go implementation of a simple in-memory message queue system using channels, goroutines, and Go's concurrency primitives.

## Features

- **Goroutine-based concurrency** using channels and goroutines for message handling
- **Multiple topics** with independent message channels
- **Producer-Consumer pattern** with multiple subscribers per topic
- **FIFO message ordering** within each topic using buffered channels
- **Asynchronous message delivery** using goroutines
- **Subscription management** with dynamic subscribe/unsubscribe
- **Statistics and monitoring** for topics and overall system
- **Bounded queues** with configurable maximum sizes using buffered channels
- **Graceful shutdown** with context cancellation
- **Error handling** with panic recovery and error logging

## Architecture

The implementation consists of several key types:

- **Message**: Struct representing a message with metadata
- **MessageHandler**: Interface for processing messages
- **Consumer**: Subscribes to topics and processes messages
- **Topic**: Manages messages and subscribers for a specific topic
- **MessageQueue**: Central broker managing all topics and routing
- **Producer**: Publishes messages to topics

## Compilation and Execution

```bash
# Run directly
go run message_queue.go

# Build executable
go build -o message_queue message_queue.go

# Run the executable
./message_queue
```

### Go Version Requirements

- **Go 1.13 or later** for module support and context features
- **Standard library only** - no external dependencies

## Usage

### Basic Example

```go
package main

import (
    "fmt"
    "time"
)

// Create a custom message handler
type MyHandler struct {
    name string
}

func (h *MyHandler) HandleMessage(message *Message) error {
    fmt.Printf("[%s] Processing: %s\n", h.name, message.Payload)
    return nil
}

func main() {
    // Set up the system
    mq := NewMessageQueue()
    defer mq.Close()
    
    consumer := NewConsumer("my-consumer", &MyHandler{name: "my-handler"})
    producer := NewProducer("my-producer", mq)
    
    // Subscribe and publish
    mq.Subscribe(consumer, "events")
    producer.Publish("events", "Hello, World!", nil)
    
    time.Sleep(100 * time.Millisecond) // Wait for processing
}
```

### Advanced Features

```go
// Create topic with custom size limit
mq.CreateTopic("high-volume", 5000)

// Publish with headers
headers := map[string]string{
    "priority": "high",
    "source":   "api",
}
producer.Publish("events", "Important message", headers)

// Get topic statistics
stats := mq.GetTopicStats("events")
if stats != nil {
    fmt.Printf("Messages processed: %d\n", stats.MessageCount)
}
```

## Performance Characteristics

- **Time Complexity**: O(1) for publish, O(n) for delivery to n subscribers
- **Space Complexity**: O(m) where m is the total number of messages across all topics
- **Concurrency**: Highly concurrent using goroutines and channels
- **Throughput**: Excellent performance due to Go's efficient goroutine scheduler

## Concurrency Model

The implementation leverages Go's concurrency features:
- **Channels**: Used for message queues and communication between goroutines
- **Goroutines**: Lightweight threads for asynchronous message processing
- **Mutexes**: `sync.RWMutex` for protecting shared data structures
- **Atomic operations**: `sync/atomic` for counters and flags
- **Context**: For graceful shutdown and cancellation

## Key Design Decisions

1. **Channel-based queues**: Uses buffered channels as message queues for natural backpressure
2. **Goroutine per message**: Each message delivery spawns a goroutine for maximum concurrency
3. **Interface-based handlers**: Clean abstraction for message processing
4. **Atomic counters**: Lock-free counters for performance metrics
5. **Graceful shutdown**: Context-based cancellation for clean resource cleanup

## Error Handling

- **Panic recovery**: Message handlers are protected with recover() to prevent crashes
- **Error logging**: Comprehensive error logging for debugging
- **Graceful degradation**: Inactive consumers are automatically removed
- **Resource cleanup**: Proper cleanup of channels and goroutines

## Memory Management

- **Garbage collection**: Automatic memory management by Go runtime
- **Bounded channels**: Prevents unbounded memory growth
- **Resource cleanup**: Proper cleanup in Close() methods
- **Leak prevention**: Context cancellation prevents goroutine leaks

## Limitations

- **In-memory only**: Messages are not persisted to disk
- **No delivery guarantees**: Messages may be lost if consumers fail
- **No message acknowledgment**: Fire-and-forget delivery model
- **Single process**: Cannot distribute across multiple processes without additional networking

## Extensions

Possible enhancements for production use:
- Persistent storage with database or file system
- Message acknowledgment and retry mechanisms
- Dead letter queues for failed messages
- Metrics integration (Prometheus, etc.)
- Network protocol support (gRPC, HTTP, WebSocket)
- Message filtering and routing rules
- Clustering support for distributed deployment

## Go-Specific Features

This implementation showcases several Go idioms and patterns:
- **Interface satisfaction**: Implicit interface implementation
- **Embedding**: Struct composition for code reuse
- **Function types**: `MessageHandlerFunc` adapter pattern
- **Zero values**: Proper initialization with meaningful zero values
- **Defer statements**: Resource cleanup with defer
- **Select statements**: Non-blocking channel operations

## Testing

```bash
# Run tests (if test files exist)
go test -v

# Run with race detection
go run -race message_queue.go

# Benchmark performance
go test -bench=.
```

## Dependencies

This implementation uses only Go standard library packages:
- `context` for cancellation and timeouts
- `sync` and `sync/atomic` for concurrency primitives
- `time` for timestamps and delays
- `fmt` and `log` for output and logging
- `math/rand` for ID generation

## Build Tags and Configuration

```bash
# Build with debug information
go build -tags debug message_queue.go

# Build for different platforms
GOOS=linux GOARCH=amd64 go build message_queue.go
GOOS=windows GOARCH=amd64 go build message_queue.go
```