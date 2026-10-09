#!/usr/bin/env python3
"""
Parking Lot System - Low Level Design Implementation

A comprehensive parking lot management system that handles multiple vehicle types,
multi-level parking, and time-based pricing.

Author: System Design Learning Guide
"""

import time
import math
from enum import Enum
from typing import Dict, List, Optional, Tuple, Deque
from collections import deque
from dataclasses import dataclass
import uuid


class VehicleType(Enum):
    MOTORCYCLE = "motorcycle"
    CAR = "car"
    BUS = "bus"


class SpotType(Enum):
    MOTORCYCLE = "motorcycle"
    COMPACT = "compact"
    LARGE = "large"


@dataclass
class Vehicle:
    """Represents a vehicle with license plate and type."""
    license_plate: str
    vehicle_type: VehicleType
    
    def __str__(self) -> str:
        return f"{self.license_plate} ({self.vehicle_type.value.title()})"


@dataclass
class ParkingSpot:
    """Represents a single parking spot."""
    spot_id: int
    spot_type: SpotType
    is_occupied: bool = False
    current_vehicle: Optional[str] = None  # license plate
    
    def occupy(self, license_plate: str) -> None:
        """Mark spot as occupied by a vehicle."""
        self.is_occupied = True
        self.current_vehicle = license_plate
    
    def vacate(self) -> None:
        """Mark spot as available."""
        self.is_occupied = False
        self.current_vehicle = None


@dataclass
class Ticket:
    """Represents a parking ticket."""
    ticket_id: str
    license_plate: str
    vehicle_type: VehicleType
    entry_time: float
    level_index: int
    spot_id: int
    spot_type: SpotType
    
    def __str__(self) -> str:
        entry_str = time.strftime("%H:%M:%S", time.localtime(self.entry_time))
        return f"Ticket {self.ticket_id}: {self.license_plate} at Level {self.level_index}, Spot {self.spot_id} (entered {entry_str})"


class PricingPolicy:
    """Handles pricing calculations for different vehicle types."""
    
    def __init__(self):
        self.base_fee = 2.0
        self.hourly_rates = {
            VehicleType.MOTORCYCLE: 0.5,
            VehicleType.CAR: 1.0,
            VehicleType.BUS: 3.0
        }
    
    def calculate_fee(self, vehicle_type: VehicleType, entry_time: float, exit_time: float) -> float:
        """Calculate parking fee based on vehicle type and duration."""
        duration_hours = max(1.0, math.ceil((exit_time - entry_time) / 3600.0))
        hourly_rate = self.hourly_rates[vehicle_type]
        return self.base_fee + (hourly_rate * duration_hours)


