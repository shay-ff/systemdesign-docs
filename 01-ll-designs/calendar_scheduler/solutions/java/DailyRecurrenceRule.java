import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy: DAILY every N days, bounded by a count and/or an until date
 * (RRULE-lite: FREQ=DAILY;INTERVAL=n;COUNT=k or UNTIL=date).
 *
 * Expansion steps the ANCHOR slot's start in the anchor's own zone (so a
 * "daily 09:00 IST" series stays at 09:00 IST), then converts each occurrence
 * back to UTC - the anchor-zone approach that keeps DST-correct local times.
 */
public class DailyRecurrenceRule implements RecurrenceRule {
    private final int interval;
    private final Integer count;  // null = unbounded (use window/count explicitly)
    private final LocalDate until; // null = no until bound

    public DailyRecurrenceRule() {
        this(1, null, null);
    }

    public DailyRecurrenceRule(int interval, Integer count, LocalDate until) {
        if (interval < 1) {
            throw new IllegalArgumentException("DAILY interval must be >= 1, got " + interval);
        }
        if (count != null && count < 1) {
            throw new IllegalArgumentException("DAILY count must be >= 1, got " + count);
        }
        this.interval = interval;
        this.count = count;
        this.until = until;
    }

    @Override
    public RecurrenceType getType() {
        return RecurrenceType.DAILY;
    }

    @Override
    public int getInterval() {
        return interval;
    }

    @Override
    public List<TimeSlot> occurrencesBetween(TimeSlot anchor, ZonedDateTime from, ZonedDateTime to) {
        if (anchor == null) {
            throw new IllegalArgumentException("Anchor slot cannot be null");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException(
                "Window must satisfy from < to (UTC instants), got " + from + " .. " + to);
        }
        List<TimeSlot> occurrences = new ArrayList<>();
        ZonedDateTime candidate = anchor.getStart();
        int occurrenceIndex = 1;
        while (true) {
            if (count != null && occurrenceIndex > count) {
                break; // series count exhausted
            }
            if (until != null && isAfterUntil(candidate, until)) {
                break; // series until date passed
            }
            ZonedDateTime candidateEnd = anchor.getEnd().plusDays(
                (long) (occurrenceIndex - 1) * interval);
            TimeSlot slot = new TimeSlot(candidate, candidateEnd);
            if (!slot.getStart().isBefore(from)) {
                if (!slot.getStart().isBefore(to)) {
                    break; // past the window and monotonically increasing - done
                }
                occurrences.add(slot);
            }
            candidate = candidate.plusDays(interval);
            occurrenceIndex++;
            if (candidate.isAfter(to) && (count == null || occurrenceIndex > count)) {
                // safety: never spin past both the window and every bound
                if (candidate.isAfter(to)) {
                    break;
                }
            }
        }
        return occurrences;
    }

    @Override
    public List<TimeSlot> expand(TimeSlot anchor, int requestedCount) {
        if (anchor == null) {
            throw new IllegalArgumentException("Anchor slot cannot be null");
        }
        if (requestedCount < 1) {
            throw new IllegalArgumentException("Expansion count must be >= 1, got " + requestedCount);
        }
        List<TimeSlot> occurrences = new ArrayList<>();
        ZonedDateTime start = anchor.getStart();
        for (int i = 0; i < requestedCount; i++) {
            if (count != null && i >= count) {
                break;
            }
            ZonedDateTime end = anchor.getEnd().plusDays((long) i * interval);
            TimeSlot slot = new TimeSlot(start, end);
            if (until != null && isAfterUntil(slot.getStart(), until)) {
                break;
            }
            occurrences.add(slot);
            start = start.plusDays(interval);
        }
        return occurrences;
    }

    @Override
    public ZonedDateTime nextStart(TimeSlot anchor, ZonedDateTime after) {
        if (anchor == null) {
            throw new IllegalArgumentException("Anchor slot cannot be null");
        }
        if (after == null) {
            return anchor.getStart(); // nothing given - the anchor IS next
        }
        ZonedDateTime candidate = anchor.getStart();
        int occurrenceIndex = 1;
        while (!candidate.isAfter(after)) {
            if (count != null && occurrenceIndex >= count) {
                return null; // series exhausted before reaching 'after'
            }
            candidate = candidate.plusDays(interval);
            occurrenceIndex++;
            if (until != null && isAfterUntil(candidate, until)) {
                return null;
            }
        }
        return candidate;
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("DAILY;INTERVAL=").append(interval);
        if (count != null) {
            sb.append(";COUNT=").append(count);
        }
        if (until != null) {
            sb.append(";UNTIL=").append(until);
        }
        return sb.toString();
    }

    private static boolean isAfterUntil(ZonedDateTime utcStart, LocalDate untilDate) {
        // until bounds the LOCAL DATE of the occurrence in the anchor zone.
        return utcStart.toLocalDate().isAfter(untilDate);
    }
}
