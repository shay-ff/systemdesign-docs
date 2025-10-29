# Database Patterns

## Database Sharding Patterns

### Horizontal Sharding (Range-Based)

```mermaid
graph TB
    App[Application] --> Router[Shard Router]
    
    Router --> S1[Shard 1<br/>Users 1-1000]
    Router --> S2[Shard 2<br/>Users 1001-2000]
    Router --> S3[Shard 3<br/>Users 2001-3000]
    
    S1 --> DB1[(Database 1)]
    S2 --> DB2[(Database 2)]
    S3 --> DB3[(Database 3)]
    
    style App fill:#2563eb,color:#fff
    style Router fill:#7c3aed,color:#fff
    style S1 fill:#059669,color:#fff
    style S2 fill:#059669,color:#fff
    style S3 fill:#059669,color:#fff
    style DB1 fill:#6b7280,color:#fff
    style DB2 fill:#6b7280,color:#fff
    style DB3 fill:#6b7280,color:#fff
```

### Hash-Based Sharding

```mermaid
graph TB
    App[Application] --> Hash[Hash Function<br/>hash(user_id) % 3]
    
    Hash --> S1[Shard 1<br/>Hash = 0]
    Hash --> S2[Shard 2<br/>Hash = 1]
    Hash --> S3[Shard 3<br/>Hash = 2]
    
    S1 --> DB1[(Database 1)]
    S2 --> DB2[(Database 2)]
    S3 --> DB3[(Database 3)]
    
    style App fill:#2563eb,color:#fff
    style Hash fill:#7c3aed,color:#fff
    style S1 fill:#059669,color:#fff
    style S2 fill:#059669,color:#fff
    style S3 fill:#059669,color:#fff
    style DB1 fill:#6b7280,color:#fff
    style DB2 fill:#6b7280,color:#fff
    style DB3 fill:#6b7280,color:#fff
```

## Replication Patterns

### Master-Slave Replication

```mermaid
graph TB
    App[Application] --> Master[(Master DB<br/>Read/Write)]
    
    Master --> Slave1[(Slave DB 1<br/>Read Only)]
    Master --> Slave2[(Slave DB 2<br/>Read Only)]
    Master --> Slave3[(Slave DB 3<br/>Read Only)]
    
    App --> Slave1
    App --> Slave2
    App --> Slave3
    
    style App fill:#2563eb,color:#fff
    style Master fill:#059669,color:#fff
    style Slave1 fill:#3b82f6,color:#fff
    style Slave2 fill:#3b82f6,color:#fff
    style Slave3 fill:#3b82f6,color:#fff
```

### Master-Master Replication

```mermaid
graph TB
    App1[App Instance 1] --> Master1[(Master DB 1<br/>Read/Write)]
    App2[App Instance 2] --> Master2[(Master DB 2<br/>Read/Write)]
    
    Master1 <--> Master2
    
    style App1 fill:#2563eb,color:#fff
    style App2 fill:#2563eb,color:#fff
    style Master1 fill:#059669,color:#fff
    style Master2 fill:#059669,color:#fff
```

## CQRS (Command Query Responsibility Segregation)

```mermaid
graph TB
    App[Application] --> CmdHandler[Command Handler]
    App --> QueryHandler[Query Handler]
    
    CmdHandler --> WriteDB[(Write Database<br/>Normalized)]
    QueryHandler --> ReadDB[(Read Database<br/>Denormalized)]
    
    WriteDB --> Sync[Data Sync]
    Sync --> ReadDB
    
    style App fill:#2563eb,color:#fff
    style CmdHandler fill:#dc2626,color:#fff
    style QueryHandler fill:#059669,color:#fff
    style WriteDB fill:#6b7280,color:#fff
    style ReadDB fill:#3b82f6,color:#fff
    style Sync fill:#ea580c,color:#fff
```

## Database Per Service Pattern

