# Parking Lot System - Low Level Design

A comprehensive parking lot management system supporting multiple vehicle types, multi-level parking, and flexible pricing strategies.

## Problem Statement

Design a parking lot system that can handle different vehicle types (Motorcycle, Car, Bus) across multiple levels with different spot sizes and time-based pricing.

## Key Features

- **Multi-level parking**: Support for multiple floors/levels
- **Vehicle types**: Motorcycle, Car, Bus with smart allocation rules
- **Spot types**: Motorcycle, Compact, Large spots with flexible assignment
- **Time-based pricing**: Configurable pricing policies with hourly rates
- **Ticket management**: Unique ticket generation and validation
- **Concurrent access**: Thread-safe operations (language-dependent)

## Smart Allocation Rules

- **Motorcycles**: Can use Motorcycle → Compact → Large spots (in order of preference)
- **Cars**: Can use Compact → Large spots
- **Buses**: Can only use Large spots

## Files Structure

```
parking_lot/
├── README.md              # This file
├── design.puml           # PlantUML class diagram
├── explanation.md        # Detailed design explanation and trade-offs
├── src/                  # Original C++ implementation
│   └── parking_lot.cpp
└── solutions/            # Multi-language implementations
    ├── python/           # Python implementation with type hints
    ├── java/             # Java implementation with strong typing
    ├── go/               # Go implementation with goroutine safety
    └── cpp/              # Enhanced C++ implementation (future)
```

## Quick Start

### Python
```bash
cd solutions/python
python parking_lot.py
```

### Java
```bash
cd solutions/java
javac *.java
java ParkingLotDemo
```

### Go
```bash
cd solutions/go
go run *.go
```

### C++ (Original)
```bash
cd src
g++ -std=c++17 parking_lot.cpp -o parking && ./parking
```

## Design Patterns Used

- **Strategy Pattern**: Flexible pricing policies
- **Composite Pattern**: Hierarchical parking structure (Lot → Levels → Spots)
- **Factory Pattern**: Ticket and vehicle creation
- **Observer Pattern**: Availability notifications (extensible)

## Time Complexity

- **Park vehicle**: O(L × S) worst case, O(1) average case
- **Unpark vehicle**: O(1) with hash map lookups
- **Check availability**: O(L) where L = number of levels

## Key Design Decisions

1. **Efficient Allocation**: Use queues/deques for O(1) spot allocation
2. **Flexible Pricing**: Strategy pattern allows different pricing models
3. **Thread Safety**: Proper locking mechanisms in concurrent implementations
4. **Extensibility**: Interface-based design for easy feature additions

## Real-World Extensions

- Reservation system for advance booking
- Payment processing integration
- Mobile app API endpoints
- Electric vehicle charging station support
- Real-time availability monitoring

## Learning Objectives

This implementation demonstrates:
- Object-oriented design principles
- Data structure selection for performance
- Concurrency handling in different languages
- Real-world system modeling
- Trade-off analysis between memory and speed

For detailed design discussion, trade-offs analysis, and interview preparation, see [explanation.md](explanation.md).

