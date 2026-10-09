import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a parking ticket issued when a vehicle is parked.
 */
public class Ticket {
    private final String ticketId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final long entryTime;
    private final int levelIndex;
    private final int spotId;
    private final SpotType spotType;
    
    public Ticket(String ticketId, String licensePlate, VehicleType vehicleType,
                  long entryTime, int levelIndex, int spotId, SpotType spotType) {
        this.ticketId = ticketId;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.entryTime = entryTime;
        this.levelIndex = levelIndex;
        this.spotId = spotId;
        this.spotType = spotType;
    }
    
    public String getTicketId() {
        return ticketId;
    }
    
    public String getLicensePlate() {
        return licensePlate;
    }
    
    public VehicleType getVehicleType() {
        return vehicleType;
    }
    
    public long getEntryTime() {
        return entryTime;
    }
    
    public int getLevelIndex() {
        return levelIndex;
    }
    
    public int getSpotId() {
        return spotId;
    }
    
    public SpotType getSpotType() {
        return spotType;
    }
    
    @Override
    public String toString() {
        LocalDateTime entryDateTime = LocalDateTime.ofEpochSecond(entryTime / 1000, 0, 
            java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()));
        String formattedTime = entryDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        return String.format("Ticket %s: %s (%s) at Level %d, Spot %d (entered %s)",
                ticketId, licensePlate, vehicleType, levelIndex, spotId, formattedTime);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Ticket ticket = (Ticket) obj;
        return ticketId.equals(ticket.ticketId);
    }
    
    @Override
    public int hashCode() {
        return ticketId.hashCode();
    }
}