package main

import (
	"fmt"
	"strings"
	"sync"
	"time"
)

// ParkingLot represents the main parking lot management system
type ParkingLot struct {
	mu            sync.RWMutex
	Name          string                    `json:"name"`
	Levels        []*ParkingLevel           `json:"levels"`
	PricingPolicy PricingPolicy             `json:"-"`
	ActiveTickets map[string]*Ticket        `json:"active_tickets"`
	SpotToLicense map[string]string         `json:"-"` // "level-spotId" -> licensePlate
}

// NewParkingLot creates a new parking lot
func NewParkingLot(name string, levels []*ParkingLevel) *ParkingLot {
	return &ParkingLot{
		Name:          strings.TrimSpace(name),
		Levels:        levels,
		PricingPolicy: NewStandardPricingPolicy(),
		ActiveTickets: make(map[string]*Ticket),
		SpotToLicense: make(map[string]string),
	}
}

// SetPricingPolicy sets a custom pricing policy
func (pl *ParkingLot) SetPricingPolicy(policy PricingPolicy) {
	pl.mu.Lock()
	defer pl.mu.Unlock()
	pl.PricingPolicy = policy
}

// ParkVehicle parks a vehicle and returns a ticket if successful
func (pl *ParkingLot) ParkVehicle(vehicle *Vehicle) (*Ticket, error) {
	if vehicle == nil {
		return nil, &ParkingError{Op: "park", Msg: "vehicle cannot be nil"}
	}
	
	pl.mu.Lock()
	defer pl.mu.Unlock()
	
	licensePlate := vehicle.LicensePlate
	
	// Check if vehicle is already parked
	if _, exists := pl.ActiveTickets[licensePlate]; exists {
		return nil, &ParkingError{
			Op:  "park",
			Msg: fmt.Sprintf("vehicle %s is already parked", licensePlate),
		}
	}
	
	// Try to find a spot across all levels
	for _, level := range pl.Levels {
		spotIndex, err := level.FindAvailableSpot(vehicle.Type)
		if err != nil {
			continue // Try next level
		}
		
		// Get the spot and occupy it
		spot, err := level.GetSpot(spotIndex)
		if err != nil {
			continue // This shouldn't happen, but handle gracefully
		}
		
		if err := spot.Occupy(licensePlate); err != nil {
			continue // Spot was taken by another goroutine, try next
		}
		
		// Create ticket
		spotID, spotType := spot.GetInfo()
		ticket := NewTicket(licensePlate, vehicle.Type, level.Index, spotID, spotType)
		
		// Update tracking maps
		pl.ActiveTickets[licensePlate] = ticket
		pl.SpotToLicense[pl.getSpotKey(level.Index, spotID)] = licensePlate
		
		return ticket, nil
	}
	
	return nil, ErrNoAvailableSpots
}

