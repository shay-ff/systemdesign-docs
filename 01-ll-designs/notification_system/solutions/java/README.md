# Notification System - Java Implementation

Java 11, no external libraries, no `package` declarations (one top-level class per file, repo convention).

## Class-by-Class Design

| File | Role |
|---|---|
| `NotificationDemo.java` | Five-section end-to-end narrative demo with `main` |
| `Notification.java` | Abstract base: identity (`notificationId` - the dedupe key), routing, attempt count; `final` template-method `format()` (shared skeleton, subclass body); guarded `overrideId`/`withId` replay seam |
| `EmailNotification.java` / `SmsNotification.java` / `PushNotification.java` | Channel variants: body + framing hooks, per-channel validation (`@`, digit phone, `device:` token), SMS 160-char truncation |
| `NotificationChannel.java` / `NotificationPriority.java` / `NotificationStatus.java` | Enums: routing key; HIGH=0/NORMAL=1 queue rank; the six-outcome lifecycle vocabulary |
| `NotificationSender.java` | Per-channel strategy interface: `send(notification)` throws `SendException` |
| `EmailSender.java` | Mock SMTP with a DETERMINISTIC failure script (`failOnAttempts`) - attempt 1-2 fail (greylist), 3 lands |
| `SmsSender.java` | Mock SMS gateway with a static script bound (`failAllAttemptsUpTo`, `MAX_VALUE` = fail forever) - the bounded-script alternative to the flippable sender below; not wired into the demo (which needs the runtime flip) but kept as the second deterministic-mock idiom |
| `SwitchableSmsSender.java` | Mock carrier with a runtime-flippable `gatewayDown` flag (AtomicBoolean) - "down now, recovered later" timelines |
| `PushSender.java` | Mock FCM with one permanently dead token - the PERMANENT failure path |
| `SendException.java` | Carries `transient` vs `permanent` - transient retries, permanent dead-letters immediately |
| `RetryPolicy.java` | Injected budget: max attempts + exponential backoff with cap (`min(base * 2^(n-1), cap)`) |
| `TokenBucketRateLimiter.java` | Minimal per-channel bucket (burst + refill); full reference: `../rate_limiter/` |
| `DispatcherService.java` | The heart: `PriorityBlockingQueue` ordered by (rank, sequence), `ExecutorService` workers polling with timeout, per-channel limiting (defer-not-fail), retry loop, sink-side dedupe (`deliveredIds`), dead-letter log, outcome records |
| `NotificationAttempt.java` | Immutable one-attempt record - the attempt HISTORY the dead letter needs |
| `DeliveryOutcome.java` | Immutable terminal outcome + attempts + summary line |

Key invariants:

- A notification whose id is already in `deliveredIds` is never delivered twice (the at-least-once replay is absorbed at the sink).
- A notification whose id is already `enqueued` is never queued twice (fail-fast duplicate rejection).
- A permanent failure never burns the retry budget - straight to the dead letter.
- A rate-limited notification is never FAILED - it is deferred and re-queued (pacing, not error).
- Every terminal outcome (SENT / DEAD_LETTER / DUPLICATE) is recorded exactly once in the outcome log.

## Run

```bash
cd solutions/java

# Option 1: compile then run
javac *.java
java NotificationDemo

# Option 2: no javac handy - merge into ONE file and run it
#   (script hoists imports and concatenates the classes in dependency order)
python3 /path/to/merge_java.py . NotificationDemo.java
java /tmp/merged_notification_system.java
```

Runtime is ~2 seconds (base backoff 150ms capped at 400ms, token refill 10/s - all demo-scale; production numbers are seconds-to-minutes). Output is grouped under `=== Section N ===` headers:

1. Priority ordering (HIGH OTP picked up before earlier-enqueued NORMAL) + transient email failure retrying through to SENT.
2. Carrier outage -> retries exhausted -> DEAD-LETTER; plus a permanent push failure skipping retries entirely.
3. Carrier recovers; four SMS against a 2-burst/10-per-s bucket -> the first 2 pass, the rest deferred then sent.
4. At-least-once replay of an already-delivered OTP id -> sink dedupe drops it (no second OTP); duplicate submission rejected at enqueue.
5. Summary: one line per terminal outcome + the dead-letter log.

## Complexity

| Operation | Cost |
|---|---|
| Enqueue | O(log N) offer + O(1) dedupe |
| Worker dequeue | O(log N) |
| Rate-limit check | O(1) |
| Send + format | O(message length) |
| Outcome append | O(1) |

## Production Notes (interview talking points)

- Swap the in-memory queue for Kafka/SQS: the broker IS the queue, its redrive policy is the retry loop, its DLQ is the dead-letter log - senders and policies unchanged (that is the payoff of the seams here).
- Starvation fix: aging (`effectiveRank = baseRank - waitedTime/coefficient` with periodic re-offer) or two queues + weighted round-robin; see the problem's `explanation.md`.
- Per-tenant limits compose with per-channel: both buckets must pass.
- The sink dedupe in production keys on (recipient, notificationId) with a TTL, and the receiving vendor does its own idempotency - end-to-end idempotency is a chain.
