import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A rental store in one city. Composes its own VehicleInventory and owns the
 * reservation log for its vehicles. Availability = "no ACTIVE reservation on
 * this vehicle overlaps the requested window" — kept here so there is a single
 * source of truth for the overlap rule.
 */
public class Store {
    private final String storeId;
    private final Location location;
    private final VehicleInventory inventory;
    private final List<Reservation> reservations;

    public Store(String storeId, Location location) {
        if (storeId == null || storeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Store id cannot be null or empty");
        }
        this.storeId = storeId.trim();
        this.location = Objects.requireNonNull(location, "Store location cannot be null");
        this.inventory = new VehicleInventory();
        this.reservations = new ArrayList<>();
    }

    public String getStoreId() {
        return storeId;
    }

    public Location getLocation() {
        return location;
    }

    public VehicleInventory getInventory() {
        return inventory;
    }

    public List<Reservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }

    void addReservation(Reservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("Cannot add a null reservation to a store");
        }
        reservations.add(reservation);
    }

    /**
     * THE CRUX: is the vehicle free for the whole window?
     * O(R) scan over the vehicle's active reservations. Upgrade paths
     * (sorted list + binary search, interval tree) are discussed in
     * explanation.md — this scan is the right choice at interview scale.
     */
    public boolean isAvailable(Vehicle vehicle, Interval window) {
        return findConflictingReservation(vehicle, window) == null;
    }

    /** Returns the first conflicting active reservation, or null if the vehicle is free. */
    public Reservation findConflictingReservation(Vehicle vehicle, Interval window) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null");
        Objects.requireNonNull(window, "Rental window cannot be null");
        for (Reservation r : reservations) {
            if (!r.isActive()) {
                continue; // COMPLETED / CANCELLED do not hold the vehicle
            }
            if (r.getVehicle().getVehicleId().equals(vehicle.getVehicleId())
                    && r.getRentalWindow().overlaps(window)) {
                return r;
            }
        }
        return null;
    }

    /** Free vehicles of the given type for the whole window. */
    public List<Vehicle> searchAvailable(VehicleType type, Interval window) {
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null");
        }
        Objects.requireNonNull(window, "Rental window cannot be null");
        List<Vehicle> available = new ArrayList<>();
        for (Vehicle v : inventory.findByType(type)) {
            if (isAvailable(v, window)) {
                available.add(v);
            }
        }
        return available;
    }

    @Override
    public String toString() {
        return "Store " + storeId + " [" + location + "] — " + inventory;
    }
}
