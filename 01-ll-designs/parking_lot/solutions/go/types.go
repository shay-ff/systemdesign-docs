package main

import "fmt"

// VehicleType represents the type of vehicle
type VehicleType int

const (
	VehicleTypeMotorcycle VehicleType = iota
	VehicleTypeCar
	VehicleTypeBus
)

func (vt VehicleType) String() string {
	switch vt {
	case VehicleTypeMotorcycle:
		return "Motorcycle"
	case VehicleTypeCar:
		return "Car"
	case VehicleTypeBus:
		return "Bus"
	default:
		return "Unknown"
	}
}

// SpotType represents the type of parking spot
type SpotType int

const (
	SpotTypeMotorcycle SpotType = iota
	SpotTypeCompact
	SpotTypeLarge
)

func (st SpotType) String() string {
	switch st {
	case SpotTypeMotorcycle:
		return "Motorcycle"
	case SpotTypeCompact:
		return "Compact"
	case SpotTypeLarge:
		return "Large"
	default:
		return "Unknown"
	}
}

// ParkingError represents parking-related errors
type ParkingError struct {
	Op  string // operation that failed
	Err error  // underlying error
	Msg string // additional message
}

func (e *ParkingError) Error() string {
	if e.Err != nil {
		return fmt.Sprintf("parking %s: %s: %v", e.Op, e.Msg, e.Err)
	}
	return fmt.Sprintf("parking %s: %s", e.Op, e.Msg)
}

func (e *ParkingError) Unwrap() error {
	return e.Err
}

// Common error variables
var (
	ErrVehicleAlreadyParked = &ParkingError{Op: "park", Msg: "vehicle already parked"}
	ErrNoAvailableSpots     = &ParkingError{Op: "park", Msg: "no available spots"}
	ErrInvalidTicket        = &ParkingError{Op: "unpark", Msg: "invalid ticket"}
	ErrSpotNotFound         = &ParkingError{Op: "unpark", Msg: "spot not found"}
)