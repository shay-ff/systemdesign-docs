# Parking Lot System - Java Implementation

A robust parking lot management system implemented in Java with object-oriented design principles.

## Features

- **Type Safety**: Strong typing with enums for vehicle and spot types
- **Thread Safety**: Synchronized methods for concurrent access (extensible)
- **Clean Architecture**: Separation of concerns with dedicated classes
- **Exception Handling**: Proper error handling for edge cases

## Design Patterns

- **Strategy Pattern**: PricingPolicy for flexible pricing strategies
- **Builder Pattern**: For complex object construction (extensible)
- **Singleton Pattern**: Can be applied to ParkingLot for single instance

## Compilation and Execution

```bash
# Compile all Java files
javac *.java

# Run the demo
java ParkingLotDemo
```

## Class Structure

```
ParkingLotDemo.java     - Main demo class
ParkingLot.java         - Core parking lot management
ParkingLevel.java       - Individual level management
Vehicle.java            - Vehicle representation
ParkingSpot.java        - Spot representation
Ticket.java             - Parking ticket
PricingPolicy.java      - Pricing calculations
VehicleType.java        - Vehicle type enum
SpotType.java           - Spot type enum
```

## Key Features

1. **Efficient Spot Allocation**: O(1) average case using ArrayDeque
2. **Memory Efficient**: Minimal object creation during operations
3. **Extensible Design**: Easy to add new vehicle types or pricing policies
4. **Robust Error Handling**: Comprehensive validation and error messages

## Example Usage

```java
// Create parking lot
List<ParkingLevel> levels = Arrays.asList(
    new ParkingLevel(0, 2, 2, 1),
    new ParkingLevel(1, 1, 2, 1)
);
ParkingLot parkingLot = new ParkingLot("Downtown Plaza", levels);

// Park a vehicle
Vehicle car = new Vehicle("ABC123", VehicleType.CAR);
Ticket ticket = parkingLot.parkVehicle(car);

// Unpark and get fee
double fee = parkingLot.unparkVehicle(ticket);
```