# Calendar / Meeting Scheduler - Low Level Design

A meeting scheduling system (think Google Calendar lite) with per-participant conflict detection, invite + RSVP flow, recurring meetings (RRULE-lite), room booking with capacity filter, free-slot search, and an in-memory notification bus.

## Problem Statement

Design a calendar / meeting scheduler where:

- **Users** have calendars. A **meeting** has an organizer, participants, a title, and a time interval.
- A meeting cannot be scheduled if **any required participant** (or the organizer, or the room) is **busy during the interval**.
- Invited users **respond** with RSVP: `ACCEPTED / DECLINED / TENTATIVE`.
- Meetings can be **recurring**: DAILY or WEEKLY with an interval step, until a count or an end date (RRULE-lite; full RFC 5545 is out of scope).
- A recurring series supports **exceptions**: skip a single occurrence without touching the rest of the series.
- **Rooms** are a bookable resource with capacity; meeting rooms are filtered by headcount.
- All times are stored in **UTC** and rendered in each viewer's timezone.
- Participants get **notifications** (invite, RSVP change, cancellation) via a simple in-memory observer bus.
- The system can **find the first N free slots** for a set of users within working hours (09:00-18:00 local).

## Key Features

- **Conflict detection per participant** — the crux. Clean `TimeSlot` interval with `overlaps()`; sorted per-user busy-lists.
- **Half-open interval semantics, documented**: `[10:00, 11:00)` and `[11:00, 12:00)` do NOT conflict — adjacent meetings are allowed. `[10:00, 11:00)` and `[10:59, ...)` DO conflict.
- **UTC storage, local display** — `ZonedDateTime` in UTC internally, formatted per user's `ZoneId`; demo shows one IST and one PST user.
- **RRULE-lite recurrence** — `RecurrenceRule` (DAILY / WEEKLY + interval, count/until), expanded lazily to concrete `Occurrence`s; series-level skip list for exceptions.
- **Room inventory** — capacity-filtered room search; rooms are resources in the same conflict engine as people.
- **Observer-pattern notifications** — `NotificationService` fans out invites/updates/cancellations to a `NotificationListener` (console listener in the demo).
- **Free-slot finder** — merges busy intervals of M users and scans for gaps within 09:00-18:00 local working hours.

## Clarifying Questions an Interviewer Expects You to Ask

1. **Do adjacent meetings conflict?** No — endpoint-touching is allowed: ending 11:00 and starting 11:00 is fine. Define this up front; it is the #1 ambiguity. (This design uses half-open `[start, end)`.)
2. **Conflict for whom?** Organizer + every invited participant (people) + the room (a resource). A DECLINED participant frees their calendar? Design choice: DECLINED participants' busy blocks are *not* counted (they said no), TENTATIVE still blocks. State your choice.
3. **Timezones?** Store UTC, display local. Every `TimeSlot` holds `ZonedDateTime` in UTC; formatting uses the viewer's `ZoneId`.
4. **Recurring meetings — how far do you expand?** Lazy: expand the rule to concrete occurrences only when checking/scheduling within a window; never materialize an unbounded series.
5. **What does "skip one occurrence" mean?** The series survives; a single occurrence's start instant goes on the series' skip list. Cancelling one != cancelling all.
6. **Can a meeting have zero participants?** No — organizer + at least one participant (validated).
7. **Are rooms required?** Optional in this design: `bookRoom` is separate; if a room is attached, it joins the conflict check.
8. **Free-slot search bounds?** Working hours 09:00-18:00 in the *requester's* local zone, across the next N days, 30-min granularity.
9. **Notifications reliable?** In-memory observer here; production = queue + retries (out of scope, documented).

## Core Entities

