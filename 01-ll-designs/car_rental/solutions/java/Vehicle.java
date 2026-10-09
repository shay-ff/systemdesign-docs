/**
 * Abstract base for all rentable vehicles.
 *
 * Services depend only on this abstraction (and VehicleType), so new variants
 * such as Bike or Truck were added with ZERO changes to BookingService,
 * Store, or VehicleInventory — the Open/Closed Principle payoff.
 */
public abstract class Vehicle {
    private final String vehicleId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final double perDayRate;
    private final int dailyKmAllowance;

    protected Vehicle(String vehicleId, String licensePlate, VehicleType vehicleType,
                       double perDayRate, int dailyKmAllowance) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            throw new IllegalArgumentException("Vehicle id cannot be null or empty");
        }
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty");
        }
        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null");
        }
        if (perDayRate <= 0) {
            throw new IllegalArgumentException(
                "Per-day rate must be positive, got " + perDayRate);
        }
        if (dailyKmAllowance <= 0) {
            throw new IllegalArgumentException(
                "Daily km allowance must be positive, got " + dailyKmAllowance);
        }
        this.vehicleId = vehicleId.trim();
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = vehicleType;
        this.perDayRate = perDayRate;
        this.dailyKmAllowance = dailyKmAllowance;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public double getPerDayRate() {
        return perDayRate;
    }

    public int getDailyKmAllowance() {
        return dailyKmAllowance;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + vehicleId + ", plate " + licensePlate
                + ", " + vehicleType + ", rate INR " + String.format("%.0f", perDayRate)
                + "/day, allowance " + dailyKmAllowance + "km/day]";
    }
}
