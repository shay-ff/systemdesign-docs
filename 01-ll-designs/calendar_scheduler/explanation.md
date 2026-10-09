# Calendar / Meeting Scheduler - Design Explanation

A walkthrough of every entity, the relationships, pattern choices, rejected alternatives, trade-offs, and the classic edge cases interviewers use on this problem.

## The Problem in One Sentence

Schedule meetings between people (and rooms) such that **no required participant is double-booked**, support recurring series with exceptions, RSVP, timezones, and free-slot search — the crux being **conflict detection over intervals per participant**.

---

## The Most Important Design Decision: Interval Semantics

**Half-open intervals `[start, end)`, stated out loud.**

- A meeting 10:00-11:00 and another 11:00-12:00 do **NOT** conflict — back-to-back meetings are the normal case.
- 10:00-11:00 and 10:30-11:30 DO conflict.
- Zero-length (start == end) is invalid — rejected with a message.

```java
public boolean overlaps(TimeSlot other) {
    return start.isBefore(other.end) && other.start.isBefore(this.end);
}
```

The strict `<` comparison is what makes touching endpoints legal. Interviewers *will* probe "what if one meeting ends exactly when another starts?" — answer with the half-open convention before they ask.

**Adjacent meetings allowed** is documented in both READMEs and enforced in the demo (the conflict-then-resolve section books the adjacent slot successfully).

## Timezone Strategy: Store UTC, Display Local

- `TimeSlot` holds `ZonedDateTime` values **normalized to UTC** at construction (`withZoneSameInstant(ZoneOffset.UTC)`).
- All conflict math happens on instants — timezone-agnostic.
- All *display* goes through `formatIn(ZoneId)` — the same slot prints as 19:30 IST and 07:00 PST.
- Recurring series keep an **anchor zone**: a "weekly 9:00 IST standup" stays at 9:00 *IST* across DST-in-naive-implementations; each occurrence is computed in the anchor zone and then converted to UTC. (IST has no DST; the PST user in the demo is where a naive fixed-offset rule would drift — the anchor-zone approach handles it.)

Demo: organizer in `Asia/Kolkata` (IST), participant in `America/Los_Angeles` (PST); the same three meeting slots print in both zones.

---

## Entity-by-Entity Rationale

### `User`
Identity + home `ZoneId`. Nothing else — calendars belong to the system, not the user object (see `Calendar`).

### `TimeSlot` — immutable value object
`[start, end)` over `ZonedDateTime` (UTC). Owns `overlaps`, `containsInstant`, `durationMinutes`, `toLocalDate(zone)`, `formatIn(zone)`. Equality and hash on the instant pair, so slots compare correctly across zones. Validation: start strictly before end; null checks with named-field messages.

### `Meeting` vs `Occurrence` — the series/occurrence split
- `Meeting` = series metadata: id, title, organizer, participants, optional `Room`, optional `RecurrenceRule`, and the **first occurrence's slot** (the anchor).
- `Occurrence` = one concrete slot of that meeting, materialized on demand by expanding the rule. **Never store the full series** — unbounded expansion is the classic trap.

This split is what makes "skip one occurrence" trivial: the skip list holds occurrence start instants; the rest of the series is untouched.

### `RecurrenceRule` (interface) + `DailyRecurrenceRule` + `WeeklyRecurrenceRule` — strategy pattern
- `occurrencesBetween(anchor, from, to)` expands concrete slots in a bounded window — lazy, bounded work.
- `expand(occurrenceCount)` for demo printing.
- `nextStart(after)` steps the anchor: DAILY adds `interval` days; WEEKLY adds `interval * 7` days.
- Full RFC 5545 RRULE (BYDAY, BYSETPOS, UNTIL datetime forms, RDATE/EXDATE...) is explicitly **out of scope** — say so, then sketch it: one `RecurrenceRule` implementation per grammar subset, a parser in front, same expansion contract.

