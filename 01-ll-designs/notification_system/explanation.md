# Notification System - Design Explanation

A walkthrough of every entity, the relationships, why each pattern was chosen, what was rejected, and the edge cases interviewers use to trip you up.

## The Problem in One Sentence

Many producers, three channels, vendors that fail transiently and throttle by rate, and a delivery contract (at-least-once) that only stays honest if every notification carries an identity - all funneled through one queue with priorities and workers.

The three cruxes - **priority ordering**, **retry semantics**, **at-least-once + idempotency** - are where this problem earns its interview slot. Everything else is standard OOP modelling.

---

## Entity-by-Entity Rationale

### `Notification` (abstract) + the three channel variants

The abstract base owns identity, routing data, and the **template method**:

```java
public final String format() {        // skeleton: To / Subject / body / footer
    ... formatBody() ...             // subclass hook
}
```

Why template over "each subclass formats itself"? Because the framing is shared - To/Subject lines, separators. When the framing changes (add a tenant tag, a compliance footer), it changes HERE once, not in three places. `format()` is `final` so a subclass cannot accidentally skip the compliance framing - the base class guarantees the skeleton.

The three variants earn their classes with real per-channel behaviour:

- **`EmailNotification`** - subject line + full body + a signature footer; validates the recipient contains `@`.
- **`SmsNotification`** - no subject (base skips empty), 160-character hard truncation (carrier reality); validates a 10-13 digit phone number.
- **`PushNotification`** - title/alert body, visually different framing (showing the `openSeparator`/`closeSeparator` hooks earning their keep); validates a `device:`-prefixed token.

**THE KEY FIELD: `notificationId`.** At-least-once delivery means the SAME logical notification can be attempted or delivered more than once - network retries, worker crashes after-send-before-ack, broker redrives on rebalance. Without a stable producer-side id, dedupe is impossible; without dedupe, at-least-once means "the user got the OTP twice". Identity is a **requirement of the delivery contract**, not an afterthought - this is the single most important modelling decision in the problem.

`attemptCount` is an `AtomicInteger`: in this design each notification is owned by one worker at a time (polled off the queue), but the counter is also read by other threads during outcomes recording - cheap correctness, no downside.

`withId`/`overrideId` is a small test seam: it lets the demo forge a copy carrying an ALREADY-DELIVERED id (the at-least-once replay). The guard is deliberate: an id may only be forged on a FRESH copy - never after attempts began - so ids stay stable once delivery starts. Only the notification itself can forge its identity (encapsulation); the demo never touches fields.

### `NotificationChannel` / `NotificationPriority` / `NotificationStatus` - enums

Channels and priorities are DATA (routing keys, queue ranks), not behaviour - behaviour differences live in senders and notifications. `NotificationStatus` is the lifecycle vocabulary:

```
QUEUED -> SENT
QUEUED -> (retry) QUEUED -> SENT
QUEUED -> (retries exhausted) DEAD_LETTER
QUEUED -> DUPLICATE         (deduped at sink or enqueue)
QUEUED -> RATE_LIMITED      (deferred by the channel limiter)
```

`FAILED` is a per-ATTEMPT outcome; `DEAD_LETTER` is the terminal NOTIFICATION outcome after the budget is spent. Keeping those two levels separate is what makes the dead-letter log meaningful rather than just a pile of "failed".

### `NotificationSender` + the three mock senders - strategy per channel

Email, SMS, and push are different vendor integrations with different failure modes: SMTP greylists transiently; SMS carriers throttle; push tokens go stale when apps are uninstalled. The dispatcher must not know any of that - it knows "send this, tell me what happened".

The mocks are deliberately **deterministic**, not random:

- **`EmailSender(int... failOnAttempts)`** - a SCRIPT: "attempts 1 and 2 fail, 3 succeeds" is exactly a greylisting story, and the demo tells it identically every run. Random failure rates make demos flaky and hide the retry logic behind luck.
- **`SwitchableSmsSender`** - a runtime-flippable "carrier down" flag. The demo's timeline needs "down for the first wave, recovered by the rate-limit section" - no static script expresses that; a boolean the demo flips between sections does. `AtomicBoolean` because the demo thread flips while workers read.
- **`PushSender(deadToken)`** - one recipient is a dead token: **permanent** failure.

`SendException` carries the load-bearing flag: **transient vs permanent**. Transient failures get retries; permanent failures skip straight to the dead letter - retrying an unregistered token is guaranteed waste. This single distinction is the most useful thing a notification system can know about a failure, and it is encoded in the exception, not in an if-ladder in the dispatcher.

