/**
 * Domain exception: something the BANK (or the ATM hardware) refused, as
 * opposed to a programming error (IllegalArgumentException and
 * IllegalStateException keep covering those). Carrying an AtmResult code makes the failure
 * machine-readable for the audit trail, while getMessage() stays
 * human-readable for the screen.
 */
public class AtmException extends Exception {

    private final AtmResult result;

    public AtmException(AtmResult result, String message) {
        super(message);
        if (result == null) {
            throw new IllegalArgumentException("AtmResult cannot be null");
        }
        this.result = result;
    }

    public AtmResult getResult() {
        return result;
    }
}