Why strategy instead of one class with a `RecurrenceType` switch? Because MONTHLY/YEARLY/BYDAY are *new classes*, and the switch grows into a monolith — the pattern's whole point is never editing existing expansion logic (OCP). For a 45-minute round, two small rule classes read as deliberate, not over-engineered.

### `RSVP`
PENDING -> ACCEPTED / DECLINED / TENTATIVE. Design choice, stated: **DECLINED participants do not block scheduling** (they said no — their calendar is not held), **TENTATIVE still blocks** (a pencil-hold beats double-booking someone who might show up). The conflict engine takes a `blockOnDeclined=false` predicate behavior.

### `Room` + `RoomInventory`
A room is a *resource that holds busy intervals*, exactly like a participant — so it reuses the same conflict engine rather than a parallel one. `RoomInventory.search(minCapacity)` filters by capacity and checks availability for a window. Rooms are optional per meeting; if attached, they join the conflict check.

### `Calendar`
Per user: a sorted list of busy `TimeSlot`s plus the meeting map. Key methods:

- `isFree(slot)` / `findConflicts(slot)` — binary search into the sorted busy list: O(log S + k).
- `addBusy(slot)` / `removeBusy(slot)` — maintains sort order.

Why per-user calendars instead of one global list? Conflict checks are always per-participant, so per-user structures keep each check small and local; a global list makes every check scan everyone.

### `CalendarService` — the facade
The only class the demo talks to:

1. `scheduleMeeting(...)` — the crux: conflict check on organizer + every participant (+ room), create meeting, place busy slots, send invites.
2. `respondInvite(meetingId, user, rsvp)` — RSVP state machine; on DECLINE, that user's busy block is lifted.
3. `cancelMeeting(meetingId)` — cancels the whole series (all busy blocks lifted, notifications out).
4. `skipOccurrence(meetingId, occurrenceStart)` — single-occurrence exception: busy block lifted for that participant set, series survives.
5. `bookRoom(...)` / `searchRooms(...)` — room conflict + capacity.
6. `findFreeSlots(...)` — delegates to `FreeSlotFinder`.

Booking = check-then-act again. Single-threaded demo; the concurrency answer (per-user lock / optimistic version / DB range exclusion) is the same as car rental's — see that problem's explanation for the full treatment.

### `NotificationService` + `NotificationListener` — observer pattern
`subscribe(listener)` / `unsubscribe`; the service pushes `Notification` events (INVITE, UPDATED, CANCELLED, RSVP, ROOM_BOOKED). The demo's `ConsoleNotificationListener` prints them. Adding an email listener = one new class, zero scheduler changes — the OCP payoff. Production: queue + retries + idempotency (out of scope, documented).

Why not `java.util.Observable`? Deprecated since Java 9, and it's class-based — an interface keeps listeners open for extension.

### `FreeSlotFinder` — merged-interval sweep
The "find first N slots where all M users are free" algorithm:

1. Pull each user's busy slots for the requested day(s), converted to the requester's zone.
2. Merge overlapping busy intervals into a disjoint set (sort + linear merge).
3. Walk the 09:00-18:00 local working-hour window in 30-min steps; a step is free if no merged busy interval covers it; collect until N slots or days exhausted.

O(M x S log S + D x W). Rejected alternative: for each candidate slot, check each user's calendar by binary search — O(N x M x log S), which re-scans shared busy intervals; the merge-then-sweep does the work once and is the answer interviewers want to hear ("merge intervals" is the underlying pattern).

---

## Class Relationships

```
CalendarService        1 ── many  User            (registry)
CalendarService        1 ── many  Calendar        (one per user)
CalendarService        1 ── 1     RoomInventory
CalendarService        1 ── 1     NotificationService
User                   1 ── 1     Calendar
Calendar               1 ── many  TimeSlot        (sorted busy list)
Meeting                1 ── 1     TimeSlot        (anchor occurrence slot)
Meeting                1 ── 1     RecurrenceRule? (null = single meeting)
Meeting                * ── 1     User            (organizer)
Meeting                * ── many  User            (participants)
Meeting                * ── 1     Room?          (optional)
Occurrence             * ── 1     Meeting         (materialized series items)
RecurrenceRule  <|-- DailyRecurrenceRule
RecurrenceRule  <|-- WeeklyRecurrenceRule
RoomInventory         1 ── many  Room
Room                   1 ── many  TimeSlot        (busy slots — same engine as people)
NotificationService   1 ── many  NotificationListener  (observer)
CalendarService      uses→ FreeSlotFinder
```

