import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * THE DISPATCHER - the heart of the system.
 *
 * Pipeline: enqueue (dedupe + QUEUED) -> priority queue -> N worker threads
 * -> per-channel rate limiter -> send (via the channel's NotificationSender)
 * -> SENT / retry-with-backoff / DEAD_LETTER, every step recorded.
 *
 * THE FOUR INTERVIEW CRUXES, all implemented here:
 *
 * 1. PRIORITY ORDERING: the PriorityBlockingQueue orders by
 *    (priority rank, sequence number). The sequence tiebreaker is the
 *    often-missed detail: without it, Java's PriorityQueue arbitrarily
 *    reorders EQUAL-priority elements, and "NORMAL #1 arrived before
 *    NORMAL #2" stops holding. Rank first, arrival order within rank.
 *
 * 2. RETRY: transient failures re-enqueue for a later attempt after the
 *    policy's exponential backoff. PERMANENT failures skip retries - the
 *    token is dead; retrying is waste - straight to the dead-letter log.
 *
 * 3. AT-LEAST-ONCE + IDEMPOTENCY: dedupe happens at the SINK (a delivered
 *    notification id is remembered), not at enqueue. Why the sink? Because
 *    at-least-once failures can strike AFTER a successful send - the worker
 *    sends, the process dies before recording, the retry delivers AGAIN.
 *    The only safe dedupe point is after-or-at delivery. The demo's
 *    redelivery section simulates exactly that replay and the sink drops it.
 *
 * 4. RATE LIMITING: every send consults the channel's token bucket first.
 *    No token -> the notification is re-queued for a later pass (deferred,
 *    NOT failed - rate limiting is not an error, it is pacing).
 *
 * STARVATION (the acknowledged trade-off): strict priority means a sustained
 * HIGH stream starves NORMAL traffic indefinitely. The standard fix is
 * AGING: wait time decays a notification's effective rank so an old NORMAL
 * eventually outranks a fresh HIGH. It is deliberately NOT implemented here
 * - it adds a clock dependency into the comparator (see explanation.md for
 * the sketch) - but the demo prints the starvation scenario so the
 * conversation happens. Knowing the trade-off beats blindly fixing it.
 */
public class DispatcherService {

    /** Queue element: notification + arrival sequence (see comparator note). */
    private static final class QueueEntry implements Comparable<QueueEntry> {
        final Notification notification;
        final long sequence;

        QueueEntry(Notification notification, long sequence) {
            this.notification = notification;
            this.sequence = sequence;
        }

        /**
         * Rank first (HIGH=0 before NORMAL=1), THEN arrival sequence -
         * the tiebreaker that keeps same-priority FIFO. compareTo must be
         * CONSISTENT with equals for correct queue behaviour; sequence makes
         * it a total order, which satisfies that trivially.
         */
        @Override
        public int compareTo(QueueEntry other) {
            int rank = Integer.compare(notification.getPriority().getQueueRank(),
                    other.notification.getPriority().getQueueRank());
            return rank != 0 ? rank : Long.compare(sequence, other.sequence);
        }
    }

    private final Map<NotificationChannel, NotificationSender> senders;
    private final Map<NotificationChannel, TokenBucketRateLimiter> limiters;
    private final RetryPolicy retryPolicy;

    private final PriorityBlockingQueue<QueueEntry> queue = new PriorityBlockingQueue<>();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();

    /** Delivered ids: the dedupe sink for at-least-once idempotency. */
    private final Set<String> deliveredIds = ConcurrentHashMap.newKeySet();
    /** Seen ids at enqueue: rejects exact duplicate submissions. */
    private final Set<String> enqueuedIds = ConcurrentHashMap.newKeySet();

    private final ExecutorService workers;
    private final List<DeliveryOutcome> outcomes = new CopyOnWriteArrayList<>();
    private final List<DeliveryOutcome> deadLetters = new CopyOnWriteArrayList<>();

    /**
     * @param senders one sender per channel (at least one)
     * @param limiters one token bucket per channel (may be null: unlimited)
     * @param retryPolicy the shared retry budget
     * @param workerCount worker thread count
     */
    public DispatcherService(Map<NotificationChannel, NotificationSender> senders,
                             Map<NotificationChannel, TokenBucketRateLimiter> limiters,
                             RetryPolicy retryPolicy, int workerCount) {
        if (senders == null || senders.isEmpty()) {
            throw new IllegalArgumentException("At least one sender is required");
        }
        for (Map.Entry<NotificationChannel, NotificationSender> e : senders.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                throw new IllegalArgumentException("Sender map has a null key or value");
            }
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("Retry policy cannot be null");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be positive, got "
                    + workerCount);
        }
        this.senders = new ConcurrentHashMap<>(senders);
        this.limiters = limiters == null ? Collections.emptyMap() : new ConcurrentHashMap<>(limiters);
        this.retryPolicy = retryPolicy;
        this.workers = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workerLoop);
        }
    }

    // ------------------------------------------------------------- enqueue

    /**
     * Accepts a notification for delivery. Duplicate submissions (same
     * notificationId already seen) are rejected with a DUPLICATE outcome -
     * this is the cheap, up-front half of idempotency; the sink-side half
     * (deliveredIds) is what actually guards at-least-once replays.
     */
    public DeliveryOutcome enqueue(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification cannot be null");
        }
        if (!senders.containsKey(notification.getChannel())) {
            throw new IllegalArgumentException("No sender registered for channel "
                    + notification.getChannel());
        }
        if (!enqueuedIds.add(notification.getNotificationId())) {
            System.out.println("  [queue] REJECTED duplicate submission "
                    + notification.getNotificationId() + " (already enqueued)");
            return record(new DeliveryOutcome(notification.getNotificationId(),
                    NotificationStatus.DUPLICATE, Collections.emptyList(),
                    "duplicate submission - dropped at enqueue"));
        }
        queue.offer(new QueueEntry(notification, sequence.incrementAndGet()));
        System.out.println("  [queue] QUEUED " + notification);
        return null; // queued, not yet resolved - outcome comes later
    }

    // -------------------------------------------------------------- workers

    /**
     * Worker loop: poll with a timeout so shutdown is prompt (a plain take()
     * would park forever on an empty queue and block executor shutdown).
     */
    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            QueueEntry entry;
            try {
                entry = queue.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (entry != null) {
                deliver(entry.notification);
            }
        }
    }

    /**
     * One delivery attempt for one notification - the full decision tree:
     *
     *   sink dedupe?      -> DUPLICATE, stop (at-least-once replay absorbed)
     *   rate limiter?     -> re-queue, stop (pacing, not failure)
     *   send ok?          -> SENT, remember id in the sink
     *   permanent fail?   -> DEAD_LETTER, stop (retries are waste)
     *   transient fail?   -> budget left? re-queue after backoff : DEAD_LETTER
     */
    private void deliver(Notification notification) {
        String id = notification.getNotificationId();

        // 1. SINK DEDUPE - the idempotency guarantee.
        if (deliveredIds.contains(id)) {
            System.out.println("  [worker] DEDUPED " + id
                    + " on " + Thread.currentThread().getName()
                    + " - already delivered once (at-least-once replay)");
            record(new DeliveryOutcome(id, NotificationStatus.DUPLICATE,
                    Collections.emptyList(), "replayed delivery deduped by id"));
            return;
        }

        // 2. PER-CHANNEL RATE LIMITER.
        TokenBucketRateLimiter limiter = limiters.get(notification.getChannel());
        if (limiter != null && !limiter.allow()) {
            System.out.println("  [worker] RATE-LIMITED " + id + " ("
                    + notification.getChannel() + " " + limiter + ") - re-queued");
            record(new DeliveryOutcome(id, NotificationStatus.RATE_LIMITED,
                    Collections.emptyList(), notification.getChannel() + " limiter deferred"));
            requeueAfterBackoff(notification);
            return;
        }

        NotificationSender sender = senders.get(notification.getChannel());
        int attempt = notification.incrementAttempt();

        // 3. THE SEND.
        try {
            sender.send(notification);
            deliveredIds.add(id);
            record(new DeliveryOutcome(id, NotificationStatus.SENT,
                    Collections.singletonList(new NotificationAttempt(attempt, "sent")),
                    "delivered on attempt " + attempt));
            return;
        } catch (SendException e) {
            String failure = e.getMessage();
            record(new DeliveryOutcome(id, NotificationStatus.FAILED,
                    Collections.singletonList(new NotificationAttempt(attempt, failure)),
                    "attempt " + attempt + " failed"));

            // 4. PERMANENT failure: no retries, straight to the dead letter.
            if (!e.isTransient()) {
                deadLetter(notification, "PERMANENT failure: " + failure);
                return;
            }

            // 5. TRANSIENT failure: retry with backoff while budget remains.
            if (retryPolicy.canRetry(attempt)) {
                long backoff = retryPolicy.backoffMillisBefore(attempt + 1);
                System.out.println("  [worker] RETRYING " + id + " - attempt " + attempt
                        + " failed (" + failure + "); backing off "
                        + backoff + "ms before attempt " + (attempt + 1));
                sleep(backoff);
                requeue(notification);
                return;
            }

            // 6. Budget exhausted: dead letter with the full story.
            deadLetter(notification, "retries exhausted after " + attempt
                    + " attempts: " + failure);
        }
    }

    /** Re-queues preserving priority; new sequence = fair re-ordering. */
    private void requeue(Notification notification) {
        queue.offer(new QueueEntry(notification, sequence.incrementAndGet()));
    }

    /**
     * Re-queue after a limiter deferral. A small sleep (the limiter's refill
     * window) keeps the deferred notification from busy-looping against a
     * bucket with no tokens - without it, a tight queue would spin the
     * worker through poll/requeue at full CPU.
     */
    private void requeueAfterBackoff(Notification notification) {
        sleep(100); // one refill tick for the demo's bucket sizes
        requeue(notification);
    }

    private void deadLetter(Notification notification, String reason) {
        System.out.println("  [worker] DEAD-LETTER " + notification + " - " + reason);
        deadLetters.add(new DeliveryOutcome(notification.getNotificationId(),
                NotificationStatus.DEAD_LETTER,
                Collections.singletonList(new NotificationAttempt(
                        Math.max(1, notification.getAttemptCount()), reason)),
                reason));
    }

    private DeliveryOutcome record(DeliveryOutcome outcome) {
        // RATE_LIMITED rows are pacing chatter, not delivery history - keep
        // them OUT of the outcomes log (the status is still observable in
        // the narrative line) or the summary table drowns in them.
        if (outcome.getStatus() != NotificationStatus.RATE_LIMITED) {
            outcomes.add(outcome);
        }
        return outcome;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------- shutdown

    /**
     * Graceful stop: no new tasks, drain what is in flight (workers finish
     * their current attempt), up to a timeout.
     */
    public void shutdown(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
        try {
            workers.shutdownNow(); // interrupt parked polls; finish attempts
        } catch (RejectedExecutionException ignored) {
            // already shutting down - benign
        }
        workers.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------- queries

    public List<DeliveryOutcome> getOutcomes() {
        return new ArrayList<>(outcomes);
    }

    public List<DeliveryOutcome> getDeadLetters() {
        return new ArrayList<>(deadLetters);
    }

    /** True when the queue has fully drained (used by the demo to wait). */
    public boolean isIdle() {
        return queue.isEmpty();
    }
}
