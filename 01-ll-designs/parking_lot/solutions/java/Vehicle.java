/**
 * Represents a vehicle with license plate and type.
 */
public class Vehicle {
    private final String licensePlate;
    private final VehicleType vehicleType;
    
    public Vehicle(String licensePlate, VehicleType vehicleType) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate cannot be null or empty");
        }
        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null");
        }
        
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = vehicleType;
    }
    
    public String getLicensePlate() {
        return licensePlate;
    }
    
    public VehicleType getVehicleType() {
        return vehicleType;
    }
    
    @Override
    public String toString() {
        return licensePlate + " (" + vehicleType + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Vehicle vehicle = (Vehicle) obj;
        return licensePlate.equals(vehicle.licensePlate);
    }
    
    @Override
    public int hashCode() {
        return licensePlate.hashCode();
    }
}