import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One user's calendar: a busy-slot list kept SORTED by start.
 *
 * Conflict check: scan for overlapping busy slots. The sorted list lets a
 * scan stop at the first busy.start >= candidate.end (O(S) worst case,
 * O(k + position) typical, and the honest interview answer; see
 * explanation.md for the binary-search and interval-tree upgrade path).
 *
 * RSVP awareness: this class holds raw busy blocks; the DECLINED-blocks-do-
 * not-count policy is applied by CalendarService BEFORE a busy block is
 * placed/lifted (on decline, the user's block is lifted), so the calendar
 * itself stays a dumb interval store - one source of truth.
 */
public class Calendar {
    private final User user;
    private final List<TimeSlot> busySlots = new ArrayList<>();
    private final List<String> meetingIds = new ArrayList<>();

    public Calendar(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Calendar owner cannot be null");
        }
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    /** All busy slots overlapping the candidate (empty = free). */
    public List<TimeSlot> findConflicts(TimeSlot candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Candidate slot cannot be null");
        }
        List<TimeSlot> conflicts = new ArrayList<>();
        for (TimeSlot busy : busySlots) {
            if (!busy.getStart().isBefore(candidate.getEnd())) {
                break; // sorted by start; nothing later can overlap (half-open)
            }
            if (busy.overlaps(candidate)) {
                conflicts.add(busy);
            }
        }
        return conflicts;
    }

    public boolean isFree(TimeSlot candidate) {
        return findConflicts(candidate).isEmpty();
    }

    /** Adds a busy block, keeping the list sorted by start. */
    public void addBusy(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Busy slot cannot be null");
        }
        int insertAt = busySlots.size();
        for (int i = 0; i < busySlots.size(); i++) {
            if (slot.getStart().isBefore(busySlots.get(i).getStart())) {
                insertAt = i;
                break;
            }
        }
        busySlots.add(insertAt, slot);
    }

    /** Removes one busy block (decline / cancel / skip-occurrence). */
    public boolean removeBusy(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Busy slot cannot be null");
        }
        return busySlots.remove(slot);
    }

    public void addMeetingId(String meetingId) {
        if (meetingId == null || meetingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Meeting id cannot be null or empty");
        }
        if (!meetingIds.contains(meetingId)) {
            meetingIds.add(meetingId);
        }
    }

    public void removeMeetingId(String meetingId) {
        meetingIds.remove(meetingId);
    }

    public List<TimeSlot> getBusySlots() {
        return Collections.unmodifiableList(busySlots);
    }

    /** Defensive copy sorted chronologically (busy list is already sorted). */
    public List<TimeSlot> busySlotsSnapshot() {
        return new ArrayList<>(busySlots);
    }

    public static final Comparator<TimeSlot> BY_START =
        Comparator.comparing(TimeSlot::getStart);
}