class ParkingLevel:
    """Represents a single level/floor of the parking lot."""
    
    def __init__(self, level_index: int, motorcycle_spots: int, compact_spots: int, large_spots: int):
        self.level_index = level_index
        self.spots: List[ParkingSpot] = []
        
        # Queues for quick spot allocation
        self.free_motorcycle: Deque[int] = deque()
        self.free_compact: Deque[int] = deque()
        self.free_large: Deque[int] = deque()
        
        self._initialize_spots(motorcycle_spots, compact_spots, large_spots)
    
    def _initialize_spots(self, motorcycle_spots: int, compact_spots: int, large_spots: int) -> None:
        """Initialize all parking spots and free queues."""
        spot_id = 0
        
        # Create motorcycle spots
        for _ in range(motorcycle_spots):
            self.spots.append(ParkingSpot(spot_id, SpotType.MOTORCYCLE))
            self.free_motorcycle.append(spot_id)
            spot_id += 1
        
        # Create compact spots
        for _ in range(compact_spots):
            self.spots.append(ParkingSpot(spot_id, SpotType.COMPACT))
            self.free_compact.append(spot_id)
            spot_id += 1
        
        # Create large spots
        for _ in range(large_spots):
            self.spots.append(ParkingSpot(spot_id, SpotType.LARGE))
            self.free_large.append(spot_id)
            spot_id += 1
    
    def find_available_spot(self, vehicle_type: VehicleType) -> Optional[int]:
        """Find and allocate an available spot for the given vehicle type."""
        if vehicle_type == VehicleType.MOTORCYCLE:
            # Motorcycles can use any spot type (prefer smaller first)
            if self.free_motorcycle:
                return self.free_motorcycle.popleft()
            elif self.free_compact:
                return self.free_compact.popleft()
            elif self.free_large:
                return self.free_large.popleft()
        
        elif vehicle_type == VehicleType.CAR:
            # Cars can use compact or large spots
            if self.free_compact:
                return self.free_compact.popleft()
            elif self.free_large:
                return self.free_large.popleft()
        
        elif vehicle_type == VehicleType.BUS:
            # Buses can only use large spots
            if self.free_large:
                return self.free_large.popleft()
        
        return None
    
    def release_spot(self, spot_index: int) -> None:
        """Release a spot and add it back to the appropriate free queue."""
        spot = self.spots[spot_index]
        spot.vacate()
        
        # Add back to appropriate free queue
        if spot.spot_type == SpotType.MOTORCYCLE:
            self.free_motorcycle.append(spot_index)
        elif spot.spot_type == SpotType.COMPACT:
            self.free_compact.append(spot_index)
        elif spot.spot_type == SpotType.LARGE:
            self.free_large.append(spot_index)
    
    def get_availability(self) -> Tuple[int, int, int]:
        """Get current availability count for each spot type."""
        return (len(self.free_motorcycle), len(self.free_compact), len(self.free_large))
    
    def __str__(self) -> str:
        motorcycle, compact, large = self.get_availability()
        total_spots = len(self.spots)
        occupied = sum(1 for spot in self.spots if spot.is_occupied)
        return f"Level {self.level_index}: {motorcycle}/{compact}/{large} available (motorcycle/compact/large), {occupied}/{total_spots} occupied"


class ParkingLot:
    """Main parking lot management system."""
    
    def __init__(self, name: str, levels: List[ParkingLevel]):
        self.name = name
        self.levels = levels
        self.pricing_policy = PricingPolicy()
        
        # Maps for quick lookups
        self.active_tickets: Dict[str, Ticket] = {}  # license_plate -> ticket
        self.spot_to_license: Dict[Tuple[int, int], str] = {}  # (level, spot_id) -> license_plate
    
    def park_vehicle(self, vehicle: Vehicle) -> Optional[Ticket]:
        """Park a vehicle and return a ticket if successful."""
        # Check if vehicle is already parked
        if vehicle.license_plate in self.active_tickets:
            print(f"Vehicle {vehicle.license_plate} is already parked!")
            return None
        
        # Try to find a spot across all levels
        for level in self.levels:
            spot_index = level.find_available_spot(vehicle.vehicle_type)
            if spot_index is not None:
                # Allocate the spot
                spot = level.spots[spot_index]
                spot.occupy(vehicle.license_plate)
                
                # Create ticket
                ticket = Ticket(
                    ticket_id=self._generate_ticket_id(vehicle.license_plate, level.level_index, spot.spot_id),
                    license_plate=vehicle.license_plate,
                    vehicle_type=vehicle.vehicle_type,
                    entry_time=time.time(),
                    level_index=level.level_index,
                    spot_id=spot.spot_id,
                    spot_type=spot.spot_type
                )
                
                # Update tracking maps
                self.active_tickets[vehicle.license_plate] = ticket
                self.spot_to_license[(level.level_index, spot.spot_id)] = vehicle.license_plate
                
                return ticket
        
        # No available spots
        return None
    
    def unpark_vehicle(self, ticket: Ticket) -> Optional[float]:
        """Unpark a vehicle and return the fee charged."""
        license_plate = ticket.license_plate
        
        # Verify ticket is valid
        if license_plate not in self.active_tickets:
            print(f"Invalid ticket: {license_plate} not found in active tickets")
            return None
        
        stored_ticket = self.active_tickets[license_plate]
        if stored_ticket.ticket_id != ticket.ticket_id:
            print(f"Ticket mismatch for {license_plate}")
            return None
        
        # Find the spot and release it
        level = self.levels[ticket.level_index]
        spot_index = None
        for i, spot in enumerate(level.spots):
            if spot.spot_id == ticket.spot_id and spot.current_vehicle == license_plate:
                spot_index = i
                break
        
        if spot_index is None:
            print(f"Spot not found for ticket {ticket.ticket_id}")
            return None
        
        # Release the spot
        level.release_spot(spot_index)
        
        # Calculate fee
        exit_time = time.time()
        fee = self.pricing_policy.calculate_fee(ticket.vehicle_type, ticket.entry_time, exit_time)
        
        # Clean up tracking maps
        del self.active_tickets[license_plate]
        del self.spot_to_license[(ticket.level_index, ticket.spot_id)]
        
        return fee
    
    def get_availability_summary(self) -> str:
        """Get a summary of current availability across all levels."""
        summary = [f"Parking Lot: {self.name}"]
        total_motorcycle = total_compact = total_large = 0
        
        for level in self.levels:
            motorcycle, compact, large = level.get_availability()
            total_motorcycle += motorcycle
            total_compact += compact
            total_large += large
            summary.append(str(level))
        
        summary.append(f"Total available: {total_motorcycle} motorcycle, {total_compact} compact, {total_large} large")
        return "\n".join(summary)
    
    def _generate_ticket_id(self, license_plate: str, level_index: int, spot_id: int) -> str:
        """Generate a unique ticket ID."""
        timestamp = int(time.time() * 1000)  # milliseconds
        return f"{license_plate}-L{level_index}-S{spot_id}-{timestamp}"
    
    def __str__(self) -> str:
        return self.get_availability_summary()


