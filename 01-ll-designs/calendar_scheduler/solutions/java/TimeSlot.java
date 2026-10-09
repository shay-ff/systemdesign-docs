import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable half-open time slot [start, end), ALWAYS stored in UTC.
 *
 * Half-open semantics: a meeting ending 11:00 and another starting 11:00 do
 * NOT overlap (adjacent meetings are allowed). The overlap check uses strict
 * inequality, which is exactly what enforces that boundary.
 */
public final class TimeSlot {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ZonedDateTime start; // normalized to UTC
    private final ZonedDateTime end;   // normalized to UTC

    public TimeSlot(ZonedDateTime start, ZonedDateTime end) {
        if (start == null) {
            throw new IllegalArgumentException("Slot start cannot be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("Slot end cannot be null");
        }
        // Normalize to UTC once, at construction: all math is instant-based.
        this.start = start.withZoneSameInstant(ZoneOffset.UTC);
        this.end = end.withZoneSameInstant(ZoneOffset.UTC);
        if (!this.start.isBefore(this.end)) {
            throw new IllegalArgumentException(
                "Invalid slot: start " + this.start + " must be strictly before end "
                + this.end + " (slots are half-open [start, end))");
        }
    }

    /** True if this slot and the other share at least one instant. O(1). */
    public boolean overlaps(TimeSlot other) {
        Objects.requireNonNull(other, "Cannot check overlap against a null slot");
        // Strict '<' = half-open intervals: touching endpoints are NOT overlaps.
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    public boolean containsInstant(ZonedDateTime t) {
        if (t == null) {
            return false;
        }
        ZonedDateTime utc = t.withZoneSameInstant(ZoneOffset.UTC);
        return !utc.isBefore(start) && utc.isBefore(end);
    }

    public long durationMinutes() {
        return ChronoUnit.MINUTES.between(start, end);
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    /** Same instant, shifted for display only. Never used for comparisons. */
    public ZonedDateTime startIn(ZoneId zone) {
        return start.withZoneSameInstant(zone);
    }

    public ZonedDateTime endIn(ZoneId zone) {
        return end.withZoneSameInstant(zone);
    }

    /** Renders the slot in a viewer's timezone, e.g. "2026-10-05 19:30-20:30 [Asia/Kolkata]". */
    public String formatIn(ZoneId zone) {
        ZoneId target = zone == null ? ZoneOffset.UTC : zone;
        return startIn(target).format(FORMATTER) + "-" + endIn(target).format(FORMATTER)
                + " [" + target.getId() + "]";
    }

    @Override
    public String toString() {
        return formatIn(ZoneOffset.UTC);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TimeSlot other = (TimeSlot) obj;
        return start.equals(other.start) && end.equals(other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
