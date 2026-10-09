import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calendar / Meeting Scheduler demo.
 *
 * All instants are FIXED dates (2026-10-05 onward, a Monday) so the log is
 * byte-identical on every rerun - no wall clock, no sleeps. The organizer
 * lives in IST (Asia/Kolkata) and the key participant in PST
 * (America/Los_Angeles): the same UTC slots are printed in both zones to
 * make the "store UTC, display local" rule visible on the log.
 */
public class CalendarDemo {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final ZoneId PST = ZoneId.of("America/Los_Angeles");

    public static void main(String[] args) {
        System.out.println("=== Calendar / Meeting Scheduler — Low Level Design Demo ===\n");

        CalendarService service = new CalendarService();
        service.notifications().subscribe(new ConsoleNotificationListener());

        // Three users in two zones; Ananya (IST) organizes most meetings,
        // Ryan (PST) is the cross-zone participant, Meera (IST) the third.
        User ananya = new User("ananya", "Ananya", IST);
        User ryan = new User("ryan", "Ryan", PST);
        User meera = new User("meera", "Meera", IST);
        service.registerUser(ananya);
        service.registerUser(ryan);
        service.registerUser(meera);

        Room huddle = new Room("huddle", "Huddle Room", 4);
        Room lambda = new Room("lambda", "Lambda Hall", 8);
        Room delta = new Room("delta", "Delta Boardroom", 12);
        service.roomInventory().addRoom(huddle);
        service.roomInventory().addRoom(lambda);
        service.roomInventory().addRoom(delta);

        System.out.println("Users: Ananya (IST), Ryan (PST), Meera (IST)");
        System.out.println("Rooms: " + huddle + " / " + lambda + " / " + delta);
        System.out.println();

        section1Timezones(service, ananya, ryan);
        section2SchedulingAndConflicts(service, ananya, ryan, meera);
        section3RsvpFlow(service, ananya, ryan, meera);
        section4RecurringAndSkip(service, ananya, ryan);
        section5Rooms(service, ananya, meera, huddle, lambda, delta);
        section6FreeSlots(service, ananya, meera);
        section7Cancellation(service, ananya, ryan, meera);

        System.out.println("\n=== Demo Complete ===");
    }

