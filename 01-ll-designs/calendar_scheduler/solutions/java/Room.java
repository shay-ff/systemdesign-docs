import java.util.ArrayList;
import java.util.List;

/**
 * A bookable resource with capacity. A room holds busy intervals exactly like
 * a participant's calendar - the SAME conflict engine serves both, which is
 * the composite-flavoured insight worth stating out loud in the round.
 */
public class Room {
    private final String roomId;
    private final String name;
    private final int capacity;
    private final List<TimeSlot> busySlots = new ArrayList<>();

    public Room(String roomId, String name, int capacity) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room id cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Room name cannot be null or empty");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Room capacity must be >= 1, got " + capacity);
        }
        this.roomId = roomId.trim();
        this.name = name.trim();
        this.capacity = capacity;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    /** All busy slots overlapping the given slot (empty = free). O(S). */
    public List<TimeSlot> findConflicts(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Slot cannot be null");
        }
        List<TimeSlot> conflicts = new ArrayList<>();
        for (TimeSlot busy : busySlots) {
            if (busy.overlaps(slot)) {
                conflicts.add(busy);
            }
        }
        return conflicts;
    }

    public boolean isFree(TimeSlot slot) {
        return findConflicts(slot).isEmpty();
    }

    /** Holds the room for the slot. Caller is expected to check first. */
    public void book(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Slot cannot be null");
        }
        if (!isFree(slot)) {
            throw new IllegalStateException(
                "Room " + name + " is already busy during " + slot);
        }
        busySlots.add(slot);
    }

    /** Lifts one busy block (decline/cancel/skip-occurrence). */
    public boolean release(TimeSlot slot) {
        return busySlots.remove(slot);
    }

    @Override
    public String toString() {
        return name + " (" + roomId + ", seats " + capacity + ")";
    }
}
