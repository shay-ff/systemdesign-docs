/**
 * Queue priority. HIGH jumps the queue over any NORMAL notification that was
 * enqueued earlier - see DispatcherService for how the priority queue
 * orders, and explanation.md for the STARVATION discussion (what happens to
 * NORMAL traffic when HIGH never stops arriving - and the aging fix).
 */
public enum NotificationPriority {
    HIGH(0),
    NORMAL(1);

    private final int queueRank; // lower = dispatched first

    NotificationPriority(int queueRank) {
        this.queueRank = queueRank;
    }

    public int getQueueRank() {
        return queueRank;
    }
}
