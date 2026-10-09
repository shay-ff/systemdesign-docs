import java.time.ZonedDateTime;
import java.util.List;

/**
 * RRULE-lite recurrence strategy: expands an anchor slot into concrete
 * occurrences LAZILY, only within a bounded window — an unbounded series is
 * never materialized (the classic trap on this problem).
 *
 * Full RFC 5545 RRULE (BYDAY, BYSETPOS, RDATE/EXDATE, ...) is explicitly out
 * of scope for this design: each grammar subset becomes one more
 * RecurrenceRule implementation behind this same contract.
 */
public interface RecurrenceRule {
    RecurrenceType getType();

    /** Step size: every N days / every N weeks. */
    int getInterval();

    /**
     * All occurrences of the series that fall in [from, to], starting from the
     * anchor slot. Bounded by count and/or the until date when present.
     */
    List<TimeSlot> occurrencesBetween(TimeSlot anchor, ZonedDateTime from, ZonedDateTime to);

    /** Convenience expansion for the first {@code count} occurrences (demo printing). */
    List<TimeSlot> expand(TimeSlot anchor, int count);

    /** Start instant of the occurrence strictly after the given instant, or null. */
    ZonedDateTime nextStart(TimeSlot anchor, ZonedDateTime after);

    /** Human-readable rule description, e.g. "WEEKLY;INTERVAL=1;COUNT=8". */
    String describe();
}
