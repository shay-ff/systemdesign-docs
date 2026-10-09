/**
 * Physical seat category on a screen.
 *
 * REGULAR  - standard rows
 * PREMIUM  - closer to the screen / better audio / recliner-lite
 * VIP      - recliner / couple seats; priced highest
 *
 * Used as the key in {@code PriceCalculator}'s seat-type price map, so adding
 * a new category (e.g. GOLD for select screens) is a one-line change - no
 * branching code anywhere.
 */
public enum SeatType {
    REGULAR("Regular"),
    PREMIUM("Premium"),
    VIP("VIP Recliner");

    private final String label;

    SeatType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
