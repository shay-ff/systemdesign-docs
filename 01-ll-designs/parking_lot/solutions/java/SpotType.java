/**
 * Enumeration for different types of parking spots.
 */
public enum SpotType {
    MOTORCYCLE("Motorcycle"),
    COMPACT("Compact"),
    LARGE("Large");
    
    private final String displayName;
    
    SpotType(String displayName) {
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