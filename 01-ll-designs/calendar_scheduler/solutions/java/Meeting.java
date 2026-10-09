import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Series/occurrence split: a Meeting is the series metadata (title, people,
 * anchor slot, optional rule, optional room); concrete occurrences are
 * materialized on demand by expanding the rule (see Occurrence).
 *
 * This split is what makes "skip one occurrence" trivial: the skip list holds
 * occurrence START INSTANTS; everything else about the series is untouched.
 *
 * RSVP map: participant id -> RSVP, PENDING by default. The DECLINED-
 * blocks-not policy lives in CalendarService (busy blocks are lifted on
 * decline); the map here is just the state machine.
 */
public class Meeting {
    private final String meetingId;
    private final String title;
    private final User organizer;
    private final Set<User> participants;
    private final TimeSlot anchorSlot;
    private final RecurrenceRule rule;      // null = single (non-recurring) meeting
    private final Room room;                // null = no room booked
    private final Set<ZonedDateTime> skippedOccurrences = new LinkedHashSet<>();
    private final Map<String, RSVP> rsvps = new LinkedHashMap<>();

    public Meeting(String meetingId, String title, User organizer, Set<User> participants,
                   TimeSlot anchorSlot, RecurrenceRule rule, Room room) {
        if (meetingId == null || meetingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Meeting id cannot be null or empty");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Meeting title cannot be null or empty");
        }
        if (organizer == null) {
            throw new IllegalArgumentException("Meeting organizer cannot be null");
        }
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException(
                "Meeting needs at least one participant besides the organizer");
        }
        if (anchorSlot == null) {
            throw new IllegalArgumentException("Meeting anchor slot cannot be null");
        }
        this.meetingId = meetingId.trim();
        this.title = title.trim();
        this.organizer = organizer;
        this.participants = new LinkedHashSet<>(participants);
        this.participants.remove(organizer); // organizer deduped from participant set
        if (this.participants.isEmpty()) {
            throw new IllegalArgumentException(
                "Meeting needs at least one participant besides the organizer");
        }
        this.anchorSlot = anchorSlot;
        this.rule = rule;
        this.room = room;
        for (User participant : this.participants) {
            rsvps.put(participant.getUserId(), RSVP.PENDING);
        }
    }

    public String getMeetingId() {
        return meetingId;
    }

    public String getTitle() {
        return title;
    }

    public User getOrganizer() {
        return organizer;
    }

    public Set<User> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public TimeSlot getAnchorSlot() {
        return anchorSlot;
    }

    public RecurrenceRule getRule() {
        return rule;
    }

    public boolean isRecurring() {
        return rule != null;
    }

    public Room getRoom() {
        return room;
    }

    public Map<String, RSVP> getRsvps() {
        return Collections.unmodifiableMap(rsvps);
    }

    public RSVP rsvpOf(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (!participants.contains(user)) {
            throw new IllegalArgumentException(
                "User " + user.getUserId() + " is not an invited participant of " + meetingId);
        }
        return rsvps.getOrDefault(user.getUserId(), RSVP.PENDING);
    }

    /** RSVP state transition; only invited participants may respond. */
    public void respond(User user, RSVP rsvp) {
        if (rsvp == null) {
            throw new IllegalArgumentException("RSVP cannot be null");
        }
        if (!participants.contains(user)) {
            throw new IllegalArgumentException(
                "User " + user.getUserId() + " is not an invited participant of " + meetingId);
        }
        rsvps.put(user.getUserId(), rsvp);
    }

    public boolean isOccurrenceSkipped(ZonedDateTime occurrenceStart) {
        if (occurrenceStart == null) {
            return false;
        }
        for (ZonedDateTime skipped : skippedOccurrences) {
            if (skipped.equals(occurrenceStart)) {
                return true;
            }
        }
        return false;
    }

    /** Adds an occurrence start to the series' exception (skip) list. */
    public void skipOccurrence(ZonedDateTime occurrenceStart) {
        if (occurrenceStart == null) {
            throw new IllegalArgumentException("Occurrence start cannot be null");
        }
        skippedOccurrences.add(occurrenceStart);
    }

    public Set<ZonedDateTime> getSkippedOccurrences() {
        return Collections.unmodifiableSet(skippedOccurrences);
    }

    /**
     * Materializes occurrences in [from, to] by expanding the rule (single
     * meetings: the anchor only, when inside the window). Skipped occurrences
     * are EXCLUDED - the caller sees what actually takes place.
     */
    public List<TimeSlot> allOccurrences(ZonedDateTime from, ZonedDateTime to) {
        List<TimeSlot> raw = (rule == null)
            ? singleOccurrenceWithin(anchorSlot, from, to)
            : rule.occurrencesBetween(anchorSlot, from, to);
        List<TimeSlot> active = new ArrayList<>();
        for (TimeSlot slot : raw) {
            if (!isOccurrenceSkipped(slot.getStart())) {
                active.add(slot);
            }
        }
        return active;
    }

    /** Convenience: first N occurrences of the series (demo printing). */
    public List<TimeSlot> nextOccurrences(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Occurrence count must be >= 1, got " + count);
        }
        List<TimeSlot> raw = (rule == null)
            ? java.util.Collections.singletonList(anchorSlot)
            : rule.expand(anchorSlot, count);
        List<TimeSlot> active = new ArrayList<>();
        for (TimeSlot slot : raw) {
            if (!isOccurrenceSkipped(slot.getStart())) {
                active.add(slot);
            }
        }
        return active;
    }

    private static List<TimeSlot> singleOccurrenceWithin(TimeSlot anchor,
                                                          ZonedDateTime from, ZonedDateTime to) {
        List<TimeSlot> result = new ArrayList<>();
        if (!anchor.getStart().isBefore(from) && anchor.getStart().isBefore(to)) {
            result.add(anchor);
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(title)
            .append(" (").append(meetingId).append(", organizer ")
            .append(organizer.getName())
            .append(", ").append(participants.size()).append(" participant(s)");
        if (rule != null) {
            sb.append(", RRULE ").append(rule.describe());
        }
        if (room != null) {
            sb.append(", room ").append(room.getName());
        }
        sb.append(")");
        return sb.toString();
    }
}
