import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Base strategy: base daily rate x booked days, plus a per-km charge for
 * every km driven beyond the total allowance (daily allowance x days).
 * Early returns still pay for the booked days (stated policy).
 */
public class PerDayPricingStrategy implements PricingStrategy {
    private final double perExtraKmRate;

    public PerDayPricingStrategy(double perExtraKmRate) {
        if (perExtraKmRate < 0) {
            throw new IllegalArgumentException(
                "Per-extra-km rate cannot be negative, got " + perExtraKmRate);
        }
        this.perExtraKmRate = perExtraKmRate;
    }

    @Override
    public Reservation.Bill calculate(Vehicle vehicle, Interval window, int actualKm,
                                      LocalDate actualReturn) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null for pricing");
        }
        if (window == null) {
            throw new IllegalArgumentException("Rental window cannot be null for pricing");
        }
        if (actualKm < 0) {
            throw new IllegalArgumentException(
                "Actual km driven cannot be negative, got " + actualKm);
        }
        if (actualReturn == null) {
            throw new IllegalArgumentException("Actual return date cannot be null");
        }

        long days = window.days();
        int allowance = (int) Math.min(Integer.MAX_VALUE,
                vehicle.getDailyKmAllowance() * days);
        double baseAmount = vehicle.getPerDayRate() * days;

        int overageKm = Math.max(0, actualKm - allowance);
        double kmOverage = overageKm * perExtraKmRate;

        List<String> lines = new ArrayList<>();
        lines.add(String.format("Base: INR %.2f/day x %d day(s) = INR %.2f",
                vehicle.getPerDayRate(), days, baseAmount));
        lines.add(String.format("Km allowance: %d km for %d day(s); driven %d km",
                allowance, days, actualKm));
        if (overageKm > 0) {
            lines.add(String.format("Km overage: %d km x INR %.2f/km = INR %.2f",
                    overageKm, perExtraKmRate, kmOverage));
        }

        double total = baseAmount + kmOverage;
        return new Reservation.Bill(days, baseAmount, kmOverage, 0.0,
                round2(total), lines);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
