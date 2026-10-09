import java.util.*;

/**
 * Represents a single level/floor of the parking lot.
 * Manages spots and provides efficient allocation/deallocation.
 */
public class ParkingLevel {
    private final int levelIndex;
    private final List<ParkingSpot> spots;
    
    // Queues for efficient spot allocation
    private final ArrayDeque<Integer> freeMotorcycleSpots;
    private final ArrayDeque<Integer> freeCompactSpots;
    private final ArrayDeque<Integer> freeLargeSpots;
    
    public ParkingLevel(int levelIndex, int motorcycleSpots, int compactSpots, int largeSpots) {
        this.levelIndex = levelIndex;
        this.spots = new ArrayList<>();
        this.freeMotorcycleSpots = new ArrayDeque<>();
        this.freeCompactSpots = new ArrayDeque<>();
        this.freeLargeSpots = new ArrayDeque<>();
        
        initializeSpots(motorcycleSpots, compactSpots, largeSpots);
    }
    
    /**
     * Initialize all parking spots and populate free queues.
     */
    private void initializeSpots(int motorcycleSpots, int compactSpots, int largeSpots) {
        int spotId = 0;
        
        // Create motorcycle spots
        for (int i = 0; i < motorcycleSpots; i++) {
            spots.add(new ParkingSpot(spotId, SpotType.MOTORCYCLE));
            freeMotorcycleSpots.offer(spotId);
            spotId++;
        }
        
        // Create compact spots
        for (int i = 0; i < compactSpots; i++) {
            spots.add(new ParkingSpot(spotId, SpotType.COMPACT));
            freeCompactSpots.offer(spotId);
            spotId++;
        }
        
        // Create large spots
        for (int i = 0; i < largeSpots; i++) {
            spots.add(new ParkingSpot(spotId, SpotType.LARGE));
            freeLargeSpots.offer(spotId);
            spotId++;
        }
    }
    
    /**
     * Find and allocate an available spot for the given vehicle type.
     * @param vehicleType Type of vehicle to park
     * @return Index of allocated spot, or -1 if no spot available
     */
    public int findAvailableSpot(VehicleType vehicleType) {
        switch (vehicleType) {
            case MOTORCYCLE:
                // Motorcycles can use any spot type (prefer smaller first)
                if (!freeMotorcycleSpots.isEmpty()) {
                    return freeMotorcycleSpots.poll();
                } else if (!freeCompactSpots.isEmpty()) {
                    return freeCompactSpots.poll();
                } else if (!freeLargeSpots.isEmpty()) {
                    return freeLargeSpots.poll();
                }
                break;
                
            case CAR:
                // Cars can use compact or large spots
                if (!freeCompactSpots.isEmpty()) {
                    return freeCompactSpots.poll();
                } else if (!freeLargeSpots.isEmpty()) {
                    return freeLargeSpots.poll();
                }
                break;
                
            case BUS:
                // Buses can only use large spots
                if (!freeLargeSpots.isEmpty()) {
                    return freeLargeSpots.poll();
                }
                break;
        }
        
        return -1; // No available spot
    }
    
    /**
     * Release a spot and add it back to the appropriate free queue.
     * @param spotIndex Index of the spot to release
     */
    public void releaseSpot(int spotIndex) {
        if (spotIndex < 0 || spotIndex >= spots.size()) {
            throw new IllegalArgumentException("Invalid spot index: " + spotIndex);
        }
        
        ParkingSpot spot = spots.get(spotIndex);
        spot.vacate();
        
        // Add back to appropriate free queue
        switch (spot.getSpotType()) {
            case MOTORCYCLE:
                freeMotorcycleSpots.offer(spotIndex);
                break;
            case COMPACT:
                freeCompactSpots.offer(spotIndex);
                break;
            case LARGE:
                freeLargeSpots.offer(spotIndex);
                break;
        }
    }
    
    /**
     * Get the parking spot at the given index.
     * @param spotIndex Index of the spot
     * @return ParkingSpot object
     */
    public ParkingSpot getSpot(int spotIndex) {
        if (spotIndex < 0 || spotIndex >= spots.size()) {
            throw new IllegalArgumentException("Invalid spot index: " + spotIndex);
        }
        return spots.get(spotIndex);
    }
    
    /**
     * Find spot index by spot ID.
     * @param spotId ID of the spot to find
     * @return Index of the spot, or -1 if not found
     */
    public int findSpotIndexById(int spotId) {
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).getSpotId() == spotId) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Get current availability count for each spot type.
     * @return Array with [motorcycle, compact, large] availability counts
     */
    public int[] getAvailability() {
        return new int[]{
            freeMotorcycleSpots.size(),
            freeCompactSpots.size(),
            freeLargeSpots.size()
        };
    }
    
    /**
     * Get total number of spots in this level.
     * @return Total spot count
     */
    public int getTotalSpots() {
        return spots.size();
    }
    
    /**
     * Get number of occupied spots in this level.
     * @return Occupied spot count
     */
    public int getOccupiedSpots() {
        return (int) spots.stream().mapToLong(spot -> spot.isOccupied() ? 1 : 0).sum();
    }
    
    public int getLevelIndex() {
        return levelIndex;
    }
    
    @Override
    public String toString() {
        int[] availability = getAvailability();
        int occupied = getOccupiedSpots();
        int total = getTotalSpots();
        
        return String.format("Level %d: %d/%d/%d available (motorcycle/compact/large), %d/%d occupied",
                levelIndex, availability[0], availability[1], availability[2], occupied, total);
    }
}