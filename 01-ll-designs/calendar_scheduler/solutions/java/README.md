# Calendar / Meeting Scheduler — Java Implementation

Java 11, no external libraries, no `package` declarations (repo convention: one top-level class per file, compiled side by side).

## Design Patterns

- **Facade** — `CalendarService` is the only class the demo talks to; conflict logic, calendars, rooms, RSVP policy, recurrence expansion and notifications are coordinated behind it.
- **Strategy** — `RecurrenceRule` (`DailyRecurrenceRule`, `WeeklyRecurrenceRule`): expansion rules are injected; MONTHLY/YEARLY are new classes, not edits (OCP).
- **Observer** — `NotificationService` fans out `INVITE`/`RSVP`/`UPDATED`/`CANCELLED`/`ROOM_BOOKED` events to any `NotificationListener`; email/SMS listeners are one new class, zero scheduler changes.
- **Iterator / lazy expansion** — recurring series expand to concrete occurrences only inside a bounded window; an unbounded series is never materialized.
- **Composite-flavoured resource model** — people (`Calendar`) and rooms (`Room`) are both "busy interval holders"; one conflict engine serves both.
- **Value object** — `TimeSlot` is immutable, half-open `[start, end)`, always normalized to UTC at construction.

## Class-by-Class

| File | Class | Responsibility |
|---|---|---|
| `User.java` | `User` | Identity + home `ZoneId` (equality on `userId`); calendars belong to the service, not the user |
| `TimeSlot.java` | `TimeSlot` | Immutable half-open `[start, end)` slot stored in UTC; `overlaps()` with strict `<` (adjacent meetings allowed); `formatIn(zone)` for local display |
| `Meeting.java` | `Meeting` | Series metadata: organizer, participants (organizer deduped), anchor slot, optional rule/room, RSVP map, skip list of occurrence start instants; `allOccurrences(from, to)` materializes lazily, skips excluded |
| `Occurrence.java` | `Occurrence` | One concrete slot of a series, produced on demand; reports whether it is on the series' skip list |
| `RecurrenceRule.java` | `RecurrenceRule` | Strategy contract: `occurrencesBetween(anchor, from, to)` bounded-window expansion, `expand(anchor, count)`, `nextStart`, `describe()` |
| `DailyRecurrenceRule.java` | `DailyRecurrenceRule` | FREQ=DAILY;INTERVAL=n;COUNT/UNTIL — steps the anchor in its own zone (DST-correct), converts to UTC per occurrence |
| `WeeklyRecurrenceRule.java` | `WeeklyRecurrenceRule` | FREQ=WEEKLY;INTERVAL=n;COUNT/UNTIL — same anchor-zone expansion contract, weekly step |
| `RecurrenceType.java` | `RecurrenceType` | Enum `DAILY`/`WEEKLY` (MONTHLY/YEARLY would be new rule classes) |
| `RSVP.java` | `RSVP` | Enum `PENDING`/`ACCEPTED`/`DECLINED`/`TENTATIVE` |
| `Room.java` | `Room` | Bookable resource with capacity + its own busy slots — reuses the same conflict semantics as people (`book`/`release`/`findConflicts`) |
| `RoomInventory.java` | `RoomInventory` | Room registry; `search(minCapacity, slot)` = capacity filter + availability in one call |
| `Calendar.java` | `Calendar` | Per-user sorted busy list + meeting-id list; `isFree`/`findConflicts`/`addBusy`/`removeBusy`; a dumb interval store (RSVP policy lives in the service) |
| `CalendarService.java` | `CalendarService` | Facade: `scheduleMeeting` (conflict check on organizer + every participant + room, then busy blocks + invites), `respondInvite` (DECLINE lifts that user's blocks), `cancelMeeting`, `skipOccurrence`, `bookRoom`/`searchRooms`, `findFreeSlots`; bounded 8-week placement window for series |
| `NotificationKind.java` | `NotificationKind` | Enum of bus event kinds |
| `Notification.java` | `Notification` | Immutable event: kind + meetingId + message |
| `NotificationListener.java` | `NotificationListener` | Observer interface (not deprecated class-based `Observable`) |
| `NotificationService.java` | `NotificationService` | In-memory observer bus; `subscribe`/`unsubscribe`; one throwing listener cannot starve the others |
| `ConsoleNotificationListener.java` | `ConsoleNotificationListener` | Demo listener printing every event to stdout |
| `FreeSlotFinder.java` | `FreeSlotFinder` | "First N slots where all M users are free": merge busy intervals across users, sweep 09:00–18:00 requester-zone working hours in 30-min steps |
| `CalendarDemo.java` | `CalendarDemo` | Seven-section demo with `===` headers, fixed 2026-10-05 dates, IST/PST users |

## Run

```bash
# Java 22+ single-file source launcher (handles sibling types):
java CalendarDemo.java

# Java 11+ classic:
javac *.java && java CalendarDemo
```

## Demo Sections

1. **One meeting, two timezones** — an IST-hosted evening sync and a PST-hosted morning standup; the same UTC instants print as 19:30 IST / 07:00 PST (and 08:30 PST / 21:00 IST) via `formatIn(zone)`.
2. **Scheduling, adjacency and conflict rejection** — adjacent `[10,11)` + `[11,12)` on the same three people allowed (half-open), overlapping `[10:30,11:30)` rejected naming the busy party, organizer-conflict rejection, plus zero-length / reversed / zero-participant guardrails.
3. **RSVP flow** — Ryan ACCEPTS (block stays), a probe booking is rejected, Ryan DECLINES (his busy block lifts), Meera rebooks the freed `[14:30,15:00)` successfully; the organizer's meeting survives.
4. **Recurring WEEKLY + skip one occurrence** — `WEEKLY;INTERVAL=1;COUNT=8` standup lazily expanded inside a 2-week window in both zones; the 2026-10-12 occurrence is skipped (skip list + busy blocks lift), 10-19 still holds, the freed Monday is rebooked.
5. **Room search by capacity + room conflicts** — capacity ≥ 5 filters out the 4-seat huddle; `bookRoom` hold on Delta; overlapping room booking rejected by the same conflict engine; adjacent `[17,18)` booking into Delta allowed.
6. **Free-slot finder** — two IST users with a 10:00–11:30 and a 12:30–13:30 block; first 3 free 60-min slots inside 09:00–18:00 IST found by merge-then-sweep.
7. **Cancellation + notifications** — the retro is cancelled: every busy block lifts for organizer and participants, the slot is immediately rebookable, and the `CANCELLED` event is visible via `ConsoleNotificationListener`.

Rerunning the demo reproduces the identical log (all dates are fixed 2026-10-05 onward; no wall clock, no sleeps).
