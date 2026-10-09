import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Immutable audit-trail entry: one row per attempted transaction, approved
 * or declined. The audit trail is a regulatory requirement for real ATMs
 * (every ISO 8583 exchange is logged with its response code); here it is a
 * synchronized list the demo prints at the end.
 *
 * WHY immutable: audit rows are facts about the past - allowing mutation
 * would let a later bug rewrite history. All fields final, no setters.
 */
public final class TransactionRecord {
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String cardNumber;      // masked when printed
    private final String cardLast4;
    private final TransactionType type;
    private final long amountInPaise;
    private final AtmResult result;
    private final Map<Integer, Integer> notePlan;
    private final String threadName;
    private final LocalDateTime timestamp;

    public TransactionRecord(Card card, TransactionType type, long amountInPaise,
                             AtmResult result, Map<Integer, Integer> notePlan,
                             String threadName) {
        if (card == null) {
            throw new IllegalArgumentException("Card cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("Atm result cannot be null");
        }
        this.cardNumber = card.getCardNumber();
        this.cardLast4 = card.last4();
        this.type = type;
        this.amountInPaise = amountInPaise;
        this.result = result;
        this.notePlan = notePlan;
        this.threadName = threadName;
        this.timestamp = LocalDateTime.now();
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmountInPaise() {
        return amountInPaise;
    }

    public AtmResult getResult() {
        return result;
    }

    /** Cash plan for approved withdrawals; null for every other outcome. */
    public Map<Integer, Integer> getNotePlan() {
        return notePlan;
    }

    /** Single audit line: time | thread | card | type | amount | result. */
    public String auditLine() {
        return String.format("%s | %-9s | ****%s | %-15s | %-10s | %s",
                timestamp.format(TS),
                threadName,
                cardLast4,
                type,
                amountInPaise > 0 ? Account.formatRupees(amountInPaise) : "-",
                result.name());
    }

    @Override
    public String toString() {
        return "TransactionRecord[" + type + " " + Account.formatRupees(amountInPaise)
                + " -> " + result + " at " + timestamp.format(TS) + "]";
    }
}
