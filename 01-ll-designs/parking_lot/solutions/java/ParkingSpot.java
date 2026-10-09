/**
 * Represents a single parking spot with its properties and state.
 */
public class ParkingSpot {
    private final int spotId;
    private final SpotType spotType;
    private boolean isOccupied;
    private String currentVehicleLicense;
    
    public ParkingSpot(int spotId, SpotType spotType) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.isOccupied = false;
        this.currentVehicleLicense = null;
    }
    
    public int getSpotId() {
        return spotId;
    }
    
    public SpotType getSpotType() {
        return spotType;
    }
    
    public boolean isOccupied() {
        return isOccupied;
    }
    
    public String getCurrentVehicleLicense() {
        return currentVehicleLicense;
    }
    
    /**
     * Occupy this spot with a vehicle.
     * @param licensePlate The license plate of the vehicle
     * @throws IllegalStateException if the spot is already occupied
     */
    public void occupy(String licensePlate) {
        if (isOccupied) {
            throw new IllegalStateException("Spot " + spotId + " is already occupied");
        }
        this.isOccupied = true;
        this.currentVehicleLicense = licensePlate;
    }
    
    /**
     * Vacate this spot, making it available.
     * @throws IllegalStateException if the spot is not occupied
     */
    public void vacate() {
        if (!isOccupied) {
            throw new IllegalStateException("Spot " + spotId + " is not occupied");
        }
        this.isOccupied = false;
        this.currentVehicleLicense = null;
    }
    
    @Override
    public String toString() {
        String status = isOccupied ? "Occupied by " + currentVehicleLicense : "Available";
        return String.format("Spot %d (%s): %s", spotId, spotType, status);
    }
}