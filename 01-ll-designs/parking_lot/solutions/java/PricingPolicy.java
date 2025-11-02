/**
 * Handles pricing calculations for different vehicle types.
 * Uses strategy pattern to allow for different pricing strategies.
 */
public class PricingPolicy {
    private static final double BASE_FEE = 2.0;
    private static final double MOTORCYCLE_HOURLY_RATE = 0.5;
    private static final double CAR_HOURLY_RATE = 1.0;
    private static final double BUS_HOURLY_RATE = 3.0;
    
    /**
     * Calculate parking fee based on vehicle type and duration.
     * @param vehicleType Type of vehicle
     * @param entryTime Entry time in milliseconds
     * @param exitTime Exit time in milliseconds
     * @return Total fee to be charged
     */
    public double calculateFee(VehicleType vehicleType, long entryTime, long exitTime) {
        if (exitTime < entryTime) {
            throw new IllegalArgumentException("Exit time cannot be before entry time");
        }
        
        // Calculate duration in hours (minimum 1 hour)
        double durationHours = Math.max(1.0, Math.ceil((exitTime - entryTime) / (1000.0 * 3600.0)));
        
        double hourlyRate = getHourlyRate(vehicleType);
        return BASE_FEE + (hourlyRate * durationHours);
    }
    
    /**
     * Get hourly rate for a specific vehicle type.
     * @param vehicleType Type of vehicle
     * @return Hourly rate for the vehicle type
     */
    private double getHourlyRate(VehicleType vehicleType) {
        switch (vehicleType) {
            case MOTORCYCLE:
                return MOTORCYCLE_HOURLY_RATE;
            case CAR:
                return CAR_HOURLY_RATE;
            case BUS:
                return BUS_HOURLY_RATE;
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + vehicleType);
        }
    }
    
    /**
     * Get base fee (same for all vehicle types).
     * @return Base parking fee
     */
    public double getBaseFee() {
        return BASE_FEE;
    }
    
    /**
     * Get hourly rate for a vehicle type (public method for display purposes).
     * @param vehicleType Type of vehicle
     * @return Hourly rate
     */
    public double getHourlyRateForVehicle(VehicleType vehicleType) {
        return getHourlyRate(vehicleType);
    }
}