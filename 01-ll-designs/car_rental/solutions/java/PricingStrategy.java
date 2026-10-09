/**
 * Strategy interface: computes the final bill for a (possibly late) return.
 * Pricing rules vary independently of booking flow — inject a different
 * strategy to change pricing without touching BookingService (OCP).
 */
public interface PricingStrategy {
    /**
     * @param vehicle       the rented vehicle (rate + allowance source)
     * @param window        the booked rental window
     * @param actualKm      km driven (odometer delta at return)
     * @param actualReturn   the date the vehicle was actually returned
     * @return an itemized Bill
     */
    Reservation.Bill calculate(Vehicle vehicle, Interval window, int actualKm,
                               java.time.LocalDate actualReturn);
}
