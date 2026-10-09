package main

import (
	"fmt"
	"sync"
)

// ParkingLevel represents a single level/floor of the parking lot
type ParkingLevel struct {
	mu         sync.RWMutex
	Index      int                    `json:"index"`
	Spots      []*ParkingSpot         `json:"spots"`
	FreeSpots  map[SpotType][]int     `json:"-"` // indices of free spots by type
}

// NewParkingLevel creates a new parking level
func NewParkingLevel(index, motorcycleSpots, compactSpots, largeSpots int) *ParkingLevel {
	level := &ParkingLevel{
		Index:     index,
		Spots:     make([]*ParkingSpot, 0),
		FreeSpots: make(map[SpotType][]int),
	}
	
	level.initializeSpots(motorcycleSpots, compactSpots, largeSpots)
	return level
}

// initializeSpots creates all parking spots and populates free spot queues
func (pl *ParkingLevel) initializeSpots(motorcycleSpots, compactSpots, largeSpots int) {
	spotID := 0
	
	// Initialize free spot slices
	pl.FreeSpots[SpotTypeMotorcycle] = make([]int, 0, motorcycleSpots)
	pl.FreeSpots[SpotTypeCompact] = make([]int, 0, compactSpots)
	pl.FreeSpots[SpotTypeLarge] = make([]int, 0, largeSpots)
	
	// Create motorcycle spots
	for i := 0; i < motorcycleSpots; i++ {
		pl.Spots = append(pl.Spots, NewParkingSpot(spotID, SpotTypeMotorcycle))
		pl.FreeSpots[SpotTypeMotorcycle] = append(pl.FreeSpots[SpotTypeMotorcycle], len(pl.Spots)-1)
		spotID++
	}
	
	// Create compact spots
	for i := 0; i < compactSpots; i++ {
		pl.Spots = append(pl.Spots, NewParkingSpot(spotID, SpotTypeCompact))
		pl.FreeSpots[SpotTypeCompact] = append(pl.FreeSpots[SpotTypeCompact], len(pl.Spots)-1)
		spotID++
	}
	
	// Create large spots
	for i := 0; i < largeSpots; i++ {
		pl.Spots = append(pl.Spots, NewParkingSpot(spotID, SpotTypeLarge))
		pl.FreeSpots[SpotTypeLarge] = append(pl.FreeSpots[SpotTypeLarge], len(pl.Spots)-1)
		spotID++
	}
}

// FindAvailableSpot finds and allocates an available spot for the given vehicle type
func (pl *ParkingLevel) FindAvailableSpot(vehicleType VehicleType) (int, error) {
	pl.mu.Lock()
	defer pl.mu.Unlock()
	
	switch vehicleType {
	case VehicleTypeMotorcycle:
		// Motorcycles can use any spot type (prefer smaller first)
		if spotIndex := pl.popFreeSpot(SpotTypeMotorcycle); spotIndex != -1 {
			return spotIndex, nil
		}
		if spotIndex := pl.popFreeSpot(SpotTypeCompact); spotIndex != -1 {
			return spotIndex, nil
		}
		if spotIndex := pl.popFreeSpot(SpotTypeLarge); spotIndex != -1 {
			return spotIndex, nil
		}
		
	case VehicleTypeCar:
		// Cars can use compact or large spots
		if spotIndex := pl.popFreeSpot(SpotTypeCompact); spotIndex != -1 {
			return spotIndex, nil
		}
		if spotIndex := pl.popFreeSpot(SpotTypeLarge); spotIndex != -1 {
			return spotIndex, nil
		}
		
	case VehicleTypeBus:
		// Buses can only use large spots
		if spotIndex := pl.popFreeSpot(SpotTypeLarge); spotIndex != -1 {
			return spotIndex, nil
		}
	}
	
	return -1, ErrNoAvailableSpots
}

// ReleaseSpot releases a spot and adds it back to the appropriate free queue
func (pl *ParkingLevel) ReleaseSpot(spotIndex int) error {
	pl.mu.Lock()
	defer pl.mu.Unlock()
	
	if spotIndex < 0 || spotIndex >= len(pl.Spots) {
		return &ParkingError{
			Op:  "release_spot",
			Msg: fmt.Sprintf("invalid spot index: %d", spotIndex),
		}
	}
	
	spot := pl.Spots[spotIndex]
	if err := spot.Vacate(); err != nil {
		return err
	}
	
	// Add back to appropriate free queue
	_, spotType := spot.GetInfo()
	pl.FreeSpots[spotType] = append(pl.FreeSpots[spotType], spotIndex)
	
	return nil
}

// GetSpot returns the parking spot at the given index
func (pl *ParkingLevel) GetSpot(spotIndex int) (*ParkingSpot, error) {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	if spotIndex < 0 || spotIndex >= len(pl.Spots) {
		return nil, &ParkingError{
			Op:  "get_spot",
			Msg: fmt.Sprintf("invalid spot index: %d", spotIndex),
		}
	}
	
	return pl.Spots[spotIndex], nil
}

// FindSpotIndexByID finds spot index by spot ID
func (pl *ParkingLevel) FindSpotIndexByID(spotID int) int {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	for i, spot := range pl.Spots {
		id, _ := spot.GetInfo()
		if id == spotID {
			return i
		}
	}
	return -1
}

// GetAvailability returns current availability count for each spot type
func (pl *ParkingLevel) GetAvailability() (motorcycle, compact, large int) {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	return len(pl.FreeSpots[SpotTypeMotorcycle]),
		   len(pl.FreeSpots[SpotTypeCompact]),
		   len(pl.FreeSpots[SpotTypeLarge])
}

// GetTotalSpots returns total number of spots in this level
func (pl *ParkingLevel) GetTotalSpots() int {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	return len(pl.Spots)
}

// GetOccupiedSpots returns number of occupied spots in this level
func (pl *ParkingLevel) GetOccupiedSpots() int {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	occupied := 0
	for _, spot := range pl.Spots {
		if isOccupied, _ := spot.GetStatus(); isOccupied {
			occupied++
		}
	}
	return occupied
}

// popFreeSpot removes and returns the first available spot index of the given type
// Returns -1 if no spots available. Must be called with lock held.
func (pl *ParkingLevel) popFreeSpot(spotType SpotType) int {
	freeSpots := pl.FreeSpots[spotType]
	if len(freeSpots) == 0 {
		return -1
	}
	
	spotIndex := freeSpots[0]
	pl.FreeSpots[spotType] = freeSpots[1:]
	return spotIndex
}

func (pl *ParkingLevel) String() string {
	motorcycle, compact, large := pl.GetAvailability()
	occupied := pl.GetOccupiedSpots()
	total := pl.GetTotalSpots()
	
	return fmt.Sprintf("Level %d: %d/%d/%d available (motorcycle/compact/large), %d/%d occupied",
		pl.Index, motorcycle, compact, large, occupied, total)
}