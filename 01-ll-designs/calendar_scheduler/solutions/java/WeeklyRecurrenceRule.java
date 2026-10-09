import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy: WEEKLY every N weeks, bounded by a count and/or an until date
 * (RRULE-lite: FREQ=WEEKLY;INTERVAL=n;COUNT=k or UNTIL=date).
 *
 * Same anchor-zone expansion contract as DailyRecurrenceRule: "weekly Monday
 * 09:30 IST standup" stays at 09:30 IST in the anchor zone across the whole
 * series, and each occurrence is normalized to UTC inside TimeSlot.
 */
public class WeeklyRecurrenceRule implements RecurrenceRule {
    private final int interval;
    private final Integer count;
    private final LocalDate until;

    public WeeklyRecurrenceRule() {
        this(1, null, null);
    }

    public WeeklyRecurrenceRule(int interval, Integer count, LocalDate until) {
        if (interval < 1) {
            throw new IllegalArgumentException("WEEKLY interval must be >= 1, got " + interval);
        }
        if (count != null && count < 1) {
            throw new IllegalArgumentException("WEEKLY count must be >= 1, got " + count);
        }
        this.interval = interval;
        this.count = count;
        this.until = until;
    }

    @Override
    public RecurrenceType getType() {
        return RecurrenceType.WEEKLY;
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
                break;
            }
            if (until != null && candidate.toLocalDate().isAfter(until)) {
                break;
            }
            ZonedDateTime candidateEnd = anchor.getEnd().plusWeeks(
                (long) (occurrenceIndex - 1) * interval);
            TimeSlot slot = new TimeSlot(candidate, candidateEnd);
            if (!slot.getStart().isBefore(from)) {
                if (!slot.getStart().isBefore(to)) {
                    break; // past the window - starts only increase from here
                }
                occurrences.add(slot);
            }
            candidate = candidate.plusWeeks(interval);
            occurrenceIndex++;
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
            ZonedDateTime end = anchor.getEnd().plusWeeks((long) i * interval);
            TimeSlot slot = new TimeSlot(start, end);
            if (until != null && start.toLocalDate().isAfter(until)) {
                break;
            }
            occurrences.add(slot);
            start = start.plusWeeks(interval);
        }
        return occurrences;
    }

    @Override
    public ZonedDateTime nextStart(TimeSlot anchor, ZonedDateTime after) {
        if (anchor == null) {
            throw new IllegalArgumentException("Anchor slot cannot be null");
        }
        if (after == null) {
            return anchor.getStart();
        }
        ZonedDateTime candidate = anchor.getStart();
        int occurrenceIndex = 1;
        while (!candidate.isAfter(after)) {
            if (count != null && occurrenceIndex >= count) {
                return null;
            }
            candidate = candidate.plusWeeks(interval);
            occurrenceIndex++;
            if (until != null && candidate.toLocalDate().isAfter(until)) {
                return null;
            }
        }
        return candidate;
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("WEEKLY;INTERVAL=").append(interval);
        if (count != null) {
            sb.append(";COUNT=").append(count);
        }
        if (until != null) {
            sb.append(";UNTIL=").append(until);
        }
        return sb.toString();
    }
}