```mermaid
graph TB
    subgraph "User Service"
        US[User Service] --> UD[(User Database)]
    end
    
    subgraph "Order Service"
        OS[Order Service] --> OD[(Order Database)]
    end
    
    subgraph "Payment Service"
        PS[Payment Service] --> PD[(Payment Database)]
    end
    
    US -.-> MQ[Message Queue]
    OS -.-> MQ
    PS -.-> MQ
    
    style US fill:#2563eb,color:#fff
    style OS fill:#2563eb,color:#fff
    style PS fill:#2563eb,color:#fff
    style UD fill:#059669,color:#fff
    style OD fill:#059669,color:#fff
    style PD fill:#059669,color:#fff
    style MQ fill:#ea580c,color:#fff
```

## Saga Pattern (Distributed Transactions)

### Choreography-Based Saga

```mermaid
sequenceDiagram
    participant OS as Order Service
    participant PS as Payment Service
    participant IS as Inventory Service
    participant SS as Shipping Service
    
    OS->>OS: Create Order
    OS->>PS: Payment Request Event
    PS->>PS: Process Payment
    PS->>IS: Payment Success Event
    IS->>IS: Reserve Inventory
    IS->>SS: Inventory Reserved Event
    SS->>SS: Schedule Shipping
    SS->>OS: Shipping Scheduled Event
    OS->>OS: Complete Order
```

### Orchestration-Based Saga

```mermaid
sequenceDiagram
    participant Client as Client
    participant Orch as Saga Orchestrator
    participant PS as Payment Service
    participant IS as Inventory Service
    participant SS as Shipping Service
    
    Client->>Orch: Create Order
    Orch->>PS: Process Payment
    PS-->>Orch: Payment Success
    Orch->>IS: Reserve Inventory
    IS-->>Orch: Inventory Reserved
    Orch->>SS: Schedule Shipping
    SS-->>Orch: Shipping Scheduled
    Orch-->>Client: Order Complete
```

## Polyglot Persistence

```mermaid
graph TB
    App[Application Layer]
    
    App --> UserDB[(PostgreSQL<br/>User Data)]
    App --> SessionDB[(Redis<br/>Session Store)]
    App --> ProductDB[(MongoDB<br/>Product Catalog)]
    App --> AnalyticsDB[(ClickHouse<br/>Analytics)]
    App --> SearchDB[(Elasticsearch<br/>Search Index)]
    
    style App fill:#2563eb,color:#fff
    style UserDB fill:#059669,color:#fff
    style SessionDB fill:#dc2626,color:#fff
    style ProductDB fill:#10b981,color:#fff
    style AnalyticsDB fill:#f59e0b,color:#fff
    style SearchDB fill:#7c3aed,color:#fff
```

## Database Scaling Strategies

### Read Replicas

```mermaid
graph TB
    LB[Load Balancer]
    
    LB --> App1[App 1]
    LB --> App2[App 2]
    LB --> App3[App 3]
    
    App1 --> Master[(Master<br/>Writes)]
    App2 --> Master
    App3 --> Master
    
    App1 --> ReadLB[Read Load Balancer]
    App2 --> ReadLB
    App3 --> ReadLB
    
    ReadLB --> Replica1[(Read Replica 1)]
    ReadLB --> Replica2[(Read Replica 2)]
    ReadLB --> Replica3[(Read Replica 3)]
    
    Master --> Replica1
    Master --> Replica2
    Master --> Replica3
    
    style LB fill:#7c3aed,color:#fff
    style ReadLB fill:#7c3aed,color:#fff
    style App1 fill:#2563eb,color:#fff
    style App2 fill:#2563eb,color:#fff
    style App3 fill:#2563eb,color:#fff
    style Master fill:#dc2626,color:#fff
    style Replica1 fill:#3b82f6,color:#fff
    style Replica2 fill:#3b82f6,color:#fff
    style Replica3 fill:#3b82f6,color:#fff
```