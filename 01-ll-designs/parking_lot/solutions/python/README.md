# Parking Lot System - Python Implementation

A comprehensive parking lot management system supporting multiple vehicle types and parking levels.

## Features

- **Multi-level parking**: Support for multiple floors/levels
- **Vehicle types**: Motorcycle, Car, Bus with different space requirements
- **Smart allocation**: Motorcycles can use any spot, cars use compact/large, buses use large only
- **Pricing system**: Time-based pricing with different rates per vehicle type
- **Ticket management**: Unique ticket generation and validation

## Design Patterns Used

- **Strategy Pattern**: For pricing policies
- **Factory Pattern**: For vehicle and ticket creation
- **Observer Pattern**: For availability notifications (extensible)

## Time Complexity

- **Park vehicle**: O(L × S) where L = levels, S = spots per level (worst case)
- **Unpark vehicle**: O(1) with hash map lookup
- **Check availability**: O(L) where L = number of levels

## Space Complexity

- **Overall**: O(V + S) where V = active vehicles, S = total spots

## Usage

```python
python parking_lot.py
```

## Example Output

```
Parking Lot: CityCenter Mall
Level 0: 2 motorcycle, 2 compact, 1 large spots
Level 1: 1 motorcycle, 2 compact, 1 large spots

Parked KA01AB1234 (Car) -> Ticket: KA01AB1234-L0-S2-1699123456
Parked KA02ZZ9999 (Motorcycle) -> Ticket: KA02ZZ9999-L0-S0-1699123457
Parked BUS777 (Bus) -> Ticket: BUS777-L0-S4-1699123458

Current availability:
Level 0: 1/1/0 (motorcycle/compact/large)
Level 1: 1/2/1 (motorcycle/compact/large)

Unparked KA01AB1234, Fee: $3.00
```

## Key Design Decisions

1. **Flexible spot allocation**: Smaller vehicles can use larger spots when needed
2. **Time-based pricing**: Hourly rates with minimum 1-hour charge
3. **Unique ticket IDs**: Combination of license, location, and timestamp
4. **Level-based organization**: Easy to extend for multi-building scenarios
