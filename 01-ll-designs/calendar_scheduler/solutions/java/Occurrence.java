import java.time.ZoneId;

/**
 * One concrete materialized instance of a (possibly recurring) meeting.
 *
 * Occurrences are produced on demand by expanding the series' RecurrenceRule
 * inside a bounded window - the design never stores an unbounded series.
 * Skip-one-occurrence exceptions are tracked on the Meeting as start
 * instants, so an Occurrence can report whether it is skipped.
 */
public final class Occurrence {
    private final Meeting meeting;
    private final TimeSlot slot;

    public Occurrence(Meeting meeting, TimeSlot slot) {
        if (meeting == null) {
            throw new IllegalArgumentException("Occurrence meeting cannot be null");
        }
        if (slot == null) {
            throw new IllegalArgumentException("Occurrence slot cannot be null");
        }
        this.meeting = meeting;
        this.slot = slot;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    /** True if this occurrence is on the series' skip list (exception). */
    public boolean isSkipped() {
        return meeting.isOccurrenceSkipped(slot.getStart());
    }

    public String formatIn(ZoneId zone) {
        return meeting.getTitle() + " @" + slot.formatIn(zone)
            + (isSkipped() ? " [SKIPPED]" : "");
    }
}