### `RetryPolicy` - injected, reusable

Max attempts + exponential backoff with a cap:

```
backoffBefore(attempt n) = min(base * 2^(n-1), cap)
```

Why exponential: a vendor that just failed N times is probably saturated; constant-interval retries are a self-inflicted DDoS. Why a cap: with `maxAttempts` large, `2^(n-1)` explodes past any sane wait. Why a CLASS: retry budgets are POLICY - billing-critical email might get 6 attempts at seconds-scale; marketing SMS gets 2. Injecting the policy means the dispatcher contains **zero retry branches**; changing a budget is configuration.

Demo scale: base 150ms, cap 400ms, 3 attempts - the whole retry story plays in ~2 seconds. A production base is seconds-to-minutes; say that out loud.

### `TokenBucketRateLimiter` - minimal, per channel

Bucket of `capacity` tokens, refilled at `refillTokensPerSecond`; `allow()` consumes one or returns false. Burst + steady rate - the exact shape of vendor quotas ("10/s with bursts to 50").

Deliberately SIMPLE: this problem should not re-teach rate limiting. The repo's fuller treatment - continuous refill math, a sliding-window alternative, lock-free discussion - is `01-ll-designs/rate_limiter/`; this class is the inlined minimum the dispatcher needs, with a pointer to the full reference.

Why limit at all (the interview point): vendors enforce quotas, and exceeding them gets EVERYTHING throttled - including urgent traffic. A sender-side limiter per channel keeps the vendor-side 429 from ever happening.

`synchronized` is the right amount of thread-safety here: tiny shared state, every worker hits it on every send decision; the lock-free version (an `AtomicReference` to an immutable bucket snapshot) adds subtlety the demo doesn't need - covered in the rate_limiter problem.

### `NotificationAttempt` + `DeliveryOutcome` - immutable history

Why a separate ATTEMPT record instead of a mutable status on the notification: the dead-letter story requires **attempt history** - "3 attempts at these timestamps, all carrier throttles" is the sentence ops needs at 2am. A single status field erases history the moment the next attempt starts. Immutable attempt rows, appended, never rewritten - audit-log reasoning again.

`DeliveryOutcome` is the terminal fold: final status + attempts + a one-line summary for tables. Both are `final`-field immutable because outcomes are facts about the past.

### `DispatcherService` - the heart

Pipeline: **enqueue (dedupe + QUEUED) -> PriorityBlockingQueue -> N workers -> per-channel limiter -> send -> SENT / retry-with-backoff / DEAD_LETTER**, every step recorded.

The four cruxes, each in code:

**1. Priority ordering.** The queue holds `QueueEntry(notification, sequence)` and the comparator is:

```java
int rank = Integer.compare(priorityRank, otherRank);       // HIGH=0 before NORMAL=1
return rank != 0 ? rank : Long.compare(sequence, other);   // then arrival order
```

The **sequence tiebreaker is the detail interviewers miss**: Java's `PriorityQueue`/`PriorityBlockingQueue` makes no ordering guarantee among equal elements - without a tiebreaker, "NORMAL #1 arrived before NORMAL #2" silently stops holding, and users see statement emails arriving before the OTP that preceded them. Rank first, arrival order within rank. (The total order also keeps `compareTo` consistent with `equals` - a correctness requirement of the queue.)

**2. Retry.** A transient failure re-enqueues after the policy's backoff sleep. A **permanent** failure skips retries straight to the dead letter. Budget spent -> dead letter with the reason. The decision tree in `deliver()` is six lines of sequence - sink dedupe? limiter? send? permanent? budget? - and reads as documentation of itself.

