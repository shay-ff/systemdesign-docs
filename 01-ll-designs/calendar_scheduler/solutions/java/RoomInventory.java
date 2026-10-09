import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Room registry + capacity-filtered availability search. Rooms join the same
 * conflict engine as people: search(minCapacity, slot) returns rooms whose
 * capacity fits AND whose busy list has no overlap with the slot.
 */
public class RoomInventory {
    private final Map<String, Room> rooms = new LinkedHashMap<>();

    public void addRoom(Room room) {
        if (room == null) {
            throw new IllegalArgumentException("Room cannot be null");
        }
        if (rooms.containsKey(room.getRoomId())) {
            throw new IllegalArgumentException("Room id already exists: " + room.getRoomId());
        }
        rooms.put(room.getRoomId(), room);
    }

    public Room findById(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room id cannot be null or empty");
        }
        return rooms.get(roomId.trim());
    }

    /**
     * All rooms with capacity >= minCapacity that are free during the slot,
     * in registration order.
     */
    public List<Room> search(int minCapacity, TimeSlot slot) {
        if (minCapacity < 1) {
            throw new IllegalArgumentException("Minimum capacity must be >= 1, got " + minCapacity);
        }
        if (slot == null) {
            throw new IllegalArgumentException("Slot cannot be null");
        }
        List<Room> matches = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (room.getCapacity() >= minCapacity && room.isFree(slot)) {
                matches.add(room);
            }
        }
        return matches;
    }

    public List<Room> allRooms() {
        return new ArrayList<>(rooms.values());
    }
}
