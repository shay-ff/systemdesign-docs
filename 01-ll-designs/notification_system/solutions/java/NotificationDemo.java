import java.util.HashMap;
import java.util.Map;

/**
 * Notification system demo - the full narrative, five sections:
 *
 *   1. Mixed channels + priorities enqueued together; watch the priority
 *      queue serve HIGH before earlier-enqueued NORMAL, then a transient
 *      email failure retry through to SENT.
 *   2. SMS carrier outage -> retry budget exhausted -> DEAD-LETTER, plus a
 *      PERMANENT push failure that skips retries entirely.
 *   3. Carrier recovers; 4 SMS at once against a 2-burst/10-per-s bucket
 *      -> rate limiting defers the overflow (never fails).
 *   4. At-least-once redelivery replay of an already-delivered id -> sink
 *      dedupe drops it; a duplicate submission is dropped at enqueue.
 *   5. Summary: one line per terminal outcome + the dead-letter log.
 *
 * Timings are demo-scale: base backoff 150ms (cap 400ms), token refill
 * 10/s. Total runtime ~2 seconds. Run: `javac *.java && java NotificationDemo`.
 */
public class NotificationDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Notification System Demo (priority + retry + rate limit + dedupe) ===\n");

        // ---- Channel senders with DETERMINISTIC failure behaviour ------
        // Email: attempts 1-2 scripted to fail (greylist), attempt 3 passes.
        // SMS: "carrier down" until the demo flips it back up (Section 3).
        // Push: one dead device token -> PERMANENT failure -> no retries.
        SwitchableSmsSender smsSender = new SwitchableSmsSender();
        Map<NotificationChannel, NotificationSender> senders = new HashMap<>();
        senders.put(NotificationChannel.EMAIL, new EmailSender(1, 2));
        senders.put(NotificationChannel.SMS, smsSender);
        senders.put(NotificationChannel.PUSH, new PushSender("device:dead-token"));

        // ---- Per-channel token buckets ----------------------------------
        // SMS capped hard (burst 2, then 10/s) so Section 3 shows the
        // limiter deferring real sends. Email/push: generous buckets.
        Map<NotificationChannel, TokenBucketRateLimiter> limiters = new HashMap<>();
        limiters.put(NotificationChannel.EMAIL, new TokenBucketRateLimiter(50, 100.0));
        limiters.put(NotificationChannel.SMS, new TokenBucketRateLimiter(2, 10.0));
        limiters.put(NotificationChannel.PUSH, new TokenBucketRateLimiter(50, 100.0));

        RetryPolicy retryPolicy = new RetryPolicy(3, 150, 400);
        DispatcherService dispatcher = new DispatcherService(senders, limiters, retryPolicy, 3);

        System.out.println("Senders: email(fail@1,2) sms(carrier-down-until-flipped) push(dead-token)");
        System.out.println("Limiters: EMAIL burst50/100-per-s, SMS burst2/10-per-s, PUSH burst50/100-per-s");
        System.out.println("Retry: " + retryPolicy + "\n");

        // =================================================================
        System.out.println("=== Section 1: Priority ordering + transient retry to SENT ===");
        System.out.println("Enqueue order: 2 NORMAL emails + 1 NORMAL push first, then 1 HIGH OTP sms.");
        System.out.println("The HIGH OTP must be PICKED UP FIRST despite arriving last.\n");

        String otpId = "NTF-1004"; // remembered for the Section 4 replay

        dispatcher.enqueue(new EmailNotification(
                "priya@example.com", "Weekly statement",
                "Your Razorpay weekly statement is ready.", NotificationPriority.NORMAL));
        dispatcher.enqueue(new EmailNotification(
                "rahul@example.com", "Invoice paid",
                "Invoice INV-2026-0042 has been paid.", NotificationPriority.NORMAL));
        dispatcher.enqueue(new PushNotification(
                "device:pixel-8-priya", "Payment received",
                "You received INR 4,500.00", NotificationPriority.NORMAL));
        dispatcher.enqueue(new SmsNotification(
                "9876543210", "Your OTP is 4291. Valid 10 minutes. Do not share.",
                NotificationPriority.HIGH));

        waitUntilDrained(dispatcher, 8000);
        System.out.println();

        // =================================================================
        System.out.println("=== Section 2: Retries exhausted -> DEAD LETTER (+ permanent skip) ===");
        System.out.println("SMS carrier is down: the OTP sms retries 3x then dead-letters.");
        System.out.println("Push to an uninstalled app: PERMANENT -> no retries at all.\n");

        dispatcher.enqueue(new SmsNotification(
                "9000012345", "Your order has shipped - track it in the app.",
                NotificationPriority.NORMAL));
        dispatcher.enqueue(new PushNotification(
                "device:dead-token", "Offer for you",
                "Flat 10% off on your next order", NotificationPriority.NORMAL));

        waitUntilDrained(dispatcher, 8000);
        System.out.println();

        // =================================================================
        System.out.println("=== Section 3: Carrier recovers; per-channel rate limiting ===");
        System.out.println("SMS bucket allows a burst of 2, then 10/s. Four balance SMS at once:");
        System.out.println("the first 2 pass immediately, the rest are DEFERRED then sent.\n");
        smsSender.setGatewayDown(false); // the carrier comes back up

        dispatcher.enqueue(new SmsNotification("9111111111",
                "Your balance is INR 12,300.00", NotificationPriority.NORMAL));
        dispatcher.enqueue(new SmsNotification("9222222222",
                "Your balance is INR 45,800.00", NotificationPriority.NORMAL));
        dispatcher.enqueue(new SmsNotification("9333333333",
                "Your balance is INR 78,900.00", NotificationPriority.NORMAL));
        dispatcher.enqueue(new SmsNotification("9444444444",
                "Your balance is INR 2,100.00", NotificationPriority.NORMAL));
        waitUntilDrained(dispatcher, 10000);
        System.out.println();

        // =================================================================
        System.out.println("=== Section 4: At-least-once redelivery -> dedupe by notificationId ===");
        System.out.println("The worker sent the OTP, died before acking, and the broker replayed");
        System.out.println("the SAME message (same id). The sink must drop it - no second OTP.\n");

        Notification replayedOtp = new SmsNotification(
                "9876543210", "Your OTP is 4291. Valid 10 minutes. Do not share.",
                NotificationPriority.HIGH).withId(otpId);
        System.out.println("Replaying already-delivered " + replayedOtp
                + " (the carrier itself replays the SAME id)...");
        dispatcher.enqueue(replayedOtp);
        waitUntilDrained(dispatcher, 5000);

        System.out.println("\nAnd a plain duplicate submission of the same id:");
        Notification duplicateSubmission = new SmsNotification(
                "9876543210", "Your OTP is 4291. Valid 10 minutes. Do not share.",
                NotificationPriority.HIGH).withId(otpId);
        dispatcher.enqueue(duplicateSubmission);
        waitUntilDrained(dispatcher, 5000);
        System.out.println();

        // =================================================================
        System.out.println("=== Section 5: Summary ===");
        System.out.println("Terminal outcomes (" + dispatcher.getOutcomes().size() + "):");
        for (DeliveryOutcome outcome : dispatcher.getOutcomes()) {
            System.out.println("  " + outcome.summaryLine());
        }
        System.out.println("Dead-letter log (" + dispatcher.getDeadLetters().size() + "):");
        for (DeliveryOutcome dl : dispatcher.getDeadLetters()) {
            System.out.println("  " + dl.summaryLine());
        }

        dispatcher.shutdown(2000);
        System.out.println("\n=== End of notification demo ===");
    }

    // -------------------------------------------------------------- helpers

    /**
     * Waits until the queue drains and workers go quiet. Polls queue
     * emptiness; busy-wait-with-sleep is fine at demo scale (a production
     * service would expose completion futures / latch metrics instead).
     */
    private static void waitUntilDrained(DispatcherService dispatcher, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (dispatcher.isIdle()) {
                sleepQuietly(200); // let in-flight attempts finish
                if (dispatcher.isIdle()) {
                    return;
                }
            }
            sleepQuietly(50);
        }
        System.out.println("  [demo] WARNING: drain timeout hit");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
