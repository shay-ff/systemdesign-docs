import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end demonstration of the logger framework.
 *
 * Sections:
 *   1. Configuration: sinks per level via SinkFactory + LoggerConfig
 *   2. Synchronous logging through the chain (level filtering + routing)
 *   3. Chain pass-through narrative (who handled what)
 *   4. Dynamic sink attach/detach at runtime
 *   5. Async mode: caller returns immediately, worker appends
 *   6. Backpressure: bounded queue overflow with drop-oldest policy
 */
public class LoggerDemo {

    public static void main(String[] args) throws Exception {
        SinkFactory factory = new SinkFactory();

        System.out.println("=== 1. CONFIGURE SINKS (Factory + LoggerConfig) ===");
        LogSink console = factory.create("CONSOLE");
        FileSink fileSink = (FileSink) factory.create("FILE:app.log");
        DbSink dbSink = (DbSink) factory.create("DB:audit_logs");

        // Routing: DEBUG/INFO -> console only; WARN -> console+file; ERROR -> console+file+db
        LoggerConfig config = LoggerConfig.builder(LogLevel.DEBUG)
                .addSink(LogLevel.DEBUG, console)
                .addSink(LogLevel.INFO, console)
                .addSink(LogLevel.WARN, console)
                .addSink(LogLevel.WARN, fileSink)
                .addSink(LogLevel.ERROR, console)
                .addSink(LogLevel.ERROR, fileSink)
                .addSink(LogLevel.ERROR, dbSink)
                .async(false)
                .build();

        LoggerManager manager = new LoggerManager(config);
        System.out.println("Configured: " + manager.config());
        System.out.println("Chain: " + manager.describeChain());
        System.out.println();

        System.out.println("=== 2. SYNCHRONOUS LOGGING (chain routing) ===");
        manager.debug("PaymentService", "Detailed trace of request payload parsing");
        manager.info("PaymentService", "Payment session started for order ORD-1001");
        manager.warn("RiskEngine", "Card BIN falls in high-risk range; manual review queued");
        manager.error("PayoutWorker", "Payout P-55 failed after 3 attempts; queued for inspection");
        System.out.println("Published so far: " + manager.getPublishedCount());
        System.out.println("Suppressed so far: " + manager.getSuppressedCount());
        System.out.println();

        System.out.println("=== 3. LEVEL FILTERING (minimum level raised to WARN) ===");
        manager.reconfigure(LoggerConfig.builder(LogLevel.WARN)
                .addSink(LogLevel.WARN, console)
                .addSink(LogLevel.WARN, fileSink)
                .addSink(LogLevel.ERROR, console)
                .addSink(LogLevel.ERROR, fileSink)
                .addSink(LogLevel.ERROR, dbSink)
                .async(false)
                .build());
        System.out.println("New minimum level: " + manager.config().getMinimumLevel());
        manager.debug("PaymentService", "This DEBUG line should be suppressed");
        manager.info("PaymentService", "This INFO line should be suppressed too");
        manager.warn("RiskEngine", "This WARN line passes the filter");
        manager.error("PayoutWorker", "This ERROR line passes the filter");
        System.out.println("Suppressed by filter: " + manager.getSuppressedCount());
        System.out.println();

        System.out.println("=== 4. DYNAMIC SINK ATTACH/DETACH (Observer) ===");
        FileSink alertSink = (FileSink) factory.create("FILE:alerts.log");
        System.out.println("-- attaching FILE:alerts.log to all routed levels at runtime --");
        manager.attachSink(alertSink);
        manager.error("PayoutWorker", "Second payout failure; alert sink should also receive this");
        System.out.println("-- detaching DB:audit_logs at runtime --");
        manager.detachSink(dbSink);
        manager.error("PayoutWorker", "Third failure; DB sink is detached, so no insert is recorded");
        System.out.println("DB rows recorded: " + dbSink.getInsertedRows().size());
        System.out.println("alerts.log lines: " + alertSink.getLines().size() + " -> "
                + alertSink.getLines().get(alertSink.getLines().size() - 1));
        System.out.println();

        System.out.println("=== 5. ASYNC MODE (background worker) ===");
        manager.reconfigure(LoggerConfig.builder(LogLevel.INFO)
                .addSink(LogLevel.INFO, console)
                .addSink(LogLevel.WARN, console)
                .addSink(LogLevel.ERROR, console)
                .async(true)
                .build());
        System.out.println("Configured async mode; submitting from multiple threads...");
        CountDownLatch done = new CountDownLatch(1);
        Thread producerA = new Thread(() -> {
            manager.info("CheckoutService", "Async INFO from thread A");
            manager.error("CheckoutService", "Async ERROR from thread A");
            done.countDown();
        }, "producer-A");
        Thread producerB = new Thread(() -> {
            manager.warn("InventoryService", "Async WARN from thread B");
        }, "producer-B");
        producerA.start();
        producerB.start();
        producerA.join();
        producerB.join();
        // Wait for the single worker to drain; timeout keeps the demo bounded.
        done.await(2, TimeUnit.SECONDS);
        Thread.sleep(800);
        if (manager.getAsyncDroppedCount() > 0) {
            System.out.println("Async overflow dropped: " + manager.getAsyncDroppedCount());
        }
        System.out.println("Async queue drained by single worker (per-sink FIFO preserved).");
        System.out.println();

        System.out.println("=== 6. BACKPRESSURE (bounded queue, drop-oldest) ===");
        // A tiny queue (2 slots) plus a blocked worker guarantees overflow.
        AsyncAppender tiny = new AsyncAppender(2, AsyncAppender.OverflowPolicy.DROP_OLDEST);
        CountDownLatch gate = new CountDownLatch(1);
        AsyncAppender blocked = new AsyncAppender(4, AsyncAppender.OverflowPolicy.DROP_OLDEST);
        // Simulate a slow worker: first task parks on the gate, filling the queue behind it.
        blocked.submit(() -> {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // Worker is now busy holding the gate: queue fills, then drop-oldest kicks in.
        for (int i = 1; i <= 6; i++) {
            final int seq = i;
            blocked.submit(() -> System.out.println("  worker processed message #" + seq));
        }
        System.out.println("Submitted 6 tasks into a capacity-4 queue while the worker was blocked.");
        System.out.println("Messages dropped by DROP_OLDEST policy: " + blocked.getDroppedCount());
        gate.countDown(); // release the worker
        blocked.shutdown();
        Thread.sleep(400);
        System.out.println("(Oldest messages were evicted so the newest evidence survived.)");
        System.out.println();

        System.out.println("=== 7. SHUTDOWN AND FINAL STATE ===");
        manager.shutdown();
        System.out.println("Total published: " + manager.getPublishedCount());
        System.out.println("Total suppressed: " + manager.getSuppressedCount());
        System.out.println("File app.log lines: " + fileSink.getLines().size());
        System.out.println("DB audit rows: " + dbSink.getInsertedRows().size() + " (2 rows: error before detach)");
        for (String row : dbSink.getInsertedRows()) {
            System.out.println("  " + row);
        }
        System.out.println();
        System.out.println("=== Logger Framework demo complete ===");
    }
}
