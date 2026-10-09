import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The FACADE - the only class the demo talks to. It coordinates the six
 * moving parts behind one API: calendars (per-user busy lists), the meeting
 * registry, the room inventory, the recurrence expansion, the RSVP policy
 * and the notification bus.
 *
 * THE CRUX lives here: scheduleMeeting must reject a slot that overlaps any
 * busy interval of the organizer, EVERY participant, and the room (if one is
 * attached). People and rooms are both "busy interval holders", so one
 * conflict engine (check-then-act) serves both - a parallel room-booking
 * path would duplicate the exact same overlap logic.
 *
 * RSVP policy, stated out loud: DECLINED participants' busy blocks are
 * LIFTED on decline (they said no - their calendar is not held); TENTATIVE
 * still blocks (a pencil-hold beats double-booking someone who might show
 * up). The policy is applied where the busy blocks are placed/lifted, so
 * Calendar itself stays a dumb sorted interval store - one source of truth.
 *
 * Recurring series are expanded LAZILY: busy blocks are placed only for
 * occurrences inside the requested window (bounded expansion window below);
 * an unbounded series is never materialized - the classic trap on this
 * problem.
 */
public class CalendarService {
    /**
     * How far recurring-series busy blocks are placed when scheduling.
     * Bounded on purpose: a WEEKLY-forever series would otherwise loop
     * forever. Expansion for conflict checks uses the same window; anything
     * beyond it is checked when its own booking window arrives.
     */
    static final int RECURRENCE_PLACE_WINDOW_DAYS = 56; // 8 weeks

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Calendar> calendars = new LinkedHashMap<>();
    private final Map<String, Meeting> meetings = new LinkedHashMap<>();
    private final RoomInventory rooms = new RoomInventory();
    private final NotificationService notifier = new NotificationService();
    private final FreeSlotFinder slotFinder = new FreeSlotFinder();
    private int nextMeetingSeq = 1;

    // ------------------------------------------------------------------
    // setup
    // ------------------------------------------------------------------

