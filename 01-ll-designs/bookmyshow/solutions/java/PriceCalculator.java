import java.util.EnumMap;
import java.util.Map;

/**
 * Computes a show's price for a set of seats.
 *
 * Price = sum over seats of (base price by SeatType) x (show multiplier).
 *
 * EnumMap keyed by SeatType: adding a new seat category means adding one
 * entry here - no branching anywhere else (OCP). The multiplier lives on the
 * Show (morning shows cheaper, weekend/late-night shows surge), so pricing
 * varies per show without a new strategy class per combination.
 *
 * Money as double is demo-only; production uses integer paise/BigDecimal -
 * say that out loud in the interview, it is an easy point.
 */
public class PriceCalculator {
    private final Map<SeatType, Double> basePriceBySeatType;

    public PriceCalculator(Map<SeatType, Double> basePriceBySeatType) {
        if (basePriceBySeatType == null || basePriceBySeatType.isEmpty()) {
            throw new IllegalArgumentException("Base price map cannot be null/empty");
        }
        for (SeatType type : SeatType.values()) {
            Double price = basePriceBySeatType.get(type);
            if (price == null || price <= 0) {
                throw new IllegalArgumentException("Base price missing/invalid for seat type "
                    + type + " (got " + price + ")");
            }
        }
        this.basePriceBySeatType = new EnumMap<>(basePriceBySeatType);
    }

    /**
     * Total price for the given seats at the given show. Callers pass the
     * ShowSeats they intend to book; the calculator only reads seat types,
     * so it can price both locked and not-yet-locked seats.
     */
    public double calculateTotal(Show show, Iterable<ShowSeat> showSeats) {
        if (show == null) {
            throw new IllegalArgumentException("Show cannot be null");
        }
        if (showSeats == null || !showSeats.iterator().hasNext()) {
            throw new IllegalArgumentException("Seat list cannot be null/empty");
        }
        double total = 0.0;
        for (ShowSeat showSeat : showSeats) {
            SeatType type = showSeat.getSeatType();
            Double base = basePriceBySeatType.get(type);
            if (base == null) {
                throw new IllegalStateException("No base price configured for seat type " + type);
            }
            total += base * show.getPriceMultiplier();
        }
        return round2(total);
    }

    /** Unit price of one seat type for a show (used in the seat-map UI). */
    public double unitPrice(Show show, SeatType seatType) {
        if (show == null) {
            throw new IllegalArgumentException("Show cannot be null");
        }
        Double base = basePriceBySeatType.get(seatType);
        if (base == null) {
            throw new IllegalArgumentException("No base price configured for seat type " + seatType);
        }
        return round2(base * show.getPriceMultiplier());
    }

    /** Demo-grade rounding: 2 decimals. */
    public static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    public Map<SeatType, Double> getBasePrices() {
        return new EnumMap<>(basePriceBySeatType);
    }
}