**3. At-least-once + idempotency - the sink dedupe.** THE subtle point in the problem: dedupe must happen at the **sink** (delivered-ids set, checked after dequeue), NOT only at enqueue. Why: at-least-once failures strike AFTER a successful send - the worker sends, the process dies before recording, the broker redrives. An enqueue-time-only check never sees that replay. The demo forges exactly this scenario (replaying the OTP's id) and the sink drops it: the user never gets two OTPs.

There is ALSO a cheap enqueue-time check (`enqueuedIds`) that rejects exact duplicate submissions - belt and braces; the sink check is the one that matters and the one to defend in the interview.

**4. Rate limiting.** No token -> the notification is **re-queued** (deferred), never failed. Rate limiting is pacing, not an error. A tiny sleep on the deferral path keeps a deferred notification from busy-looping against an empty bucket.

The workers are `ExecutorService.submit()` loops polling with a **50ms timeout** (a bare `take()` would park forever on an empty queue and block shutdown); `shutdown()` + `awaitTermination` closes cleanly.

---

## Class Relationships

```
DispatcherService  o--  many NotificationSender   : one per channel (strategy map)
DispatcherService  o--  many TokenBucketRateLimiter : one per channel (optional)
DispatcherService  -->  RetryPolicy               : injected budget
DispatcherService  -->  PriorityBlockingQueue<QueueEntry(notification, sequence)>
DispatcherService  -->  ExecutorService           : worker pool
DispatcherService  1--*  DeliveryOutcome          : outcome log
DispatcherService  1--*  DeliveryOutcome          : dead-letter log
DispatcherService  -->  Set<String> deliveredIds / enqueuedIds  : dedupe
Notification <<abstract>>  -->  channel, priority, attempts
EmailNotification / SmsNotification / PushNotification  --|> Notification
EmailSender --> EmailNotification (via channel key)
SwitchableSmsSender / PushSender  ..|>  NotificationSender
SendException  o--  transient: boolean
DeliveryOutcome  1--*  NotificationAttempt  : history
```

No sender depends on the dispatcher; the dispatcher depends only on the `NotificationSender` interface and the notification abstractions.

---

## The Crux Discussions

### At-least-once vs at-most-once

| | At-least-once (implemented) | At-most-once |
|---|---|---|
| Guarantee | Every notification is delivered >= 1 time (retries + redrives) | Delivered 0 or 1 time, never retried |
| Cost | Duplicates must be deduped (notificationId at the sink) | Lost sends are lost |
| Fits | OTPs, payment alerts, security warnings - a missed message costs money/trust | Pure-lossy marketing where duplicates are worse than gaps |

The interview answer: **at-least-once + idempotency** for anything that matters. The idempotency half is what people forget - at-least-once WITHOUT dedupe is just "we sometimes double-send", which for an OTP means a confused user typing the wrong code. The demo's Section 4 exists to prove the dedupe half.

Two dedupe layers in this design, and why both:

- **Enqueue-time** (`enqueuedIds`): catches the producer bug of submitting the same id twice - fails fast, cheap.
- **Sink-time** (`deliveredIds`): catches the at-least-once REPLAY (the failure window after a successful send). This is the load-bearing one.

A production sink would key dedupe per (recipient, notificationId) with a TTL, and the receiving side (the email service, the SMS gateway) does its own idempotency - say that; end-to-end idempotency is a chain, and your dedupe is one link.

### Priority starvation of NORMAL traffic

Strict `(rank, sequence)` ordering means a sustained stream of HIGH notifications starves every NORMAL notification **indefinitely** - the OTP firehose outranks the statements, and statements wait forever. This is the honest trade-off of the priority queue, and the demo prints it rather than hiding it.

The standard fix is **aging**: a notification's effective rank decays with wait time so an old NORMAL eventually outranks a fresh HIGH:

```java
effectiveRank = baseRank * WEIGHT - nanosSinceEnqueued / AGE_COEFFICIENT;
```

Why is it deliberately NOT implemented here? Because it puts a **clock dependency inside the comparator** - `PriorityQueue` re-heapifies only on structural changes, so a decaying rank does not spontaneously re-order a resting heap; you need periodic re-offer of aged entries or a different structure (a delay-queue hybrid, or two queues with weighted round-robin between them). That is a real design conversation - knowing the trade-off and the shape of the fix, and saying "strict priority today, aging when NORMAL latency SLOs exist", is the strong interview answer. Blindly bolting a clock into `compareTo` is the weak one.

The simpler mitigation worth naming: **separate queues per priority + weighted round-robin** (say 3 HIGH : 1 NORMAL turns) - bounded starvation with zero comparator trickery; the cost is losing global strict ordering.

### Ordering vs parallelism per channel

Parallel workers (3 in the demo) give throughput but destroy per-recipient ordering: statement email then OTP email can arrive reversed. Interviewers probe this as "how do you guarantee user X sees messages in order?" The answer is a **trade-off, not a feature**:

- Parallel by default (throughput; most notifications are independent).
- When ordering IS required (a conversation thread, a sequence of security alerts): **sticky routing** - hash the recipient to exactly one worker/queue, so per-recipient order is preserved while cross-recipient parallelism remains. The cost: a hot recipient can no longer be parallelized, and one slow recipient stalls that lane.
- Never "global ordering" - it means one worker for the whole system.

### Retry semantics and the retry budget

The budget (`maxAttempts`) is the knob that turns "retry forever" into "fail loudly": 3 attempts with 150/300/600ms-class backoff (capped) in the demo. What the budget buys:

- **Transient blips** absorbed invisibly (Section 1: greylisted email, 3rd attempt lands).
- **Real outages** surfaced fast: budget spent -> DEAD_LETTER with attempt history - instead of silent retry-forever that no one notices until a customer asks where their OTP went.

What it deliberately does NOT do: retry PERMANENT failures (dead token). Distinguishing transient from permanent is the whole game - a dead token retried 3 times wastes vendor quota and delays the dead-letter that tells you to stop pushing to that install.

---

## Edge Cases and How the Code Handles Them

| Edge case | Behaviour |
|---|---|
| Duplicate submission (same id, never delivered) | Rejected at enqueue, `DUPLICATE` outcome |
| Replay of an ALREADY-DELIVERED id | Sink dedupe drops it; user never double-messaged |
| Transient vendor failure | Backoff -> retry; 3rd attempt typically lands |
| Permanent failure (dead token) | No retries; straight to dead letter |
| Retry budget exhausted | DEAD_LETTER with reason + attempt history |
| Channel over its rate limit | Deferred (re-queued), never failed; pacing, not error |
| Recipient validation (bad email/phone/token) | `IllegalArgumentException` at construction |
| No sender registered for a channel | `IllegalArgumentException` at enqueue (config bug, fail fast) |
| Queue drained, workers idle | Poll with 50ms timeout; prompt shutdown |
| Interrupted worker | Restores interrupt flag; exits loop cleanly |
| Id forged after attempts began | `IllegalStateException` - ids are stable once delivery starts |

---

## Trade-offs Accepted

1. **In-memory queue** - a crash loses queued notifications. Production: a persistent broker (Kafka/SQS) IS the queue, and its redrive policy is the retry loop; the demo's replay section simulates exactly the redrive that a real broker performs.
2. **Strict priority, no aging** - starvation risk documented (see above) rather than half-fixed with a clock inside the comparator.
3. **Simple synchronized token bucket** - the lock-free version is discussed and deferred to the rate_limiter problem; correct-but-simple beats subtle-but-broken in a 45-minute round.
4. **Duplicate submissions vs redeliveries as one id space** - production would separate "producer retried submission" (same id, client-side) from "broker redelivered" (same id, broker-side); both hit the same dedupe here, which is honest for a demo and worth a sentence in the interview.
5. **Millisecond demo timings** - base backoff 150ms, refill 10/s: the whole semantics visible in ~2s. Production numbers are seconds-to-minutes; stated in the demo header and here.
6. **Per-channel, not per-tenant, rate limiting** - the first-order vendor reality; per-tenant composition is an extension question, not built-in scope creep.

---

## Complexity Summary

| Operation | Complexity |
|---|---|
| Enqueue (offer + dedupe checks) | O(log N) + O(1) |
| Worker dequeue | O(log N) |
| Rate-limit check | O(1) (synchronized refill) |
| Send | O(channel formatting) - O(message length) |
| Outcome/dead-letter append | O(1) |
| Full outcome log print | O(N) |

---

## How to Extend (Interview Talking Points)

- **Persisted queue**: Kafka topic per priority (or a priority field); workers in a consumer group; broker redrive = retries; broker DLQ = the dead-letter log. The `DispatcherService` collapses into a consumer; the senders and policies are unchanged - which is the payoff of the seams chosen here.
- **Aging against starvation**: `effectiveRank = baseRank - waitedTime / coefficient` with periodic re-offer of aged entries; or two queues + weighted round-robin (3:1) - simpler, bounded starvation.
- **Per-tenant limits**: a second limiter keyed by tenant id; send requires channel bucket AND tenant bucket.
- **Circuit breaker per channel**: when failure RATE (not count) spikes, trip the channel - queued notifications wait instead of burning vendor quota.
- **Batching**: N notifications per vendor call (email APIs batch well) - a sender-side optimization invisible to the dispatcher.
- **Template rendering**: the `format()` template method becomes a template-engine lookup (Handlebars-class) - the skeleton/hook split survives.
- **Metrics**: `DeliveryOutcome` records are already the data source for SENT rate, retry histogram, dead-letter ratio per channel - wire them to a counter library.