People and rooms both model as "busy interval holders" — one conflict engine (`Calendar`, reused via `RoomInventory`'s per-room busy check) serves both. That symmetry is the composite-flavoured insight worth saying out loud.

---

## Edge Cases and How the Code Handles Them

| Edge case | Behaviour |
|---|---|
| Adjacent meetings (one ends 11:00, next starts 11:00) | **Allowed** — half-open intervals, strict `<` |
| Overlapping by 1 minute | Rejected — overlaps |
| Same start/end instant (zero-length) | `IllegalArgumentException` |
| start after end | `IllegalArgumentException` with both instants in the message |
| Cross-zone booking (IST organizer, PST participant) | Correct — all math on UTC instants |
| Recurring series unbounded | Never materialized — expansion only within a bounded window |
| Skip one occurrence | Series survives; only that occurrence's busy blocks lift |
| Cancel whole series | All busy blocks lift; participants notified |
| Participant DECLINES after accepting | Their busy block lifts; others unaffected |
| Room too small | Filtered out by capacity search |
| Room double-booked | Same conflict engine rejects |
| Free-slot search with everyone busy all day | Returns fewer than N (possibly zero) with a clear message |
| Duplicate participant list / organizer in participants | Organizer deduped from participant set |
| Unknown meeting id / user rsvp to uninvited meeting | Rejected with messages |

---

## Trade-offs Accepted

1. **Sorted-list busy storage over interval trees.** O(log S + k) search with O(S) insert is the right interview answer; interval trees are the production-scale answer (same trade-off writeup as car rental — the two problems share this crux).
2. **RSVP-aware conflict check.** Declined frees the slot — a real product behavior worth stating; the simpler "always block everyone" is defensible if you say so.
3. **Working hours hardcoded 09:00-18:00 local.** Real products make this per-user; here it is a `FreeSlotFinder` constant — an easy extension question.
4. **In-memory IDs and maps, no persistence.** Repo convention for LLD practice; the persistence story (occurrences table, tstzrange exclusion) is an extension answer.
5. **Notifications in-process.** No queue/retry — documented as the production gap.
6. **Lazy occurrence expansion, bounded windows.** Avoids both infinite loops and multi-year materialization; slightly more complex than a pre-expanded list, which would be flatly wrong at WEEKLY-forever series.

---

## Complexity Summary

| Operation | Complexity |
|---|---|
| `TimeSlot.overlaps` | O(1) |
| Per-user conflict check | O(log S + k), S = user's busy slots |
| Schedule with P participants | O(P x (log S + k)) (+ room check) |
| Recurrence expansion over a window | O(k) occurrences generated |
| RSVP / cancel / skip occurrence | O(S) (busy-list removal) or O(P x S) |
| Free-slot search, M users, D days | O(M x S log S + D x W) |
| Room search by capacity | O(R x (log S + k)) |

---

## How to Extend (Interview Talking Points)

- **Per-user working hours**: move 09:00-18:00 into `User`; the finder intersects per-user windows.
- **Optional participants**: RSVP-weighted conflict check — optional invitees warn, don't block.
- **Propose new time**: an occurrence slot + TENTATIVE RSVP, reusing the existing state machine.
- **Full RRULE**: one `RecurrenceRule` implementation per grammar subset + a parser in front; expansion contract unchanged.
- **Concurrency**: per-user locks or optimistic versions on the busy list; DB `tstzrange` exclusion constraints make double-booking structurally impossible.
- **Scale**: per-user busy caching (free/busy bitmaps), sharding by user, batch availability APIs for cross-shard queries.
- **Reminders**: another notification type on the existing observer bus + a scheduler thread.
