package main

import (
	"fmt"
	"sync"
)

// ParkingSpot represents a single parking spot
type ParkingSpot struct {
	mu                   sync.RWMutex
	ID                   int      `json:"id"`
	Type                 SpotType `json:"type"`
	IsOccupied           bool     `json:"is_occupied"`
	CurrentVehicleLicense string   `json:"current_vehicle_license,omitempty"`
}

// NewParkingSpot creates a new parking spot
func NewParkingSpot(id int, spotType SpotType) *ParkingSpot {
	return &ParkingSpot{
		ID:         id,
		Type:       spotType,
		IsOccupied: false,
	}
}

// Occupy marks the spot as occupied by a vehicle
func (ps *ParkingSpot) Occupy(licensePlate string) error {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	
	if ps.IsOccupied {
		return &ParkingError{
			Op:  "occupy_spot",
			Msg: fmt.Sprintf("spot %d is already occupied", ps.ID),
		}
	}
	
	ps.IsOccupied = true
	ps.CurrentVehicleLicense = licensePlate
	return nil
}

// Vacate marks the spot as available
func (ps *ParkingSpot) Vacate() error {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	
	if !ps.IsOccupied {
		return &ParkingError{
			Op:  "vacate_spot",
			Msg: fmt.Sprintf("spot %d is not occupied", ps.ID),
		}
	}
	
	ps.IsOccupied = false
	ps.CurrentVehicleLicense = ""
	return nil
}

// GetStatus returns the current status of the spot (thread-safe)
func (ps *ParkingSpot) GetStatus() (bool, string) {
	ps.mu.RLock()
	defer ps.mu.RUnlock()
	return ps.IsOccupied, ps.CurrentVehicleLicense
}

// GetInfo returns spot information (thread-safe)
func (ps *ParkingSpot) GetInfo() (int, SpotType) {
	ps.mu.RLock()
	defer ps.mu.RUnlock()
	return ps.ID, ps.Type
}

func (ps *ParkingSpot) String() string {
	ps.mu.RLock()
	defer ps.mu.RUnlock()
	
	status := "Available"
	if ps.IsOccupied {
		status = fmt.Sprintf("Occupied by %s", ps.CurrentVehicleLicense)
	}
	return fmt.Sprintf("Spot %d (%s): %s", ps.ID, ps.Type, status)
}