    // ------------------------------------------------------------------
    private static void section1Timezones(CalendarService service, User ananya, User ryan) {
        System.out.println("=== Section 1: One meeting, two timezones (UTC storage, local display) ===");

        // IST-hosted evening sync at a fixed instant; both zones print it.
        TimeSlot istEvening = slot(2026, 10, 5, 19, 30, 60, IST);
        Meeting sync = service.scheduleMeeting("IST-PST evening sync",
            ananya, participants(ryan), istEvening, null, null);
        System.out.println("Ananya (IST) organizes an evening sync - same stored instants, two displays:");
        System.out.println("  Ananya sees: " + sync.getAnchorSlot().formatIn(ananya.getZone()));
        System.out.println("  Ryan sees:   " + sync.getAnchorSlot().formatIn(ryan.getZone()));
        System.out.println("Conflict math is instant-based, so an overlap is an overlap in any zone -");
        System.out.println("only DISPLAY goes through the viewer's ZoneId.");

        // ...and the other direction: a PST-hosted morning prints as IST evening.
        TimeSlot pstMorning = slot(2026, 10, 6, 8, 30, 60, PST);
        Meeting standup = service.scheduleMeeting("Ryan's PST morning standup",
            ryan, participants(ananya), pstMorning, null, null);
        System.out.println("Ryan (PST) organizes the next morning - the cross-host direction:");
        System.out.println("  Ryan sees:   " + standup.getAnchorSlot().formatIn(ryan.getZone()));
        System.out.println("  Ananya sees: " + standup.getAnchorSlot().formatIn(ananya.getZone()));
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section2SchedulingAndConflicts(CalendarService service,
                                                       User ananya, User ryan, User meera) {
        System.out.println("=== Section 2: Scheduling, adjacency and conflict rejection ===");

        // Monday 10:00-11:00 IST for all three.
        TimeSlot first = slot(2026, 10, 5, 10, 0, 60, IST);
        Meeting planning = service.scheduleMeeting("Planning",
            ananya, participants(ryan, meera), first, null, null);
        System.out.println("Booked: " + planning.getTitle() + " @ "
            + planning.getAnchorSlot().formatIn(ananya.getZone()));

        // ADJACENT [11:00, 12:00): half-open semantics - a meeting ending 11:00
        // and one starting 11:00 do NOT overlap. Allowed.
        TimeSlot adjacent = slot(2026, 10, 5, 11, 0, 60, IST);
        System.out.println("Adjacent [11:00, 12:00) on the same three people - back-to-back is legal:");
        expectSchedulingSuccess(service, "Adjacent 1:1", ananya,
            participants(ryan, meera), adjacent, null);

        // OVERLAP: 10:30-11:30 collides with BOTH 10:00-11:00 and 11:00-12:00 -
        // rejected, with the first conflicting party and its slot named.
        TimeSlot overlapping = slot(2026, 10, 5, 10, 30, 60, IST);
        System.out.println("Overlapping [10:30, 11:30) on the same three people:");
        expectSchedulingRejection(service, "Overlapping 1:1", ananya,
            participants(ryan, meera), overlapping, null);

        // The organizer is a conflicting party too: Meera tries to book Ananya
        // into 10:15-10:45 while Ananya organizes the 10:00 Planning.
        TimeSlot organizerClash = slot(2026, 10, 5, 10, 15, 30, IST);
        System.out.println("Booking the ORGANIZER's busy interval (Ananya, 10:15-10:45):");
        expectSchedulingRejection(service, "Organizer busy probe", meera,
            participants(ananya), organizerClash, null);

        // Construction guardrails, each rejected with its exact reason.
        expectRejection("zero-length slot", () -> slot(2026, 10, 5, 10, 0, 0, IST));
        expectRejection("slot ending before it starts", () -> {
            ZonedDateTime start = LocalDateTime.of(2026, 10, 5, 12, 0).atZone(IST);
            ZonedDateTime end = LocalDateTime.of(2026, 10, 5, 11, 0).atZone(IST);
            return new TimeSlot(start, end);
        });
        expectRejection("meeting with no invited participant besides the organizer", () ->
            service.scheduleMeeting("No invitees", ananya,
                participants(ananya), slot(2026, 10, 20, 10, 0, 60, IST), null, null));
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section3RsvpFlow(CalendarService service,
                                         User ananya, User ryan, User meera) {
        System.out.println("=== Section 3: RSVP flow - a DECLINE lifts the busy block ===");

        // Tuesday 14:00-15:00 IST review: Ananya + Ryan only (Meera stays free
        // so she can probe the slot from the outside).
        TimeSlot review = slot(2026, 10, 6, 14, 0, 60, IST);
        Meeting reviewMeeting = service.scheduleMeeting("Design review",
            ananya, participants(ryan), review, null, null);
        System.out.println("Booked: " + reviewMeeting.getTitle() + " @ "
            + reviewMeeting.getAnchorSlot().formatIn(ananya.getZone())
            + " (participants: " + reviewMeeting.getParticipants() + ")");

        service.respondInvite(reviewMeeting.getMeetingId(), ryan, RSVP.ACCEPTED);
        System.out.println("Ryan ACCEPTED - his block stays; RSVP map: "
            + reviewMeeting.getRsvps());

        // While Ryan holds the block, Meera cannot book him at 14:30-15:00.
        TimeSlot clashing = slot(2026, 10, 6, 14, 30, 30, IST);
        System.out.println("Meera tries to book Ryan into 14:30-15:00 while the review holds:");
        expectSchedulingRejection(service, "Ryan's review still blocking", meera,
            participants(ryan), clashing, null);

        // The DECLINE: Ryan's busy block is LIFTED (a decline does not hold a
        // calendar). TENTATIVE would still block - a stated design choice.
        service.respondInvite(reviewMeeting.getMeetingId(), ryan, RSVP.DECLINED);
        System.out.println("Ryan DECLINED - his busy block is lifted; the slot is now bookable:");
        expectSchedulingSuccess(service, "Slot freed by a decline", meera,
            participants(ryan), clashing, null);

        // The organizer's meeting survives untouched; only Ryan's calendar was freed.
        System.out.println("The review itself still stands - Ananya (organizer) still blocked: "
            + !service.calendarOf(ananya).isFree(clashing));
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section4RecurringAndSkip(CalendarService service,
                                                 User ananya, User ryan) {
        System.out.println("=== Section 4: Recurring WEEKLY series + skip one occurrence ===");

        // Weekly Monday 09:00-09:30 IST standup, bounded COUNT=8 (RRULE-lite).
        TimeSlot anchor = slot(2026, 10, 5, 9, 0, 30, IST);
        RecurrenceRule weekly = new WeeklyRecurrenceRule(1, 8, null);
        Meeting standup = service.scheduleMeeting("Mon standup",
            ananya, participants(ryan), anchor, weekly, null);
        System.out.println("Booked: " + standup);
        System.out.println("RRULE: " + weekly.describe());

        // LAZY expansion: occurrences materialize only inside a bounded window -
        // an unbounded series is never stored (the classic trap on this problem).
        ZonedDateTime windowFrom = LocalDateTime.of(2026, 10, 5, 0, 0).atZone(IST);
        ZonedDateTime windowTo = LocalDateTime.of(2026, 10, 20, 0, 0).atZone(IST);
        System.out.println("Series inside a 2-week window (IST / PST - anchor zone stays 09:00 IST):");
        for (TimeSlot occurrence : standup.allOccurrences(windowFrom, windowTo)) {
            System.out.println("  " + occurrence.formatIn(ananya.getZone())
                + "  (" + occurrence.formatIn(ryan.getZone()) + ")");
        }

        // Skip ONLY the second occurrence (Mon 2026-10-12): its busy blocks lift,
        // the skip list gains one start instant, the series survives.
        ZonedDateTime secondStart = LocalDateTime.of(2026, 10, 12, 9, 0).atZone(IST);
        service.skipOccurrence(standup.getMeetingId(), secondStart);
        System.out.println("Skipped the 2026-10-12 occurrence - skip list holds "
            + standup.getSkippedOccurrences().size() + " start instant(s); "
            + standup.allOccurrences(windowFrom, windowTo).size()
            + " active occurrence(s) remain in the window.");

        // Proof: the skipped Monday is free and rebookable; the NEXT Monday still
        // belongs to the series - skipping one is not cancelling all.
        TimeSlot skippedSlot = new TimeSlot(secondStart, secondStart.plusMinutes(30));
        System.out.println("Ananya free on the skipped Monday 09:00-09:30? "
            + service.calendarOf(ananya).isFree(skippedSlot));
        ZonedDateTime thirdStart = LocalDateTime.of(2026, 10, 19, 9, 0).atZone(IST);
        TimeSlot thirdSlot = new TimeSlot(thirdStart, thirdStart.plusMinutes(30));
        System.out.println("Ananya still busy on the NEXT Monday 09:00-09:30? "
            + !service.calendarOf(ananya).isFree(thirdSlot));
        expectSchedulingSuccess(service, "Rebook the skipped Monday", ananya,
            participants(ryan), skippedSlot, null);
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section5Rooms(CalendarService service, User ananya, User meera,
                                      Room huddle, Room lambda, Room delta) {
        System.out.println("=== Section 5: Room search by capacity + room conflicts ===");

        // Wednesday 16:00-17:00 IST, 5 people: the 4-seat huddle drops out.
        TimeSlot offsite = slot(2026, 10, 7, 16, 0, 60, IST);
        System.out.println("5 people, Wed [16:00, 17:00) IST - rooms with capacity >= 5:");
        for (Room room : service.searchRooms(5, offsite)) {
            System.out.println("  " + room);
        }
        System.out.println("(Huddle Room filtered out: 4 seats < 5.)");

        // Standalone room hold, then the room joins the PEOPLE conflict engine.
        service.bookRoom(delta, offsite);
        System.out.println("Booked " + delta.getName() + " for the offsite "
            + offsite.formatIn(ZoneId.of("UTC")) + " (UTC on purpose - rooms store UTC too)");

        // Room conflict: a 2-person meeting wants Delta at 16:30 (overlaps the
        // offsite) - rejected by the SAME checkConflicts engine as people.
        TimeSlot sameHour = slot(2026, 10, 7, 16, 30, 30, IST);
        System.out.println("Scheduling a 2-person meeting into Delta at [16:30, 17:00) - overlaps:");
        expectSchedulingRejection(service, "Room double-booked", ananya,
            participants(meera), sameHour, delta);

        // Adjacent room booking at 17:00 IS allowed - half-open semantics again.
        TimeSlot afterOffsite = slot(2026, 10, 7, 17, 0, 60, IST);
        System.out.println("Booking Delta right after the offsite, [17:00, 18:00) IST - adjacent:");
        expectSchedulingSuccess(service, "Adjacent room booking", ananya,
            participants(meera), afterOffsite, delta);
        System.out.println("People and rooms are both 'busy interval holders' - ONE conflict engine");
        System.out.println("serves both; no parallel room-booking logic anywhere.");
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section6FreeSlots(CalendarService service, User ananya, User meera) {
        System.out.println("=== Section 6: Free-slot finder across two users (merge + sweep) ===");

        // Thursday 2026-10-08: Ananya + Meera hold a morning block and a lunch block.
        TimeSlot morningBlock = slot(2026, 10, 8, 10, 0, 90, IST);
        service.scheduleMeeting("Ananya deep work", ananya, participants(meera),
            morningBlock, null, null);
        TimeSlot lunch = slot(2026, 10, 8, 12, 30, 60, IST);
        service.scheduleMeeting("Team lunch", meera, participants(ananya), lunch, null, null);
        System.out.println("Thursday blocks on both calendars: "
            + morningBlock.formatIn(IST) + ", " + lunch.formatIn(IST));

        LocalDate thursday = LocalDate.of(2026, 10, 8);
        List<TimeSlot> free = service.findFreeSlots(
            Arrays.asList(ananya, meera), thursday, IST, 1, 60, 3);
        System.out.println("First 3 free 60-minute slots for Ananya + Meera on "
            + thursday + " (working hours 09:00-18:00 IST):");
        for (TimeSlot slot : free) {
            System.out.println("  " + slot.formatIn(IST));
        }
        System.out.println("The finder MERGES both users' busy intervals once, then sweeps the working");
        System.out.println("hours - the 'merge intervals' answer, not N per-candidate lookups.");
        System.out.println();
    }

    // ------------------------------------------------------------------
    private static void section7Cancellation(CalendarService service,
                                             User ananya, User ryan, User meera) {
        System.out.println("=== Section 7: Cancellation lifts all blocks; notifications via the bus ===");

        // A throwaway Friday meeting, cancelled whole: every busy block lifts
        // (organizer + participants) and the CANCELLED event fans out.
        TimeSlot friday = slot(2026, 10, 9, 15, 0, 60, IST);
        Meeting retro = service.scheduleMeeting("Quarterly retro",
            ananya, participants(ryan, meera), friday, null, null);
        System.out.println("Booked: " + retro.getTitle() + " @ "
            + retro.getAnchorSlot().formatIn(IST));
        System.out.println("Ananya's busy blocks before cancel: "
            + service.calendarOf(ananya).getBusySlots().size());

        service.cancelMeeting(retro.getMeetingId());
        System.out.println("Ananya's busy blocks after cancel:  "
            + service.calendarOf(ananya).getBusySlots().size());
        System.out.println("Ryan's busy blocks after cancel:    "
            + service.calendarOf(ryan).getBusySlots().size());
        System.out.println("Slot rebookable immediately? "
            + service.calendarOf(ananya).isFree(friday));
        System.out.println("Every event in this log was printed by ConsoleNotificationListener:");
        System.out.println("INVITE / RSVP / UPDATED / CANCELLED / ROOM_BOOKED fan out to any listener");
        System.out.println("with zero scheduler changes - the observer-pattern payoff (OCP).");
        System.out.println();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Fixed-instant slot helper: [year-month-day hour:minute, +duration). */
    private static TimeSlot slot(int year, int month, int day,
                                 int hour, int minute, int durationMinutes, ZoneId zone) {
        ZonedDateTime start = LocalDateTime.of(year, month, day, hour, minute).atZone(zone);
        return new TimeSlot(start, start.plusMinutes(durationMinutes));
    }

    private static Set<User> participants(User... users) {
        return new LinkedHashSet<>(Arrays.asList(users));
    }

    private static void expectSchedulingSuccess(CalendarService service, String title,
                                                User organizer, Set<User> participants,
                                                TimeSlot slot, Room room) {
        try {
            Meeting booked = service.scheduleMeeting(title, organizer, participants, slot, null, room);
            System.out.println("  [OK] Booked: " + booked.getTitle() + " @ "
                + slot.formatIn(organizer.getZone())
                + (room != null ? " in " + room.getName() : ""));
        } catch (RuntimeException e) {
            System.out.println("  [BUG] Expected success but got rejection: " + e.getMessage());
        }
    }

    private static void expectSchedulingRejection(CalendarService service, String title,
                                                  User organizer, Set<User> participants,
                                                  TimeSlot slot, Room room) {
        try {
            service.scheduleMeeting(title, organizer, participants, slot, null, room);
            System.out.println("  [BUG] Accepted, expected rejection: " + title);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            System.out.println("  [OK] Rejected: " + title);
            System.out.println("        Reason: " + expected.getMessage());
        }
    }

    private interface Construction {
        Object construct();
    }

    private static void expectRejection(String description, Construction construction) {
        try {
            construction.construct();
            System.out.println("  [BUG] Accepted: " + description);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            System.out.println("  [OK] Rejected: " + description);
            System.out.println("        Reason: " + expected.getMessage());
        }
    }
}
