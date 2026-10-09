package main

import (
	"math"
	"time"
)

// PricingPolicy interface for different pricing strategies
type PricingPolicy interface {
	CalculateFee(vehicleType VehicleType, entryTime, exitTime time.Time) float64
	GetBaseFee() float64
	GetHourlyRate(vehicleType VehicleType) float64
}

// StandardPricingPolicy implements the standard pricing strategy
type StandardPricingPolicy struct {
	BaseFee     float64
	HourlyRates map[VehicleType]float64
}

// NewStandardPricingPolicy creates a new standard pricing policy
func NewStandardPricingPolicy() *StandardPricingPolicy {
	return &StandardPricingPolicy{
		BaseFee: 2.0,
		HourlyRates: map[VehicleType]float64{
			VehicleTypeMotorcycle: 0.5,
			VehicleTypeCar:        1.0,
			VehicleTypeBus:        3.0,
		},
	}
}

// CalculateFee calculates the parking fee based on vehicle type and duration
func (spp *StandardPricingPolicy) CalculateFee(vehicleType VehicleType, entryTime, exitTime time.Time) float64 {
	if exitTime.Before(entryTime) {
		return 0 // Invalid time range
	}
	
	// Calculate duration in hours (minimum 1 hour)
	duration := exitTime.Sub(entryTime)
	durationHours := math.Max(1.0, math.Ceil(duration.Hours()))
	
	hourlyRate := spp.GetHourlyRate(vehicleType)
	return spp.BaseFee + (hourlyRate * durationHours)
}

// GetBaseFee returns the base fee
func (spp *StandardPricingPolicy) GetBaseFee() float64 {
	return spp.BaseFee
}

// GetHourlyRate returns the hourly rate for a vehicle type
func (spp *StandardPricingPolicy) GetHourlyRate(vehicleType VehicleType) float64 {
	if rate, exists := spp.HourlyRates[vehicleType]; exists {
		return rate
	}
	return 1.0 // Default rate
}

// PremiumPricingPolicy implements a premium pricing strategy (example of extensibility)
type PremiumPricingPolicy struct {
	*StandardPricingPolicy
	PremiumMultiplier float64
}

// NewPremiumPricingPolicy creates a new premium pricing policy
func NewPremiumPricingPolicy(multiplier float64) *PremiumPricingPolicy {
	return &PremiumPricingPolicy{
		StandardPricingPolicy: NewStandardPricingPolicy(),
		PremiumMultiplier:     multiplier,
	}
}

// CalculateFee calculates the premium parking fee
func (ppp *PremiumPricingPolicy) CalculateFee(vehicleType VehicleType, entryTime, exitTime time.Time) float64 {
	baseFee := ppp.StandardPricingPolicy.CalculateFee(vehicleType, entryTime, exitTime)
	return baseFee * ppp.PremiumMultiplier
}