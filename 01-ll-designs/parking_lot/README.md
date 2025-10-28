Parking Lot (LLD) - supports motorcycle, car, bus; multiple levels with spot types.

Files:
- `src/parking_lot.cpp` - runnable demo
- `design.puml` - PlantUML class diagram

## Build & Run
```bash
cd 01-ll-designs/parking_lot/src
g++ -std=c++17 parking_lot.cpp -o parking && ./parking
```

## Notes
- Fit rules: Motorcycle -> (Motorcycle|Compact|Large), Car -> (Compact|Large), Bus -> (Large).
- Simple flat pricing; replace `bits/stdc++.h` with explicit headers if needed.

