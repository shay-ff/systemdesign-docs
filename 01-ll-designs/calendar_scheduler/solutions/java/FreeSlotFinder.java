import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Find the first N slots where ALL of M users are free" - the merged-interval
 * sweep, which is the answer interviewers want ("merge intervals" is the
 * underlying pattern):
 *
 *   1. Pull each user's busy slots for the requested days.
 *   2. Sort and MERGE overlapping busy intervals into a disjoint set.
 *   3. Walk the requester's 09:00-18:00 local working hours in 30-min steps;
 *      a step whose whole duration avoids every merged interval is free.
 *   4. Collect until N slots or the days run out.
 *
 * Rejected alternative: for each candidate slot, binary-search each user's
 * calendar (O(N x M x log S)) - it re-scans shared busy intervals; the
 * merge-then-sweep does the shared work once.
 *
 * Working hours are a finder-level constant here (per-user working hours are
 * a documented extension question, not built-in scope creep).
 */
public class FreeSlotFinder {
    public static final int WORK_START_HOUR = 9;
    public static final int WORK_END_HOUR = 18;
    public static final int SLOT_MINUTES = 30;

    /**
     * @param users         participants whose calendars must all be free
     * @param calendarsById userId -> Calendar (the busy sources)
     * @param fromDate      first day to consider, in the requester's zone
     * @param requesterZone working hours are defined in THIS zone
     * @param days          how many days to sweep
     * @param slotMinutes   duration of each candidate slot (>= 5, multiple of 5)
     * @param count         how many free slots to return
     */
    public List<TimeSlot> findFreeSlots(List<User> users, java.util.Map<String, Calendar> calendarsById,
                                        LocalDate fromDate, ZoneId requesterZone,
                                        int days, int slotMinutes, int count) {
        if (users == null || users.isEmpty()) {
            throw new IllegalArgumentException("Need at least one user for free-slot search");
        }
        if (fromDate == null) {
            throw new IllegalArgumentException("From date cannot be null");
        }
        if (requesterZone == null) {
            throw new IllegalArgumentException("Requester zone cannot be null");
        }
        if (days < 1) {
            throw new IllegalArgumentException("Days must be >= 1, got " + days);
        }
        if (slotMinutes < 5 || slotMinutes % 5 != 0) {
            throw new IllegalArgumentException(
                "Slot minutes must be a multiple of 5 and >= 5, got " + slotMinutes);
        }
        if (count < 1) {
            throw new IllegalArgumentException("Requested slot count must be >= 1, got " + count);
        }
        for (User user : users) {
            if (!calendarsById.containsKey(user.getUserId())) {
                throw new IllegalArgumentException(
                    "No calendar registered for user " + user.getUserId());
            }
        }

        // 1. Gather + merge busy intervals across all users (UTC instants).
        List<TimeSlot> merged = mergeOverlapping(collectBusy(users, calendarsById, fromDate,
            requesterZone, days, slotMinutes));

        // 2. Sweep working-hour windows day by day in the requester's zone.
        List<TimeSlot> free = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < days && free.size() < count; dayOffset++) {
            LocalDate day = fromDate.plusDays(dayOffset);
            LocalDateTime workStart = day.atTime(WORK_START_HOUR, 0);
            LocalDateTime workEnd = day.atTime(WORK_END_HOUR, 0);
            ZonedDateTime cursor = workStart.atZone(requesterZone);
            ZonedDateTime dayEnd = workEnd.atZone(requesterZone);
            while (free.size() < count && !cursor.plusMinutes(slotMinutes).isAfter(dayEnd)) {
                TimeSlot candidate = new TimeSlot(cursor, cursor.plusMinutes(slotMinutes));
                if (noOverlap(candidate, merged)) {
                    free.add(candidate);
                }
                cursor = cursor.plusMinutes(SLOT_MINUTES);
            }
        }
        return free;
    }

    private static List<TimeSlot> collectBusy(List<User> users,
                                               java.util.Map<String, Calendar> calendarsById,
                                               LocalDate fromDate, ZoneId zone,
                                               int days, int slotMinutes) {
        // Wide window (a slot either side) so zone-shifted busy blocks that
        // bleed into the requester's working hours are not missed.
        ZonedDateTime windowStart = fromDate.atStartOfDay(zone).minusHours(12);
        ZonedDateTime windowEnd = fromDate.plusDays(days).atTime(23, 59).atZone(zone);
        List<TimeSlot> busy = new ArrayList<>();
        for (User user : users) {
            for (TimeSlot slot : calendarsById.get(user.getUserId()).getBusySlots()) {
                if (slot.getEnd().isAfter(windowStart) && slot.getStart().isBefore(windowEnd)) {
                    busy.add(slot);
                }
            }
        }
        return busy;
    }

    /** Sort by start, then fold overlapping/touching intervals together. */
    static List<TimeSlot> mergeOverlapping(List<TimeSlot> slots) {
        if (slots.isEmpty()) {
            return new ArrayList<>();
        }
        List<TimeSlot> sorted = new ArrayList<>(slots);
        Collections.sort(sorted, Comparator.comparing(TimeSlot::getStart));
        List<TimeSlot> merged = new ArrayList<>();
        TimeSlot current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            TimeSlot next = sorted.get(i);
            if (next.getStart().isBefore(current.getEnd())
                || next.getStart().equals(current.getEnd())) {
                // overlapping or touching - extend the current block
                ZonedDateTime end = next.getEnd().isAfter(current.getEnd())
                    ? next.getEnd() : current.getEnd();
                current = new TimeSlot(current.getStart(), end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static boolean noOverlap(TimeSlot candidate, List<TimeSlot> mergedBusy) {
        for (TimeSlot busy : mergedBusy) {
            if (busy.overlaps(candidate)) {
                return false;
            }
            if (!busy.getStart().isBefore(candidate.getEnd())) {
                break; // merged list is sorted and disjoint
            }
        }
        return true;
    }
}
