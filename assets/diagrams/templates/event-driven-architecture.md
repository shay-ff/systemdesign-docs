# Event-Driven Architecture Template

## Mermaid Template

```mermaid
graph TB
    P1[⚙️ Producer 1] --> ES[📢 Event Stream]
    P2[⚙️ Producer 2] --> ES
    P3[⚙️ Producer 3] --> ES
    
    ES --> C1[⚙️ Consumer 1]
    ES --> C2[⚙️ Consumer 2]
    ES --> C3[⚙️ Consumer 3]
    
    C1 --> D1[(🗄️ Database 1)]
    C2 --> D2[(🗄️ Database 2)]
    C3 --> D3[(🗄️ Database 3)]
    
    ES --> DLQ[❌ Dead Letter Queue]
    
    style P1 fill:#2563eb,color:#fff
    style P2 fill:#2563eb,color:#fff
    style P3 fill:#2563eb,color:#fff
    style ES fill:#ea580c,color:#fff
    style C1 fill:#059669,color:#fff
    style C2 fill:#059669,color:#fff
    style C3 fill:#059669,color:#fff
    style D1 fill:#6b7280,color:#fff
    style D2 fill:#6b7280,color:#fff
    style D3 fill:#6b7280,color:#fff
    style DLQ fill:#ef4444,color:#fff
```

## PlantUML Template

```plantuml
@startuml
!define PRODUCER_COLOR #2563eb
!define CONSUMER_COLOR #059669
!define STREAM_COLOR #ea580c
!define DATABASE_COLOR #6b7280
!define ERROR_COLOR #ef4444

component "Producer 1" as P1 <<PRODUCER_COLOR>>
component "Producer 2" as P2 <<PRODUCER_COLOR>>
component "Producer 3" as P3 <<PRODUCER_COLOR>>

component "Event Stream\n(Kafka/Kinesis)" as ES <<STREAM_COLOR>>

component "Consumer 1" as C1 <<CONSUMER_COLOR>>
component "Consumer 2" as C2 <<CONSUMER_COLOR>>
component "Consumer 3" as C3 <<CONSUMER_COLOR>>

database "Database 1" as D1 <<DATABASE_COLOR>>
database "Database 2" as D2 <<DATABASE_COLOR>>
database "Database 3" as D3 <<DATABASE_COLOR>>

component "Dead Letter Queue" as DLQ <<ERROR_COLOR>>

P1 --> ES : Publish Event
P2 --> ES : Publish Event
P3 --> ES : Publish Event

ES --> C1 : Subscribe
ES --> C2 : Subscribe
ES --> C3 : Subscribe

C1 --> D1 : Process & Store
C2 --> D2 : Process & Store
C3 --> D3 : Process & Store

ES --> DLQ : Failed Events

@enduml
```

## Event Flow Sequence

```mermaid
sequenceDiagram
    participant P as Producer
    participant ES as Event Stream
    participant C1 as Consumer 1
    participant C2 as Consumer 2
    participant DLQ as Dead Letter Queue
    
    P->>ES: Publish Event
    ES->>C1: Deliver Event
    ES->>C2: Deliver Event
    
    C1->>C1: Process Successfully
    C2->>C2: Processing Fails
    
    C2->>DLQ: Send Failed Event
    
    Note over ES: Event persisted for replay
    Note over DLQ: Failed events for analysis
```

## Key Components

### Event Stream (Kafka/Kinesis)
- **Durability**: Events persisted for replay
- **Scalability**: Horizontal partitioning
- **Ordering**: Per-partition ordering guarantees

### Producers
- **Async Publishing**: Non-blocking event emission
- **Batching**: Efficient throughput
- **Schema Evolution**: Backward/forward compatibility

### Consumers
- **At-least-once**: Delivery guarantees
- **Idempotency**: Handle duplicate events
- **Error Handling**: Dead letter queue for failures

## Benefits

- **Loose Coupling**: Producers and consumers are independent
- **Scalability**: Add consumers without affecting producers
- **Resilience**: Failed consumers don't block others
- **Audit Trail**: Complete event history

## Considerations

- **Eventual Consistency**: Data may be temporarily inconsistent
- **Complexity**: More complex than synchronous systems
- **Debugging**: Harder to trace event flows
- **Schema Management**: Event schema evolution challenges