import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-store vehicle inventory, indexed by VehicleType for fast browse-by-type.
 * Deliberately does NOT own availability logic — that lives with the
 * reservation log in Store, so there is a single source of truth.
 */
public class VehicleInventory {
    private final Map<VehicleType, List<Vehicle>> byType = new HashMap<>();
    private final Map<String, Vehicle> byId = new HashMap<>();

    public void addVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Cannot add a null vehicle to inventory");
        }
        if (byId.containsKey(vehicle.getVehicleId())) {
            throw new IllegalArgumentException(
                "Vehicle already in inventory: " + vehicle.getVehicleId());
        }
        byId.put(vehicle.getVehicleId(), vehicle);
        byType.computeIfAbsent(vehicle.getVehicleType(), k -> new ArrayList<>()).add(vehicle);
    }

    /** Removes and returns the vehicle, or returns null if not present. */
    public Vehicle removeVehicle(String vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        Vehicle removed = byId.remove(vehicleId);
        if (removed == null) {
            return null;
        }
        List<Vehicle> list = byType.get(removed.getVehicleType());
        if (list != null) {
            list.remove(removed);
            if (list.isEmpty()) {
                byType.remove(removed.getVehicleType());
            }
        }
        return removed;
    }

    /** All vehicles of the given type at this store (unmodifiable view). */
    public List<Vehicle> findByType(VehicleType type) {
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null");
        }
        List<Vehicle> list = byType.get(type);
        return list == null
                ? Collections.<Vehicle>emptyList()
                : Collections.unmodifiableList(list);
    }

    public Vehicle findById(String vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        return byId.get(vehicleId);
    }

    public int totalVehicles() {
        return byId.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Inventory (").append(totalVehicles()).append(" vehicles): ");
        for (Map.Entry<VehicleType, List<Vehicle>> e : byType.entrySet()) {
            sb.append(e.getKey()).append("=").append(e.getValue().size()).append(" ");
        }
        return sb.toString().trim();
    }
}