    /** Registers a user and their calendar (idempotent per user id). */
    public Calendar registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        Calendar existing = calendars.get(user.getUserId());
        if (existing != null) {
            return existing; // re-registering is a no-op, not an error
        }
        users.put(user.getUserId(), user);
        Calendar calendar = new Calendar(user);
        calendars.put(user.getUserId(), calendar);
        return calendar;
    }

    public User findUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User id cannot be null or empty");
        }
        User user = users.get(userId.trim());
        if (user == null) {
            throw new IllegalArgumentException("Unknown user id: " + userId);
        }
        return user;
    }

    /** Observer hookup: listeners subscribe once, every event fans out. */
    public NotificationService notifications() {
        return notifier;
    }

    public RoomInventory roomInventory() {
        return rooms;
    }

    public Meeting findMeeting(String meetingId) {
        if (meetingId == null || meetingId.trim().isEmpty()) {
            throw new IllegalArgumentException("Meeting id cannot be null or empty");
        }
        Meeting meeting = meetings.get(meetingId.trim());
        if (meeting == null) {
            throw new IllegalArgumentException("Unknown meeting id: " + meetingId);
        }
        return meeting;
    }

    public Calendar calendarOf(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        Calendar calendar = calendars.get(user.getUserId());
        if (calendar == null) {
            throw new IllegalArgumentException("No calendar registered for user " + user.getUserId());
        }
        return calendar;
    }

    // ------------------------------------------------------------------
    // THE CRUX: schedule / conflicts
    // ------------------------------------------------------------------

    /**
     * Schedules a meeting (recurring when {@code rule} != null) and, on
     * success, places busy blocks and sends INVITE notifications.
     *
     * Check-then-act, single-threaded demo: the concurrency answer (per-user
     * locks / optimistic versions / DB tstzrange exclusion) is a documented
     * extension, not built-in scope creep.
     *
     * @param participants invited users; the organizer is deduped out
     * @param slot         anchor occurrence slot [start, end), any zone
     * @param rule         null = single meeting; non-null = series
     * @param room         null = no room; non-null joins the conflict check
     * @return the created meeting
     */
    public Meeting scheduleMeeting(String title, User organizer, Set<User> participants,
                                    TimeSlot slot, RecurrenceRule rule, Room room) {
        if (organizer == null) {
            throw new IllegalArgumentException("Organizer cannot be null");
        }
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException(
                "Meeting needs at least one invited participant besides the organizer");
        }
        if (slot == null) {
            throw new IllegalArgumentException("Meeting slot cannot be null");
        }
        requireRegistered(organizer);
        Set<User> invitees = new LinkedHashSet<>(participants);
        invitees.remove(organizer); // organizer deduped from the participant set
        if (invitees.isEmpty()) {
            throw new IllegalArgumentException(
                "Meeting needs at least one invited participant besides the organizer");
        }
        for (User invitee : invitees) {
            requireRegistered(invitee);
        }

        // Bounded window: only occurrences inside it get busy blocks.
        List<TimeSlot> occurrences = occurrencesToPlace(slot, rule);
        for (TimeSlot occurrence : occurrences) {
            String conflict = checkConflicts(organizer, invitees, room, occurrence);
            if (conflict != null) {
                throw new IllegalStateException("Cannot schedule \"" + title + "\" at "
                    + occurrence.formatIn(organizer.getZone()) + ": " + conflict);
            }
        }

        Meeting meeting = new Meeting("m-" + nextMeetingSeq++, title, organizer,
            invitees, slot, rule, room);
        meetings.put(meeting.getMeetingId(), meeting);
        for (TimeSlot occurrence : occurrences) {
            calendarOf(organizer).addBusy(occurrence);
            calendarOf(organizer).addMeetingId(meeting.getMeetingId());
            for (User invitee : invitees) {
                calendarOf(invitee).addBusy(occurrence);
                calendarOf(invitee).addMeetingId(meeting.getMeetingId());
            }
            if (room != null) {
                room.book(occurrence);
            }
        }
        notifier.notify(new Notification(NotificationKind.INVITE, meeting.getMeetingId(),
            "Invited: " + meeting + " — first occurrence "
            + slot.formatIn(organizer.getZone())));
        return meeting;
    }

    /**
     * The one conflict engine for people and rooms. Returns null when the
     * slot is free for everyone, else a human-readable reason naming the
     * FIRST conflict found (organizer first, then participants in invite
     * order, then the room) - a caller-facing rejection message, not an
     * internal error.
     */
    public String checkConflicts(User organizer, Set<User> participants, Room room, TimeSlot slot) {
        Calendar organizerCalendar = calendarOf(organizer);
        for (TimeSlot busy : organizerCalendar.findConflicts(slot)) {
            return "organizer " + organizer.getName() + " is busy ("
                + busy.formatIn(organizer.getZone()) + ")";
        }
        for (User participant : participants) {
            Calendar calendar = calendarOf(participant);
            for (TimeSlot busy : calendar.findConflicts(slot)) {
                return "participant " + participant.getName() + " is busy ("
                    + busy.formatIn(participant.getZone()) + ")";
            }
        }
        if (room != null) {
            for (TimeSlot busy : room.findConflicts(slot)) {
                return "room " + room.getName() + " is already booked ("
                    + busy.formatIn(ZoneId.of("UTC")) + ")";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // RSVP
    // ------------------------------------------------------------------

    /**
     * RSVP state machine. On DECLINE the participant's busy blocks for this
     * meeting are lifted (they said no - the slot is freed for others);
     * ACCEPTED/TENTATIVE keep blocking (TENTATIVE is a pencil-hold).
     */
    public void respondInvite(String meetingId, User user, RSVP rsvp) {
        Meeting meeting = findMeeting(meetingId);
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (rsvp == null) {
            throw new IllegalArgumentException("RSVP cannot be null");
        }
        meeting.respond(user, rsvp); // validates: only invited participants may respond
        if (rsvp == RSVP.DECLINED) {
            liftParticipantBlocks(meeting, user);
        }
        notifier.notify(new Notification(NotificationKind.RSVP, meeting.getMeetingId(),
            user.getName() + " responded " + rsvp + " to \"" + meeting.getTitle() + "\""));
    }

    // ------------------------------------------------------------------
    // cancel / skip occurrence
    // ------------------------------------------------------------------

    /**
     * Cancels the WHOLE series: every busy block (organizer + participants +
     * room, all occurrences) is lifted and CANCELLED notifications go out.
     * Cancelling one occurrence is skipOccurrence - different operation.
     */
    public void cancelMeeting(String meetingId) {
        Meeting meeting = findMeeting(meetingId);
        List<TimeSlot> occurrences = occurrencesToPlace(meeting.getAnchorSlot(), meeting.getRule());
        liftAllBlocks(meeting, occurrences);
        meetings.remove(meeting.getMeetingId());
        notifier.notify(new Notification(NotificationKind.CANCELLED, meeting.getMeetingId(),
            "Cancelled: " + meeting.getTitle() + " (whole series; "
            + occurrences.size() + " occurrence window, "
            + meeting.getParticipants().size() + " participant(s) notified)"));
    }

    /**
     * Single-occurrence exception: adds the occurrence's start instant to the
     * series' skip list and lifts ONLY that occurrence's busy blocks - the
     * rest of the series is untouched. Skipping one != cancelling all.
     *
     * @param occurrenceStart the occurrence start INSTANT (UTC); must be an
     *                        actual occurrence of the series, else rejected
     */
    public void skipOccurrence(String meetingId, ZonedDateTime occurrenceStart) {
        Meeting meeting = findMeeting(meetingId);
        if (occurrenceStart == null) {
            throw new IllegalArgumentException("Occurrence start cannot be null");
        }
        ZonedDateTime startUtc = occurrenceStart.withZoneSameInstant(java.time.ZoneOffset.UTC);
        // The start must belong to the series (skipped starts included), so a
        // typo cannot silently poison the skip list.
        boolean known = false;
        List<TimeSlot> raw = (meeting.getRule() == null)
            ? java.util.Collections.singletonList(meeting.getAnchorSlot())
            : meeting.getRule().expand(meeting.getAnchorSlot(), 64);
        for (TimeSlot occurrence : raw) {
            if (occurrence.getStart().equals(startUtc)) {
                known = true;
                break;
            }
        }
        if (!known) {
            throw new IllegalArgumentException(
                "Instant " + startUtc + " is not an occurrence of meeting " + meetingId);
        }
        if (meeting.isOccurrenceSkipped(startUtc)) {
            return; // already skipped - idempotent
        }
        meeting.skipOccurrence(startUtc);
        liftOccurrenceBlocks(meeting, startUtc);
        notifier.notify(new Notification(NotificationKind.UPDATED, meeting.getMeetingId(),
            "Skipped one occurrence of \"" + meeting.getTitle() + "\" at "
            + startUtc.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            + " UTC (series continues)"));
    }

    // ------------------------------------------------------------------
    // rooms / free slots
    // ------------------------------------------------------------------

    /** Capacity-filtered room search + availability, via the room inventory. */
    public List<Room> searchRooms(int minCapacity, TimeSlot slot) {
        return rooms.search(minCapacity, slot);
    }

    /**
     * Books a room for a slot (a standalone hold, or before attaching to a
     * meeting). Same conflict engine as people; ROOM_BOOKED notification out.
     */
    public void bookRoom(Room room, TimeSlot slot) {
        if (room == null) {
            throw new IllegalArgumentException("Room cannot be null");
        }
        if (slot == null) {
            throw new IllegalArgumentException("Slot cannot be null");
        }
        if (rooms.findById(room.getRoomId()) != room) {
            throw new IllegalArgumentException(
                "Room " + room.getRoomId() + " is not registered with the room inventory");
        }
        if (!room.isFree(slot)) {
            throw new IllegalStateException("Room " + room.getName()
                + " is already booked during " + slot);
        }
        room.book(slot);
        notifier.notify(new Notification(NotificationKind.ROOM_BOOKED, "room-" + room.getRoomId(),
            "Room " + room.getName() + " booked " + slot.formatIn(ZoneId.of("UTC"))));
    }

    /**
     * First N free slots where ALL given users are free, inside the
     * requester's 09:00-18:00 working hours - delegates to FreeSlotFinder
     * (the merged-interval sweep). The facade does not re-implement it.
     */
    public List<TimeSlot> findFreeSlots(List<User> users, LocalDate fromDate,
                                        ZoneId requesterZone, int days,
                                        int slotMinutes, int count) {
        return slotFinder.findFreeSlots(users, calendars, fromDate,
            requesterZone, days, slotMinutes, count);
    }

    // ------------------------------------------------------------------
    // busy-block lifting helpers
    // ------------------------------------------------------------------

    /** Lifts one user's busy blocks for a meeting (all placed occurrences). */
    private void liftParticipantBlocks(Meeting meeting, User user) {
        for (TimeSlot occurrence : occurrencesToPlace(meeting.getAnchorSlot(), meeting.getRule())) {
            calendarOf(user).removeBusy(occurrence);
        }
    }

    /** Lifts EVERYBODY's busy blocks for the given occurrences (cancel). */
    private void liftAllBlocks(Meeting meeting, List<TimeSlot> occurrences) {
        User organizer = meeting.getOrganizer();
        for (TimeSlot occurrence : occurrences) {
            calendarOf(organizer).removeBusy(occurrence);
            calendarOf(organizer).removeMeetingId(meeting.getMeetingId());
            for (User participant : meeting.getParticipants()) {
                calendarOf(participant).removeBusy(occurrence);
                calendarOf(participant).removeMeetingId(meeting.getMeetingId());
            }
            if (meeting.getRoom() != null) {
                meeting.getRoom().release(occurrence);
            }
        }
    }

    /** Lifts only ONE occurrence's blocks for everybody + the room (skip). */
    private void liftOccurrenceBlocks(Meeting meeting, ZonedDateTime startUtc) {
        // Rebuild the exact occurrence slot from the anchor's duration.
        ZonedDateTime anchorStart = meeting.getAnchorSlot().getStart();
        java.time.temporal.ChronoUnit minutes = java.time.temporal.ChronoUnit.MINUTES;
        long duration = minutes.between(anchorStart, meeting.getAnchorSlot().getEnd());
        TimeSlot occurrence = new TimeSlot(startUtc, startUtc.plusMinutes(duration));
        calendarOf(meeting.getOrganizer()).removeBusy(occurrence);
        for (User participant : meeting.getParticipants()) {
            calendarOf(participant).removeBusy(occurrence);
        }
        if (meeting.getRoom() != null) {
            meeting.getRoom().release(occurrence);
        }
    }

    // ------------------------------------------------------------------
    // occurrence expansion helpers
    // ------------------------------------------------------------------

    /**
     * The bounded window of occurrences that carry busy blocks: the anchor
     * for a single meeting, or rule expansion truncated at
     * RECURRENCE_PLACE_WINDOW_DAYS (lazy + bounded - never an unbounded
     * loop, even for COUNT-less, UNTIL-less series).
     */
    private List<TimeSlot> occurrencesToPlace(TimeSlot anchor, RecurrenceRule rule) {
        if (rule == null) {
            List<TimeSlot> single = new ArrayList<>();
            single.add(anchor);
            return single;
        }
        ZonedDateTime from = anchor.getStart();
        ZonedDateTime to = from.plusDays(RECURRENCE_PLACE_WINDOW_DAYS);
        return rule.occurrencesBetween(anchor, from, to);
    }

    private void requireRegistered(User user) {
        if (!calendars.containsKey(user.getUserId())) {
            throw new IllegalArgumentException(
                "User " + user.getUserId() + " (" + user.getName()
                + ") is not registered with the calendar service");
        }
    }
}
