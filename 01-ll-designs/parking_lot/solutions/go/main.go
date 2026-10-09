package main

import (
	"fmt"
	"time"
)

func main() {
	fmt.Println("=== Parking Lot System Demo ===\n")
	
	// Create a parking lot with 2 levels
	levels := []*ParkingLevel{
		NewParkingLevel(0, 2, 2, 1), // Level 0: 2 motorcycle, 2 compact, 1 large
		NewParkingLevel(1, 1, 2, 1), // Level 1: 1 motorcycle, 2 compact, 1 large
	}
	
	parkingLot := NewParkingLot("CityCenter Mall", levels)
	
	// Show initial availability
	fmt.Println("Initial state:")
	fmt.Println(parkingLot)
	fmt.Println()
	
	// Create some vehicles
	vehicles := []*Vehicle{}
	
	// Create vehicles with error handling
	vehicleData := []struct {
		license string
		vType   VehicleType
	}{
		{"KA01AB1234", VehicleTypeCar},
		{"KA02ZZ9999", VehicleTypeMotorcycle},
		{"BUS777", VehicleTypeBus},
		{"MH12CD5678", VehicleTypeCar},
		{"BIKE123", VehicleTypeMotorcycle},
	}
	
	for _, vd := range vehicleData {
		vehicle, err := NewVehicle(vd.license, vd.vType)
		if err != nil {
			fmt.Printf("Error creating vehicle %s: %v\n", vd.license, err)
			continue
		}
		vehicles = append(vehicles, vehicle)
	}
	
	// Park vehicles
	var tickets []*Ticket
	fmt.Println("Parking vehicles:")
	for _, vehicle := range vehicles {
		ticket, err := parkingLot.ParkVehicle(vehicle)
		if err != nil {
			fmt.Printf("✗ Failed to park %s: %v\n", vehicle, err)
		} else {
			tickets = append(tickets, ticket)
			fmt.Printf("✓ Parked %s -> %s\n", vehicle, ticket.ID)
		}
	}
	
	fmt.Println()
	fmt.Println("After parking:")
	fmt.Println(parkingLot)
	fmt.Println()
	
	// Show active tickets
	fmt.Println("Active tickets:")
	for _, ticket := range parkingLot.GetActiveTickets() {
		fmt.Printf("  %s\n", ticket)
	}
	fmt.Println()
	
	// Simulate some time passing
	fmt.Println("Waiting 2 seconds to simulate parking duration...")
	time.Sleep(2 * time.Second)
	
	// Unpark some vehicles
	fmt.Println("Unparking vehicles:")
	unparkCount := 3
	if len(tickets) < unparkCount {
		unparkCount = len(tickets)
	}
	
	for i := 0; i < unparkCount; i++ {
		ticket := tickets[i]
		fee, err := parkingLot.UnparkVehicle(ticket)
		if err != nil {
			fmt.Printf("✗ Failed to unpark %s: %v\n", ticket.LicensePlate, err)
		} else {
			fmt.Printf("✓ Unparked %s, Fee: $%.2f\n", ticket.LicensePlate, fee)
		}
	}
	
	fmt.Println()
	fmt.Println("Final state:")
	fmt.Println(parkingLot)
	
	// Demonstrate error handling
	fmt.Println()
	fmt.Println("=== Error Handling Demo ===")
	
	// Try to park the same vehicle twice
	if len(vehicles) > 0 {
		duplicateVehicle := vehicles[0]
		_, err := parkingLot.ParkVehicle(duplicateVehicle)
		if err != nil {
			fmt.Printf("Attempting to park duplicate vehicle: Correctly rejected - %v\n", err)
		} else {
			fmt.Println("Attempting to park duplicate vehicle: Unexpectedly allowed")
		}
	}
	
	// Try to unpark with invalid ticket
	invalidTicket := &Ticket{
		ID:           "INVALID-ID",
		LicensePlate: "FAKE123",
		VehicleType:  VehicleTypeCar,
		EntryTime:    time.Now(),
		LevelIndex:   0,
		SpotID:       0,
		SpotType:     SpotTypeCompact,
	}
	_, err := parkingLot.UnparkVehicle(invalidTicket)
	if err != nil {
		fmt.Printf("Attempting to unpark with invalid ticket: Correctly rejected - %v\n", err)
	} else {
		fmt.Println("Attempting to unpark with invalid ticket: Unexpectedly allowed")
	}
	
	// Show pricing information
	fmt.Println()
	fmt.Println("=== Pricing Information ===")
	pricing := NewStandardPricingPolicy()
	fmt.Printf("Base fee: $%.2f\n", pricing.GetBaseFee())
	fmt.Printf("Motorcycle hourly rate: $%.2f\n", pricing.GetHourlyRate(VehicleTypeMotorcycle))
	fmt.Printf("Car hourly rate: $%.2f\n", pricing.GetHourlyRate(VehicleTypeCar))
	fmt.Printf("Bus hourly rate: $%.2f\n", pricing.GetHourlyRate(VehicleTypeBus))
	
	// Demonstrate premium pricing
	fmt.Println()
	fmt.Println("=== Premium Pricing Demo ===")
	premiumPricing := NewPremiumPricingPolicy(1.5) // 50% premium
	parkingLot.SetPricingPolicy(premiumPricing)
	
	// Calculate sample fees
	entryTime := time.Now().Add(-2 * time.Hour) // 2 hours ago
	exitTime := time.Now()
	
	standardFee := NewStandardPricingPolicy().CalculateFee(VehicleTypeCar, entryTime, exitTime)
	premiumFee := premiumPricing.CalculateFee(VehicleTypeCar, entryTime, exitTime)
	
	fmt.Printf("Standard 2-hour car parking fee: $%.2f\n", standardFee)
	fmt.Printf("Premium 2-hour car parking fee: $%.2f\n", premiumFee)
	
	fmt.Println("\n=== Demo Complete ===")
}