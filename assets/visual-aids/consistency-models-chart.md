# Consistency Models Comparison Chart

## Consistency Spectrum

```mermaid
graph LR
    Strong[Strong Consistency<br/>🔒<br/>Immediate] --> Sequential[Sequential Consistency<br/>📋<br/>Ordered]
    Sequential --> Causal[Causal Consistency<br/>🔗<br/>Cause-Effect]
    Causal --> Eventual[Eventual Consistency<br/>⏰<br/>Eventually]
    Eventual --> Weak[Weak Consistency<br/>🤷<br/>No Guarantees]
    
    style Strong fill:#dc2626,color:#fff
    style Sequential fill:#ea580c,color:#fff
    style Causal fill:#f59e0b,color:#fff
    style Eventual fill:#10b981,color:#fff
    style Weak fill:#6b7280,color:#fff
```

## Detailed Comparison

### Strong Consistency
**"All nodes see the same data at the same time"**

```mermaid
sequenceDiagram
    participant C as Client
    participant N1 as Node 1
    participant N2 as Node 2
    participant N3 as Node 3
    
    C->>N1: Write(x=5)
    N1->>N2: Replicate(x=5)
    N1->>N3: Replicate(x=5)
    N2-->>N1: ACK
    N3-->>N1: ACK
    N1-->>C: Success
    
    Note over N1,N3: All nodes have x=5 before client gets success
```

**Examples**: Banking systems, inventory management
**Trade-offs**: High latency, lower availability
**Use when**: Data accuracy is critical

### Sequential Consistency
**"All operations appear in some sequential order"**

```mermaid
sequenceDiagram
    participant C1 as Client 1
    participant C2 as Client 2
    participant S as System
    
    C1->>S: Write(x=1)
    C2->>S: Write(x=2)
    S->>S: Order: x=1, then x=2
    
    Note over S: All clients see same order: x=1 → x=2
```

**Examples**: Distributed databases with ordering
**Trade-offs**: Better performance than strong, still some coordination
**Use when**: Order matters more than immediate consistency

### Causal Consistency
**"Causally related operations are seen in order"**

```mermaid
graph TB
    A[Alice posts: "Going to lunch"] --> B[Bob replies: "Save me a seat"]
    B --> C[Charlie sees both in order]
    
    D[Dave posts: "Nice weather"] 
    
    style A fill:#2563eb,color:#fff
    style B fill:#059669,color:#fff
    style C fill:#7c3aed,color:#fff
    style D fill:#f59e0b,color:#fff
    
    Note1[Causally related:<br/>Must see in order]
    Note2[Independent:<br/>Can see in any order]
```

**Examples**: Social media feeds, collaborative editing
**Trade-offs**: Good performance, maintains logical order
**Use when**: Cause-effect relationships matter

### Eventual Consistency
**"All nodes will eventually converge to the same value"**

```mermaid
graph TB
    subgraph "Time T1"
        N1_1[Node 1: x=5] 
        N2_1[Node 2: x=3]
        N3_1[Node 3: x=7]
    end
    
    subgraph "Time T2 (Later)"
        N1_2[Node 1: x=10]
        N2_2[Node 2: x=10] 
        N3_2[Node 3: x=10]
    end
    
    N1_1 --> N1_2
    N2_1 --> N2_2
    N3_1 --> N3_2
    
    style N1_2 fill:#10b981,color:#fff
    style N2_2 fill:#10b981,color:#fff
    style N3_2 fill:#10b981,color:#fff
```

**Examples**: DNS, CDN, social media likes
**Trade-offs**: High availability, temporary inconsistency
**Use when**: Availability is more important than immediate consistency

## Technology Mapping

| Technology | Consistency Model | Use Case |
|------------|------------------|----------|
| **PostgreSQL** | Strong | Financial transactions |
| **MongoDB** | Strong (default) | User profiles |
| **Cassandra** | Eventual (tunable) | Time-series data |
| **Redis** | Strong (single node) | Session storage |
| **DynamoDB** | Eventual (tunable) | Product catalog |
| **Kafka** | Sequential | Event streaming |
| **DNS** | Eventual | Domain resolution |
| **CDN** | Eventual | Static content |

## Choosing the Right Model

```mermaid
flowchart TD
    Start[Choose Consistency Model] --> Q1{Is data correctness<br/>absolutely critical?}
    
    Q1 -->|Yes| Strong[Strong Consistency<br/>💰 Banking, Inventory]
    
    Q1 -->|No| Q2{Do operations have<br/>cause-effect relationships?}
    
    Q2 -->|Yes| Q3{Is ordering<br/>important globally?}
    
    Q3 -->|Yes| Sequential[Sequential Consistency<br/>📋 Distributed DB]
    Q3 -->|No| Causal[Causal Consistency<br/>💬 Social Media]
    
    Q2 -->|No| Q4{Can you tolerate<br/>temporary inconsistency?}
    
    Q4 -->|Yes| Eventual[Eventual Consistency<br/>🌐 CDN, DNS]
    Q4 -->|No| Strong
    
    style Strong fill:#dc2626,color:#fff
    style Sequential fill:#ea580c,color:#fff
    style Causal fill:#f59e0b,color:#fff
    style Eventual fill:#10b981,color:#fff
```

## Real-World Examples

### Social Media Platform

```mermaid
graph TB
    subgraph "Strong Consistency"
        UC[User Credentials] --> UDB[(User Database)]
        style UC fill:#dc2626,color:#fff
    end
    
    subgraph "Causal Consistency"
        Posts[Posts & Comments] --> PDB[(Posts Database)]
        style Posts fill:#f59e0b,color:#fff
    end
    
    subgraph "Eventual Consistency"
        Likes[Likes & Views] --> LDB[(Analytics Database)]
        style Likes fill:#10b981,color:#fff
    end
```

### E-commerce System

```mermaid
graph TB
    subgraph "Strong Consistency"
        Inventory[Inventory Count] --> IDB[(Inventory DB)]
        Payment[Payment Processing] --> PDB[(Payment DB)]
        style Inventory fill:#dc2626,color:#fff
        style Payment fill:#dc2626,color:#fff
    end
    
    subgraph "Eventual Consistency"
        Catalog[Product Catalog] --> CDB[(Catalog DB)]
        Reviews[Product Reviews] --> RDB[(Reviews DB)]
        style Catalog fill:#10b981,color:#fff
        style Reviews fill:#10b981,color:#fff
    end
```

## Performance vs Consistency Trade-off

```mermaid
graph LR
    subgraph "Performance vs Consistency"
        A[High Performance<br/>Low Consistency] --> B[Eventual]
        B --> C[Causal]
        C --> D[Sequential]
        D --> E[Strong]
        E --> F[Low Performance<br/>High Consistency]
    end
    
    style A fill:#10b981,color:#fff
    style F fill:#dc2626,color:#fff
```

## Key Takeaways

1. **No one-size-fits-all** - Different parts of your system may need different consistency models
2. **Understand your data** - Critical data needs stronger consistency
3. **Consider user expectations** - Users may tolerate some inconsistency for better performance
4. **Network partitions matter** - Stronger consistency becomes harder with network issues
5. **Tunable consistency** - Many systems let you configure consistency per operation