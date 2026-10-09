import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock SMS sender whose failure mode the DEMO can flip at runtime.
 *
 * WHY this class (on top of EmailSender's static script): the demo's story
 * has a timeline - SMS is down for the first wave (retry-then-dead-letter),
 * then the carrier recovers and Section 4's rate-limiting notifications go
 * through. A statically scripted sender cannot express "down now, up
 * later"; a boolean the demo flips between sections can. This is exactly
 * how real incidents feel: the channel's health is a moving target.
 *
 * AtomicBoolean because the demo thread flips it while worker threads read
 * it - the same cheap thread-safety reasoning as Notification's attempt
 * counter.
 */
public class SwitchableSmsSender implements NotificationSender {

    private final AtomicBoolean gatewayDown = new AtomicBoolean(true);

    /** @param down true while the mock carrier is "down" */
    public void setGatewayDown(boolean down) {
        gatewayDown.set(down);
        if (!down) {
            System.out.println("  [carrier] SMS gateway RECOVERED (carrier back online)");
        }
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification) throws SendException {
        if (gatewayDown.get()) {
            throw new SendException("SMS gateway rejected attempt "
                    + notification.getAttemptCount() + " (carrier outage - transient)", true);
        }
        System.out.println("    [SMS delivered] " + notification.format());
    }
}
