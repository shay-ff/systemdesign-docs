import java.time.LocalDate;
import java.util.List;

/**
 * Car Rental System demo.
 *
 * Narrative:
 *   1. Two stores across two cities, fleet seeded with cars, SUVs, bikes, trucks.
 *   2. Browse stores in Bengaluru; search SUVs for a window.
 *   3. Attempt a double booking -> conflict detected and rejected.
 *   4. Happy-path booking on another vehicle (back-to-back window allowed).
 *   5. Pickup + late return with penalty (km overage too).
 *   6. Cancel a scheduled reservation -> refund.
 *   7. Validation and lifecycle guards.
 *   8. Payment ledger summary.
 */
public class CarRentalDemo {

    public static void main(String[] args) {
        System.out.println("=== Car Rental System Demo ===\n");

        LocalDate today = LocalDate.of(2026, 10, 1);

        // --- 1. Set up the company: pricing, payments, stores in 2 cities ---
        System.out.println("=== Section 1: Two stores across two cities ===");
        PaymentService payments = new PaymentService();
        PricingStrategy pricing = new LatePenaltyPricingStrategy(
                new PerDayPricingStrategy(15.0), // INR 15 per km over allowance
                500.0);                          // flat INR 500 late fee
        BookingService bookingService = new BookingService(pricing, payments);

        Store blrKoramangala = new Store("STORE-BLR-1",
                new Location("Bengaluru", "560035", "80 Feet Road, Koramangala"));
        Store blrAirport = new Store("STORE-BLR-2",
                new Location("Bengaluru", "560017", "Terminal 2, Kempegowda Airport"));
        Store delhiAerocity = new Store("STORE-DEL-1",
                new Location("Delhi", "110037", "Aerocity Ring Road"));

        blrKoramangala.getInventory().addVehicle(
                new Car("CAR-BLR-01", "KA01AB1234", 2500.0, 200));
        blrKoramangala.getInventory().addVehicle(
                new Suv("SUV-BLR-01", "KA02XY9999", 4200.0, 300));
        blrKoramangala.getInventory().addVehicle(
                new Suv("SUV-BLR-02", "KA03PQ7777", 4500.0, 300));
        blrKoramangala.getInventory().addVehicle(
                new Bike("BIKE-BLR-01", "KA05ZZ0001", 500.0, 150));

        blrAirport.getInventory().addVehicle(
                new Car("CAR-BLR-02", "KA04CD5678", 3000.0, 250));

        delhiAerocity.getInventory().addVehicle(
                new Truck("TRK-DEL-01", "DL01LM4444", 7000.0, 400));
        delhiAerocity.getInventory().addVehicle(
                new Car("CAR-DEL-01", "DL02GH2345", 2800.0, 200));

        bookingService.addStore(blrKoramangala);
        bookingService.addStore(blrAirport);
        bookingService.addStore(delhiAerocity);

        System.out.println(blrKoramangala);
        System.out.println(blrAirport);
        System.out.println(delhiAerocity);
        System.out.println("Pricing: per-day base + INR 15/km over allowance + late penalty (INR 500 flat + day rate)\n");

        Customer ananya = new Customer("CUST-1", "Ananya Rao", "KA0120250001234");
        Customer vikram = new Customer("CUST-2", "Vikram Singh", "DL0120240005678");

        // --- 2. Browse by city and type ---
        System.out.println("=== Section 2: Browse stores in Bengaluru, search SUVs Oct 2-4 ===");
        List<Store> bengaluruStores = bookingService.findStores("Bengaluru");
        System.out.println("Found " + bengaluruStores.size() + " store(s) in Bengaluru:");
        for (Store s : bengaluruStores) {
            System.out.println("  " + s);
        }
        // Interval(pickup Oct 2, drop-off Oct 4) = 2 rental days.
        Interval oct2To4 = new Interval(today.plusDays(1), today.plusDays(3));
        List<Vehicle> suvs = bookingService.searchAvailable(
                bengaluruStores.get(0), VehicleType.SUV, oct2To4);
        System.out.println("\nAvailable SUVs at " + bengaluruStores.get(0).getStoreId()
                + " for [pickup Oct 2, drop-off Oct 4]:");
        for (Vehicle v : suvs) {
            System.out.println("  " + v);
        }
        System.out.println();

        // --- 3. Double-booking attempt -> conflict detected ---
        System.out.println("=== Section 3: Double-booking attempt on the SAME SUV, Oct 3-5 ===");
        Vehicle suv = suvs.get(0);
        System.out.println("Ananya books " + suv.getVehicleId() + " for " + oct2To4);
        Reservation ananyaTrip = bookingService.book(blrKoramangala, suv, ananya, oct2To4);

        Interval oct3To5 = new Interval(today.plusDays(2), today.plusDays(4));
        System.out.println("\nVikram now tries to book the SAME vehicle for " + oct3To5
                + " (overlaps Ananya's booking):");
        try {
            bookingService.book(blrKoramangala, suv, vikram, oct3To5);
            System.out.println("  BUG: overlapping booking was accepted!");
        } catch (IllegalStateException e) {
            System.out.println("  CONFLICT DETECTED -> booking rejected");
            System.out.println("  Reason: " + e.getMessage());
        }

        System.out.println("\nBack-to-back (touching) window drop-off Oct 4 -> pickup Oct 4 must still be ALLOWED:");
        Interval oct4To6 = new Interval(today.plusDays(3), today.plusDays(5));
        try {
            Reservation backToBack = bookingService.book(blrKoramangala, suv, vikram, oct4To6);
            System.out.println("  ACCEPTED as " + backToBack.getReservationId()
                    + " (Ananya's drop-off day == Vikram's pickup day: touching windows do not overlap)");
            // Free the vehicle again so the happy-path flow stays clean.
            bookingService.cancel(backToBack.getReservationId());
        } catch (IllegalStateException e) {
            System.out.println("  REJECTED — overlap rule too strict! " + e.getMessage());
        }
        System.out.println();

        // --- 4. Happy-path flow on the booked reservation ---
        System.out.println("=== Section 4: Happy-path reservation ===");
        System.out.println(ananyaTrip);
        System.out.println();

        // --- 5. Pickup and late return with penalty + km overage ---
        System.out.println("=== Section 5: Pickup, then LATE return with penalty ===");
        bookingService.pickup(ananyaTrip.getReservationId());

        // Booked pickup Oct 2, drop-off Oct 4 (2 days), allowance 300km/day = 600km.
        // Ananya returns on Oct 6 (2 days late) with 750 km (150 km over allowance).
        System.out.println("\nAnanya was due Oct 4 but returns Oct 6 with 750 km driven"
                + " (allowance 600 km):");
        Reservation returned = bookingService.returnVehicle(
                ananyaTrip.getReservationId(), 750, today.plusDays(5));
        System.out.println("\n  -> " + returned.getReservationId() + " final status: "
                + returned.getStatus());
        System.out.println();

        // --- 6. Cancel a scheduled reservation ---
        System.out.println("=== Section 6: Cancel a scheduled reservation ===");
        Reservation vikramTrip = bookingService.book(blrKoramangala,
                suvs.get(1), vikram, new Interval(today.plusDays(10), today.plusDays(12)));
        System.out.println("Vikram booked: " + vikramTrip);
        bookingService.cancel(vikramTrip.getReservationId());
        System.out.println("  -> status now " + bookingService
                .getReservation(vikramTrip.getReservationId()).getStatus());
        System.out.println();

        // --- 7. Validation and lifecycle guards ---
        System.out.println("=== Section 7: Validation and lifecycle guards ===");
        try {
            new Interval(today.plusDays(5), today.plusDays(2));
        } catch (IllegalArgumentException e) {
            System.out.println("  Rejected inverted interval: " + e.getMessage());
        }
        try {
            bookingService.getReservation("RES-999");
        } catch (IllegalArgumentException e) {
            System.out.println("  Rejected unknown reservation: " + e.getMessage());
        }
        try {
            bookingService.cancel(ananyaTrip.getReservationId());
        } catch (IllegalStateException e) {
            System.out.println("  Rejected cancel of COMPLETED trip: " + e.getMessage());
        }
        try {
            bookingService.pickup(vikramTrip.getReservationId());
        } catch (IllegalStateException e) {
            System.out.println("  Rejected pickup of CANCELLED reservation: " + e.getMessage());
        }
        try {
            bookingService.returnVehicle(ananyaTrip.getReservationId(), 10, today.plusDays(6));
        } catch (IllegalStateException e) {
            System.out.println("  Rejected double return: " + e.getMessage());
        }
        System.out.println();

        // --- 8. Payment ledger ---
        System.out.println("=== Section 8: Payment ledger ===");
        for (Payment p : payments.getHistory()) {
            System.out.println("  " + p);
        }

        // --- Fleet extensibility note (OCP) ---
        System.out.println("\n=== Section 9: Fleet extensibility (OCP proof) ===");
        System.out.println("  Adding Bike and Truck needed only new subclasses + enum values;");
        System.out.println("  BookingService, Store, and VehicleInventory were never touched.");
        List<Vehicle> delhiTrucks = bookingService.searchAvailable(
                delhiAerocity, VehicleType.TRUCK, oct2To4);
        for (Vehicle v : delhiTrucks) {
            System.out.println("  " + v);
        }

        System.out.println("\n=== Demo Complete ===");
    }
}
