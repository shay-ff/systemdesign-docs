import java.util.*;

/**
 * Main parking lot management system.
 * Coordinates multiple levels and handles vehicle parking/unparking operations.
 */
public class ParkingLot {
    private final String name;
    private final List<ParkingLevel> levels;
    private final PricingPolicy pricingPolicy;
    
    // Maps for quick lookups
    private final Map<String, Ticket> activeTickets; // licensePlate -> ticket
    private final Map<String, String> spotToLicense; // "level-spotId" -> licensePlate
    
    public ParkingLot(String name, List<ParkingLevel> levels) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Parking lot name cannot be null or empty");
        }
        if (levels == null || levels.isEmpty()) {
            throw new IllegalArgumentException("Parking lot must have at least one level");
        }
        
        this.name = name.trim();
        this.levels = new ArrayList<>(levels);
        this.pricingPolicy = new PricingPolicy();
        this.activeTickets = new HashMap<>();
        this.spotToLicense = new HashMap<>();
    }
    
    /**
     * Park a vehicle and return a ticket if successful.
     * @param vehicle Vehicle to park
     * @return Ticket if parking successful, null otherwise
     */
    public Ticket parkVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null");
        }
        
        String licensePlate = vehicle.getLicensePlate();
        
        // Check if vehicle is already parked
        if (activeTickets.containsKey(licensePlate)) {
            System.out.println("Vehicle " + licensePlate + " is already parked!");
            return null;
        }
        
        // Try to find a spot across all levels
        for (ParkingLevel level : levels) {
            int spotIndex = level.findAvailableSpot(vehicle.getVehicleType());
            if (spotIndex != -1) {
                // Allocate the spot
                ParkingSpot spot = level.getSpot(spotIndex);
                spot.occupy(licensePlate);
                
                // Create ticket
                Ticket ticket = new Ticket(
                    generateTicketId(licensePlate, level.getLevelIndex(), spot.getSpotId()),
                    licensePlate,
                    vehicle.getVehicleType(),
                    System.currentTimeMillis(),
                    level.getLevelIndex(),
                    spot.getSpotId(),
                    spot.getSpotType()
                );
                
                // Update tracking maps
                activeTickets.put(licensePlate, ticket);
                spotToLicense.put(getSpotKey(level.getLevelIndex(), spot.getSpotId()), licensePlate);
                
                return ticket;
            }
        }
        
        // No available spots
        return null;
    }
    
    /**
     * Unpark a vehicle and return the fee charged.
     * @param ticket Parking ticket
     * @return Fee charged, or -1 if unparking failed
     */
    public double unparkVehicle(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        
        String licensePlate = ticket.getLicensePlate();
        
        // Verify ticket is valid
        if (!activeTickets.containsKey(licensePlate)) {
            System.out.println("Invalid ticket: " + licensePlate + " not found in active tickets");
            return -1;
        }
        
        Ticket storedTicket = activeTickets.get(licensePlate);
        if (!storedTicket.getTicketId().equals(ticket.getTicketId())) {
            System.out.println("Ticket mismatch for " + licensePlate);
            return -1;
        }
        
        // Find the level and spot
        ParkingLevel level = findLevel(ticket.getLevelIndex());
        if (level == null) {
            System.out.println("Level " + ticket.getLevelIndex() + " not found");
            return -1;
        }
        
        int spotIndex = level.findSpotIndexById(ticket.getSpotId());
        if (spotIndex == -1) {
            System.out.println("Spot " + ticket.getSpotId() + " not found in level " + ticket.getLevelIndex());
            return -1;
        }
        
        ParkingSpot spot = level.getSpot(spotIndex);
        if (!licensePlate.equals(spot.getCurrentVehicleLicense())) {
            System.out.println("Spot occupancy mismatch for " + licensePlate);
            return -1;
        }
        
        // Release the spot
        level.releaseSpot(spotIndex);
        
        // Calculate fee
        long exitTime = System.currentTimeMillis();
        double fee = pricingPolicy.calculateFee(ticket.getVehicleType(), ticket.getEntryTime(), exitTime);
        
        // Clean up tracking maps
        activeTickets.remove(licensePlate);
        spotToLicense.remove(getSpotKey(ticket.getLevelIndex(), ticket.getSpotId()));
        
        return fee;
    }
    
    /**
     * Get availability summary for all levels.
     * @return Formatted string with availability information
     */
    public String getAvailabilitySummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Parking Lot: ").append(name).append("\n");
        
        int totalMotorcycle = 0, totalCompact = 0, totalLarge = 0;
        
        for (ParkingLevel level : levels) {
            int[] availability = level.getAvailability();
            totalMotorcycle += availability[0];
            totalCompact += availability[1];
            totalLarge += availability[2];
            summary.append(level.toString()).append("\n");
        }
        
        summary.append(String.format("Total available: %d motorcycle, %d compact, %d large",
                totalMotorcycle, totalCompact, totalLarge));
        
        return summary.toString();
    }
    
    /**
     * Get list of all active tickets.
     * @return Collection of active tickets
     */
    public Collection<Ticket> getActiveTickets() {
        return new ArrayList<>(activeTickets.values());
    }
    
    /**
     * Check if a vehicle is currently parked.
     * @param licensePlate License plate to check
     * @return true if vehicle is parked, false otherwise
     */
    public boolean isVehicleParked(String licensePlate) {
        return activeTickets.containsKey(licensePlate);
    }
    
    /**
     * Get ticket for a parked vehicle.
     * @param licensePlate License plate
     * @return Ticket if vehicle is parked, null otherwise
     */
    public Ticket getTicketForVehicle(String licensePlate) {
        return activeTickets.get(licensePlate);
    }
    
    public String getName() {
        return name;
    }
    
    public List<ParkingLevel> getLevels() {
        return new ArrayList<>(levels);
    }
    
    /**
     * Generate a unique ticket ID.
     * @param licensePlate Vehicle license plate
     * @param levelIndex Level index
     * @param spotId Spot ID
     * @return Unique ticket ID
     */
    private String generateTicketId(String licensePlate, int levelIndex, int spotId) {
        long timestamp = System.currentTimeMillis();
        return String.format("%s-L%d-S%d-%d", licensePlate, levelIndex, spotId, timestamp);
    }
    
    /**
     * Generate spot key for mapping.
     * @param levelIndex Level index
     * @param spotId Spot ID
     * @return Spot key string
     */
    private String getSpotKey(int levelIndex, int spotId) {
        return levelIndex + "-" + spotId;
    }
    
    /**
     * Find level by index.
     * @param levelIndex Index to find
     * @return ParkingLevel or null if not found
     */
    private ParkingLevel findLevel(int levelIndex) {
        for (ParkingLevel level : levels) {
            if (level.getLevelIndex() == levelIndex) {
                return level;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return getAvailabilitySummary();
    }
}