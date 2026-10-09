/**
 * Enumeration for different types of vehicles that can be parked.
 */
public enum VehicleType {
    MOTORCYCLE("Motorcycle"),
    CAR("Car"),
    BUS("Bus");
    
    private final String displayName;
    
    VehicleType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}