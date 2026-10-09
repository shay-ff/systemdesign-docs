import java.time.ZoneId;
import java.util.Objects;

/** A calendar user with a home timezone (UTC storage, local display). */
public final class User {
    private final String userId;
    private final String name;
    private final ZoneId zone;

    public User(String userId, String name, ZoneId zone) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User id cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty");
        }
        this.userId = userId.trim();
        this.name = name.trim();
        this.zone = zone == null ? ZoneId.of("UTC") : zone;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public ZoneId getZone() {
        return zone;
    }

    @Override
    public String toString() {
        return name + " (" + userId + ", " + zone.getId() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return userId.equals(((User) obj).userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