def demo():
    """Demonstrate the parking lot system."""
    print("=== Parking Lot System Demo ===\n")
    
    # Create a parking lot with 2 levels
    levels = [
        ParkingLevel(0, motorcycle_spots=2, compact_spots=2, large_spots=1),
        ParkingLevel(1, motorcycle_spots=1, compact_spots=2, large_spots=1)
    ]
    
    parking_lot = ParkingLot("CityCenter Mall", levels)
    
    # Show initial availability
    print("Initial state:")
    print(parking_lot)
    print()
    
    # Create some vehicles
    vehicles = [
        Vehicle("KA01AB1234", VehicleType.CAR),
        Vehicle("KA02ZZ9999", VehicleType.MOTORCYCLE),
        Vehicle("BUS777", VehicleType.BUS),
        Vehicle("MH12CD5678", VehicleType.CAR),
        Vehicle("BIKE123", VehicleType.MOTORCYCLE)
    ]
    
    # Park vehicles
    tickets = []
    print("Parking vehicles:")
    for vehicle in vehicles:
        ticket = parking_lot.park_vehicle(vehicle)
        if ticket:
            tickets.append(ticket)
            print(f"✓ Parked {vehicle} -> {ticket.ticket_id}")
        else:
            print(f"✗ Failed to park {vehicle} (lot full)")
    
    print()
    print("After parking:")
    print(parking_lot)
    print()
    
    # Simulate some time passing
    print("Waiting 2 seconds to simulate parking duration...")
    time.sleep(2)
    
    # Unpark some vehicles
    print("Unparking vehicles:")
    for i, ticket in enumerate(tickets[:3]):  # Unpark first 3 vehicles
        fee = parking_lot.unpark_vehicle(ticket)
        if fee is not None:
            print(f"✓ Unparked {ticket.license_plate}, Fee: ${fee:.2f}")
        else:
            print(f"✗ Failed to unpark {ticket.license_plate}")
    
    print()
    print("Final state:")
    print(parking_lot)


if __name__ == "__main__":
    demo()