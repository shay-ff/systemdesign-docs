import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * The receipt printer: turns a completed transaction's audit record into
 * human-readable receipt text.
 *
 * Kept deliberately DUMB - a pure formatter. It knows nothing about the
 * state machine, the bank, or the dispenser; give it the fields, it prints.
 * That is why it has no state and no logic beyond formatting: a printer is
 * an output device, not a policy holder.
 *
 * Note the rupee-note plan printed on withdrawal receipts (denominations +
 * counts) - Indian ATM receipts print exactly this, so it doubles as domain
 * flavour.
 */
public class ReceiptPrinter {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    /**
     * Builds receipt text for one transaction record. Receipts are printed
     * for declines too (insufficient funds receipts are a real thing), so
     * the record's result drives the status line.
     */
    public String print(Atm atm, TransactionRecord record,
                        Map<Integer, Integer> notePlan, long balanceAfter) {
        if (record == null) {
            throw new IllegalArgumentException("Transaction record cannot be null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("+------------------------------------------+\n");
        sb.append("|            RAZORPAY BANK ATM             |\n");
        sb.append(String.format("| %-40s |%n", atm.getLocation()));
        sb.append(String.format("| %-40s |%n", "Terminal: " + atm.getTerminalId()));
        sb.append(String.format("| %-40s |%n", "Date: " + LocalDateTime.now().format(TS)));
        sb.append("+------------------------------------------+\n");
        sb.append(String.format("| Card:              ****%s%n", record.getCardLast4()));
        sb.append(String.format("| Transaction:       %s%n", record.getType()));
        sb.append(String.format("| Amount:            %s%n",
                record.getAmountInPaise() > 0
                        ? Account.formatRupees(record.getAmountInPaise())
                        : "-"));
        sb.append(String.format("| Result:            %s%n", record.getResult()));
        if (notePlan != null && !notePlan.isEmpty()) {
            sb.append("| Denominations:     ");
            boolean first = true;
            for (Map.Entry<Integer, Integer> e : notePlan.entrySet()) {
                if (!first) {
                    sb.append("|                    ");
                }
                sb.append(e.getValue()).append(" x INR ").append(e.getKey()).append("\n");
                first = false;
            }
        }
        sb.append(String.format("| Balance available: %s%n", Account.formatRupees(balanceAfter)));
        sb.append("+------------------------------------------+");
        return sb.toString();
    }
}
