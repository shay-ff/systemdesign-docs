import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A rental window [start, end) — the conflict-detection core of the system.
 *
 * Convention: start = pickup day, end = drop-off day. The vehicle is held from
 * start (00:00) up to but NOT including end, i.e. it is free again on the end
 * date itself. days() = end - start (billed like 24h periods: pickup Oct 2,
 * drop-off Oct 4 = 2 rental days).
 *
 * The overlap rule uses STRICT inequality, so touching windows are allowed:
 * [Oct 1, Oct 3) and [Oct 3, Oct 5) do NOT overlap — a vehicle dropped off on
 * Oct 3 can be picked up again the same day. Identical or partially shared
 * windows DO overlap and are rejected.
 */
public final class Interval {
    private final LocalDate start;
    private final LocalDate end;

    public Interval(LocalDate start, LocalDate end) {
        if (start == null) {
            throw new IllegalArgumentException("Interval start date cannot be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("Interval end date cannot be null");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                "Invalid interval: start " + start + " must be strictly before end " + end
                + " (end is the drop-off day; a rental must span at least one day)");
        }
        this.start = start;
        this.end = end;
    }

    /** True if this window and the other hold the vehicle on at least one shared day. O(1). */
    public boolean overlaps(Interval other) {
        Objects.requireNonNull(other, "Cannot check overlap against a null interval");
        // Half-open [start, end): strict '<' makes touching windows non-overlapping.
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    /** Number of rental days: pickup Oct 2, drop-off Oct 4 = 2 days. */
    public long days() {
        return ChronoUnit.DAYS.between(start, end);
    }

    /** True on any day the vehicle is held (start inclusive, end exclusive). */
    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(start) && date.isBefore(end);
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "pickup " + start + ", drop-off " + end + " (" + days() + " day"
                + (days() == 1 ? "" : "s") + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Interval other = (Interval) obj;
        return start.equals(other.start) && end.equals(other.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
