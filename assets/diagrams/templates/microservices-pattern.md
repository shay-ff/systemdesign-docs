# Microservices Architecture Template

## Mermaid Template

```mermaid
graph TB
    U[👤 User] --> AG[🚪 API Gateway]
    AG --> US[⚙️ User Service]
    AG --> OS[⚙️ Order Service]
    AG --> PS[⚙️ Payment Service]
    
    US --> UC[⚡ User Cache]
    US --> UD[(🗄️ User DB)]
    
    OS --> OC[⚡ Order Cache]
    OS --> OD[(🗄️ Order DB)]
    OS --> MQ[📢 Message Queue]
    
    PS --> PC[⚡ Payment Cache]
    PS --> PD[(🗄️ Payment DB)]
    PS --> MQ
    
    MQ --> NS[⚙️ Notification Service]
    NS --> ND[(🗄️ Notification DB)]
    
    style U fill:#e2e8f0
    style AG fill:#0891b2,color:#fff
    style US fill:#2563eb,color:#fff
    style OS fill:#2563eb,color:#fff
    style PS fill:#2563eb,color:#fff
    style NS fill:#2563eb,color:#fff
    style UC fill:#dc2626,color:#fff
    style OC fill:#dc2626,color:#fff
    style PC fill:#dc2626,color:#fff
    style UD fill:#059669,color:#fff
    style OD fill:#059669,color:#fff
    style PD fill:#059669,color:#fff
    style ND fill:#059669,color:#fff
    style MQ fill:#ea580c,color:#fff
```

## PlantUML Template

```plantuml
@startuml
!define PRIMARY_COLOR #2563eb
!define CACHE_COLOR #dc2626
!define DATABASE_COLOR #059669
!define GATEWAY_COLOR #0891b2
!define QUEUE_COLOR #ea580c

actor User as U
component "API Gateway" as AG <<GATEWAY_COLOR>>

package "User Service" {
    component "User Service" as US <<PRIMARY_COLOR>>
    component "User Cache" as UC <<CACHE_COLOR>>
    database "User DB" as UD <<DATABASE_COLOR>>
}

package "Order Service" {
    component "Order Service" as OS <<PRIMARY_COLOR>>
    component "Order Cache" as OC <<CACHE_COLOR>>
    database "Order DB" as OD <<DATABASE_COLOR>>
}

package "Payment Service" {
    component "Payment Service" as PS <<PRIMARY_COLOR>>
    component "Payment Cache" as PC <<CACHE_COLOR>>
    database "Payment DB" as PD <<DATABASE_COLOR>>
}

component "Message Queue" as MQ <<QUEUE_COLOR>>
component "Notification Service" as NS <<PRIMARY_COLOR>>
database "Notification DB" as ND <<DATABASE_COLOR>>

U --> AG : API Request
AG --> US : Route
AG --> OS : Route
AG --> PS : Route

US --> UC : Cache
US --> UD : Persist

OS --> OC : Cache
OS --> OD : Persist
OS --> MQ : Event

PS --> PC : Cache
PS --> PD : Persist
PS --> MQ : Event

MQ --> NS : Process
NS --> ND : Store

@enduml
```

## Key Patterns

1. **API Gateway**: Single entry point for all client requests
2. **Service Isolation**: Each service has its own database and cache
3. **Asynchronous Communication**: Message queue for loose coupling
4. **Data Consistency**: Event-driven architecture for eventual consistency

## Benefits

- **Scalability**: Each service can scale independently
- **Fault Isolation**: Failure in one service doesn't affect others
- **Technology Diversity**: Different services can use different tech stacks
- **Team Autonomy**: Teams can develop and deploy independently

## Considerations

- **Complexity**: More moving parts to manage
- **Network Latency**: Inter-service communication overhead
- **Data Consistency**: Eventual consistency challenges
- **Monitoring**: Need comprehensive observability