import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorator-flavoured strategy: wraps a base strategy, then adds a late-return
 * penalty on top — extra days charged at the vehicle's daily rate plus a flat
 * late fee. Delegation instead of duplication: the base computation lives
 * only in the wrapped strategy.
 */
public class LatePenaltyPricingStrategy implements PricingStrategy {
    private final PricingStrategy base;
    private final double flatLateFee;

    public LatePenaltyPricingStrategy(PricingStrategy base, double flatLateFee) {
        if (base == null) {
            throw new IllegalArgumentException(
                "LatePenaltyPricingStrategy needs a non-null base strategy to wrap");
        }
        if (flatLateFee < 0) {
            throw new IllegalArgumentException(
                "Flat late fee cannot be negative, got " + flatLateFee);
        }
        this.base = base;
        this.flatLateFee = flatLateFee;
    }

    @Override
    public Reservation.Bill calculate(Vehicle vehicle, Interval window, int actualKm,
                                      LocalDate actualReturn) {
        // Delegate the base computation (which also validates inputs).
        Reservation.Bill baseBill = base.calculate(vehicle, window, actualKm, actualReturn);

        long lateDays = Math.max(0, ChronoUnit.DAYS.between(window.getEnd(), actualReturn));
        if (lateDays == 0) {
            return baseBill; // on-time return: no penalty lines at all
        }

        double perLateDayRate = vehicle.getPerDayRate();
        double penalty = lateDays * perLateDayRate + flatLateFee;

        List<String> lines = new ArrayList<>(baseBill.getLines());
        lines.add(String.format(
                "LATE RETURN: returned %d day(s) after booked end %s — %d day(s) x INR %.2f + flat INR %.2f = INR %.2f",
                lateDays, window.getEnd(), lateDays, perLateDayRate, flatLateFee, penalty));

        double total = baseBill.getTotal() + penalty;
        return new Reservation.Bill(baseBill.getDays(), baseBill.getBaseAmount(),
                baseBill.getKmOverage(), PerDayPricingStrategy.round2(penalty),
                PerDayPricingStrategy.round2(total), lines);
    }
}