// UnparkVehicle unparks a vehicle and returns the fee charged
func (pl *ParkingLot) UnparkVehicle(ticket *Ticket) (float64, error) {
	if ticket == nil {
		return 0, &ParkingError{Op: "unpark", Msg: "ticket cannot be nil"}
	}
	
	pl.mu.Lock()
	defer pl.mu.Unlock()
	
	licensePlate := ticket.LicensePlate
	
	// Verify ticket is valid
	storedTicket, exists := pl.ActiveTickets[licensePlate]
	if !exists {
		return 0, &ParkingError{
			Op:  "unpark",
			Msg: fmt.Sprintf("ticket for %s not found in active tickets", licensePlate),
		}
	}
	
	if storedTicket.ID != ticket.ID {
		return 0, &ParkingError{
			Op:  "unpark",
			Msg: fmt.Sprintf("ticket mismatch for %s", licensePlate),
		}
	}
	
	// Find the level and spot
	level := pl.findLevel(ticket.LevelIndex)
	if level == nil {
		return 0, &ParkingError{
			Op:  "unpark",
			Msg: fmt.Sprintf("level %d not found", ticket.LevelIndex),
		}
	}
	
	spotIndex := level.FindSpotIndexByID(ticket.SpotID)
	if spotIndex == -1 {
		return 0, &ParkingError{
			Op:  "unpark",
			Msg: fmt.Sprintf("spot %d not found in level %d", ticket.SpotID, ticket.LevelIndex),
		}
	}
	
	spot, err := level.GetSpot(spotIndex)
	if err != nil {
		return 0, err
	}
	
	// Verify spot occupancy
	isOccupied, currentLicense := spot.GetStatus()
	if !isOccupied || currentLicense != licensePlate {
		return 0, &ParkingError{
			Op:  "unpark",
			Msg: fmt.Sprintf("spot occupancy mismatch for %s", licensePlate),
		}
	}
	
	// Release the spot
	if err := level.ReleaseSpot(spotIndex); err != nil {
		return 0, err
	}
	
	// Calculate fee
	exitTime := time.Now()
	fee := pl.PricingPolicy.CalculateFee(ticket.VehicleType, ticket.EntryTime, exitTime)
	
	// Clean up tracking maps
	delete(pl.ActiveTickets, licensePlate)
	delete(pl.SpotToLicense, pl.getSpotKey(ticket.LevelIndex, ticket.SpotID))
	
	return fee, nil
}

// GetAvailabilitySummary returns a formatted string with availability information
func (pl *ParkingLot) GetAvailabilitySummary() string {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	var summary strings.Builder
	summary.WriteString(fmt.Sprintf("Parking Lot: %s\n", pl.Name))
	
	totalMotorcycle, totalCompact, totalLarge := 0, 0, 0
	
	for _, level := range pl.Levels {
		motorcycle, compact, large := level.GetAvailability()
		totalMotorcycle += motorcycle
		totalCompact += compact
		totalLarge += large
		summary.WriteString(fmt.Sprintf("%s\n", level.String()))
	}
	
	summary.WriteString(fmt.Sprintf("Total available: %d motorcycle, %d compact, %d large",
		totalMotorcycle, totalCompact, totalLarge))
	
	return summary.String()
}

// GetActiveTickets returns a copy of all active tickets
func (pl *ParkingLot) GetActiveTickets() []*Ticket {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	tickets := make([]*Ticket, 0, len(pl.ActiveTickets))
	for _, ticket := range pl.ActiveTickets {
		tickets = append(tickets, ticket)
	}
	return tickets
}

// IsVehicleParked checks if a vehicle is currently parked
func (pl *ParkingLot) IsVehicleParked(licensePlate string) bool {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	_, exists := pl.ActiveTickets[licensePlate]
	return exists
}

// GetTicketForVehicle returns the ticket for a parked vehicle
func (pl *ParkingLot) GetTicketForVehicle(licensePlate string) *Ticket {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	return pl.ActiveTickets[licensePlate]
}

// GetName returns the parking lot name
func (pl *ParkingLot) GetName() string {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	return pl.Name
}

// GetLevels returns a copy of all levels
func (pl *ParkingLot) GetLevels() []*ParkingLevel {
	pl.mu.RLock()
	defer pl.mu.RUnlock()
	
	levels := make([]*ParkingLevel, len(pl.Levels))
	copy(levels, pl.Levels)
	return levels
}

// getSpotKey generates a spot key for mapping
func (pl *ParkingLot) getSpotKey(levelIndex, spotID int) string {
	return fmt.Sprintf("%d-%d", levelIndex, spotID)
}

// findLevel finds a level by index (must be called with lock held)
func (pl *ParkingLot) findLevel(levelIndex int) *ParkingLevel {
	for _, level := range pl.Levels {
		if level.Index == levelIndex {
			return level
		}
	}
	return nil
}

func (pl *ParkingLot) String() string {
	return pl.GetAvailabilitySummary()
}