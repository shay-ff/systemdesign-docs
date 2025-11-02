# Parking Lot System - Go Implementation

A concurrent-safe parking lot management system implemented in Go with goroutine safety and idiomatic Go patterns.

## Features

- **Goroutine Safety**: Thread-safe operations using mutexes
- **Idiomatic Go**: Follows Go conventions and best practices
- **Error Handling**: Comprehensive error handling with custom error types
- **JSON Support**: Structs with JSON tags for serialization
- **Interface Design**: Clean interfaces for extensibility

## Design Patterns

- **Interface Segregation**: Small, focused interfaces
- **Dependency Injection**: Configurable pricing policies
- **Error Wrapping**: Go 1.13+ error wrapping for better error context

## Building and Running

```bash
# Initialize Go module (if not already done)
go mod init parking-lot

# Run the demo
go run *.go

# Or build and run
go build -o parking-lot
./parking-lot
```

## Package Structure

```
main.go           - Demo application
parking_lot.go    - Core parking lot logic
vehicle.go        - Vehicle and ticket types
spot.go           - Parking spot management
level.go          - Level management
pricing.go        - Pricing policy
types.go          - Common types and enums
```

## Key Go Features Used

1. **Channels**: For potential async operations (extensible)
2. **Interfaces**: For clean abstraction
3. **Struct Embedding**: For composition over inheritance
4. **Error Handling**: Idiomatic Go error handling
5. **Mutexes**: For concurrent safety

## Concurrency Safety

All public methods are goroutine-safe using:
- `sync.RWMutex` for read-heavy operations
- `sync.Mutex` for write operations
- Atomic operations where appropriate

## Example Usage

```go
// Create parking lot
levels := []*ParkingLevel{
    NewParkingLevel(0, 2, 2, 1),
    NewParkingLevel(1, 1, 2, 1),
}
parkingLot := NewParkingLot("Downtown Plaza", levels)

// Park a vehicle
vehicle := &Vehicle{LicensePlate: "ABC123", Type: VehicleTypeCar}
ticket, err := parkingLot.ParkVehicle(vehicle)

// Unpark and get fee
fee, err := parkingLot.UnparkVehicle(ticket)
```