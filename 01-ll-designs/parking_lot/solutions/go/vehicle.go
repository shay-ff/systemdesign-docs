package main

import (
	"fmt"
	"strings"
	"time"
)

// Vehicle represents a vehicle with license plate and type
type Vehicle struct {
	LicensePlate string      `json:"license_plate"`
	Type         VehicleType `json:"type"`
}

// NewVehicle creates a new vehicle with validation
func NewVehicle(licensePlate string, vehicleType VehicleType) (*Vehicle, error) {
	licensePlate = strings.TrimSpace(strings.ToUpper(licensePlate))
	if licensePlate == "" {
		return nil, &ParkingError{Op: "create_vehicle", Msg: "license plate cannot be empty"}
	}
	
	return &Vehicle{
		LicensePlate: licensePlate,
		Type:         vehicleType,
	}, nil
}

func (v *Vehicle) String() string {
	return fmt.Sprintf("%s (%s)", v.LicensePlate, v.Type)
}

// Ticket represents a parking ticket
type Ticket struct {
	ID           string      `json:"id"`
	LicensePlate string      `json:"license_plate"`
	VehicleType  VehicleType `json:"vehicle_type"`
	EntryTime    time.Time   `json:"entry_time"`
	LevelIndex   int         `json:"level_index"`
	SpotID       int         `json:"spot_id"`
	SpotType     SpotType    `json:"spot_type"`
}

// NewTicket creates a new parking ticket
func NewTicket(licensePlate string, vehicleType VehicleType, levelIndex, spotID int, spotType SpotType) *Ticket {
	return &Ticket{
		ID:           generateTicketID(licensePlate, levelIndex, spotID),
		LicensePlate: licensePlate,
		VehicleType:  vehicleType,
		EntryTime:    time.Now(),
		LevelIndex:   levelIndex,
		SpotID:       spotID,
		SpotType:     spotType,
	}
}

func (t *Ticket) String() string {
	return fmt.Sprintf("Ticket %s: %s (%s) at Level %d, Spot %d (entered %s)",
		t.ID, t.LicensePlate, t.VehicleType, t.LevelIndex, t.SpotID,
		t.EntryTime.Format("15:04:05"))
}

// generateTicketID generates a unique ticket ID
func generateTicketID(licensePlate string, levelIndex, spotID int) string {
	timestamp := time.Now().UnixNano() / int64(time.Millisecond)
	return fmt.Sprintf("%s-L%d-S%d-%d", licensePlate, levelIndex, spotID, timestamp)
}