| Entity | Responsibility |
|---|---|
| `User` | Identity + home `ZoneId` (IST/PST in the demo) |
| `TimeSlot` | `[start, end)` `ZonedDateTime` pair (UTC) with `overlaps()`, `toLocal(zone)` |
| `Meeting` / `Occurrence` | Series metadata; `Occurrence` = one concrete slot of a series |
| `RecurrenceRule` | DAILY/WEEKLY + interval, count or until; `nextFrom(...)` expansion |
| `RSVP` | Enum: PENDING, ACCEPTED, DECLINED, TENTATIVE |
| `Room` | Bookable resource with capacity |
| `Calendar` | Per-user busy slots + RSVP-aware conflict check |
| `CalendarService` | Schedule/invite/RSVP/cancel/skip-occurrence/rooms/free-slots facade |
| `RoomInventory` | Capacity-filtered room search + booking |
| `NotificationService` / `NotificationListener` | Observer bus + console listener |
| `FreeSlotFinder` | Merged busy intervals -> first N gaps in working hours |
| `CalendarDemo` | End-to-end narrative demo |

## Design Patterns Used (and why)

- **Observer — notifications.** Invite/RSVP/cancel events fan out to listeners; adding email/SMS listeners requires no scheduler changes (OCP).
- **Strategy — recurrence expansion.** DAILY and WEEKLY expansion rules are two small classes behind one interface; MONTHLY/YEARLY are new classes, not edits.
- **Iterator / lazy expansion — recurring meetings.** The rule produces occurrences on demand; the system never stores an infinite series.
- **Facade — CalendarService.** Demo and callers see one API; conflict logic, rooms, RSVP maps and notifications are coordinated behind it.
- **Composite-ish resource model.** People and rooms are both "things that hold busy intervals" — one conflict engine serves both.

## How to Run

```bash
cd solutions/java

# Option 1: single-file source launch (Java 11+)
java CalendarDemo.java

# Option 2: compile then run
javac *.java
java CalendarDemo
```

Runtime well under a second (no sleeps).

## Time Complexity

- Conflict check for one user: O(log S + k) via binary search into the sorted busy list, k = overlapping slots (degrades gracefully to O(S) when expanding recurrences)
- Scheduling a meeting with P participants: O(P x (log S + k))
- Free-slot search over M users and D days: O(M x S log S + D x W) — merge busy, then sweep working-hour windows
- RSVP update: O(1) map lookup

## Interview Extension Questions

1. How do you handle ** DST transitions** for recurring meetings? (Expand in the *series'* anchor zone, convert to UTC per occurrence — exactly what this design does; mention `ZoneId` rules vs fixed offsets.)
2. What if two threads schedule at 11:00 simultaneously? (Per-user lock or optimistic version on the busy list; DB unique-range constraint.)
3. How would you store this? (`meetings`, `occurrences`, `rsvps` tables; busy intervals as `tstzrange` with an exclusion constraint per user.)
4. Free-busy at Google scale? (Cache per-user busy bitmaps; sharded by user id; batch APIs for cross-shard queries.)
5. How do you support "optional" participants? (RSVP weight in the conflict check: optional participants warn but do not block.)
6. Bigger rooms / equipment filters? (Predicate on Room search — additive.)
7. How would you implement "find a time that works for 5 people across 4 timezones"? (Generalize the slot finder: intersect working-hours windows first, then sweep merged busy lists — this demo's `FreeSlotFinder` is that algorithm on 2 users.)
8. Propose new time / counter-offer flow? (New occurrence slot + tentative RSVP state machine.)

## Files Structure

```
calendar_scheduler/
├── README.md              # This file
├── design.puml            # PlantUML class diagram
├── explanation.md         # Design walkthrough, trade-offs, edge cases
└── solutions/
    └── java/
        ├── README.md      # Class-by-class notes + run instructions
        ├── CalendarDemo.java
        ├── User.java / TimeSlot.java / Meeting.java / Occurrence.java
        ├── RecurrenceRule.java / DailyRecurrenceRule.java / WeeklyRecurrenceRule.java
        ├── RecurrenceType.java / RSVP.java
        ├── Room.java / RoomInventory.java
        ├── Calendar.java / CalendarService.java
        ├── NotificationService.java / NotificationListener.java / ConsoleNotificationListener.java
        └── FreeSlotFinder.java
```

See [explanation.md](explanation.md) for the full design walkthrough.
