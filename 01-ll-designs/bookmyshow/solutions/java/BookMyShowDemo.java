import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end BookMyShow walkthrough.
 *
 * Scenario (a Friday evening in Bengaluru): Priya searches for a movie,
 * locks two seats, a second user (Arjun) collides on the same seat and is
 * rejected, one of Priya's holds expires and is released by the sweeper
 * (then re-locked by Arjun), Priya's payment succeeds and confirms with a
 * total price, a payment FAILURE path releases seats, an idempotent
 * double-confirm is shown, and finally the admin schedules a new show.
 *
 * Lock expiry is 3 seconds so the demo finishes fast; production holds last
 * 5-10 minutes.
 *
 * Run instructions: see solutions/java/README.md
 */
public class BookMyShowDemo {

    public static void main(String[] args) throws InterruptedException {
        Map<SeatType, Double> basePrices = new EnumMap<>(SeatType.class);
        basePrices.put(SeatType.REGULAR, 200.0);
        basePrices.put(SeatType.PREMIUM, 350.0);
        basePrices.put(SeatType.VIP, 600.0);
        PriceCalculator priceCalculator = new PriceCalculator(basePrices);

        // 3-second holds, sweeper every 2 seconds - short so the demo is quick.
        // The SAME mock instance is passed to the service AND kept by the demo
        // so outcomes can be scripted (see Step 8's failure script).
        MockPaymentGateway gateway = new MockPaymentGateway();
        BookingService bookingService = new BookingService(
            new InMemoryBookingRepository(), gateway,
            priceCalculator, 3, 2);
        SearchService searchService = new SearchService();
        AdminService adminService = new AdminService(searchService, bookingService);

        System.out.println("=== BookMyShow: Setup (admin lays out the catalog) ===");
        adminService.registerCity("Bengaluru");
        adminService.registerTheatre("Bengaluru", "th1", "PVR Koramangala");
        // Screen 1: rows A-B REGULAR (A1..A10, B1..B10), C PREMIUM, D VIP.
        adminService.addScreen("th1", "scr1", "Audi 1", new String[][]{
            {"A", "1", "10", "REGULAR"},
            {"B", "1", "10", "REGULAR"},
            {"C", "1", "6", "PREMIUM"},
            {"D", "1", "4", "VIP"},
        });
        Movie kanta = new Movie("m1", "Kantara", "Kannada", 148, "Action");
        Movie brahma = new Movie("m2", "Brahmastra", "Hindi", 166, "Fantasy");
        adminService.scheduleShow("th1", "scr1", "show1", kanta,
            LocalDateTime.of(2026, 10, 2, 18, 0), 1.0);
        adminService.scheduleShow("th1", "scr1", "show2", brahma,
            LocalDateTime.of(2026, 10, 2, 21, 30), 1.2);
        bookingService.start();
        System.out.println("Booking engine started: 3s seat holds, sweeper every 2s.");

        System.out.println();
        System.out.println("=== Step 1: Priya searches for shows in Bengaluru ===");
        System.out.println("Search: city=Bengaluru, movie='Kantara', evening (after 17h)");
        List<Show> found = searchService.findShows("Bengaluru", "Kantara", 17, null);
        for (Show show : found) {
            System.out.println("  Found " + show);
        }
        Show show1 = bookingService.getShow("show1");
        printSeatMap(show1, priceCalculator);

        System.out.println();
        System.out.println("=== Step 2: Priya locks seats C1 + C2 (premium) ===");
        System.out.println("lockSeats -> a HOLD, not a booking. Seats flip AVAILABLE -> LOCKED");
        System.out.println("for 3 seconds; payment runs AFTER the locks are released (holds,");
        System.out.println("not mutexes, protect the seats during payment).");
        SeatHold priyaHold = bookingService.lockSeats("show1", "priya",
            java.util.Arrays.asList("scr1-C1", "scr1-C2"));
        System.out.println("  LOCKED: " + priyaHold);
        System.out.println("  Price preview: scr1-C1 (Premium) Rs."
            + String.format("%.2f", priceCalculator.unitPrice(show1, SeatType.PREMIUM))
            + " x 2 = Rs." + String.format("%.2f", priyaHold.getTotalAmount()));
        printSeatMap(show1, priceCalculator);

        System.out.println();
        System.out.println("=== Step 3: Arjun tries to lock C2 - rejected (already LOCKED) ===");
        System.out.println("Two users race for one seat: pessimistic locking means the loser");
        System.out.println("WAITS for the winner's lock, then reads the truth and is rejected -");
        System.out.println("all-or-nothing, so nothing of Arjun's is locked.");
        try {
            bookingService.lockSeats("show1", "arjun",
                java.util.Arrays.asList("scr1-C2", "scr1-A1"));
        } catch (BookingService.SeatUnavailableException e) {
            System.out.println("  REJECTED: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== Step 4: A hold expires - the sweeper frees the seat ===");
        System.out.println("Priya's tab crashes before payment. Her 3s hold lapses; the expiry");
        System.out.println("sweeper (ScheduledExecutorService) moves her seats back to AVAILABLE.");
        System.out.println("Waiting 4 seconds for the hold to expire...");
        Thread.sleep(4000);
        System.out.println("Seat C1 state now: " + show1.getShowSeat("scr1-C1").getStatus());
        System.out.println("Seat C2 state now: " + show1.getShowSeat("scr1-C2").getStatus());

        System.out.println();
        System.out.println("=== Step 5: Arjun re-locks the freed seat - succeeds ===");
        SeatHold arjunHold = bookingService.lockSeats("show1", "arjun",
            java.util.Arrays.asList("scr1-C2", "scr1-A1"));
        System.out.println("  LOCKED: " + arjunHold);
        printSeatMap(show1, priceCalculator);

        System.out.println();
        System.out.println("=== Step 6: Arjun pays - seats LOCKED -> BOOKED ===");
        System.out.println("Payment runs OUTSIDE the per-seat mutexes; the LOCKED status is");
        System.out.println("what protects the seats during the gateway call.");
        Booking arjunBooking = bookingService.confirmHold(arjunHold, "bk-1001");
        System.out.println("  CONFIRMED: " + arjunBooking);
        System.out.println("  Seat states: " + show1.getShowSeat("scr1-C2") + ", "
            + show1.getShowSeat("scr1-A1"));
        printSeatMap(show1, priceCalculator);

        System.out.println();
        System.out.println("=== Step 7: Idempotency - Arjun double-clicks Pay ===");
        System.out.println("The SAME bookingId ('bk-1001') is retried (double-click / webhook");
        System.out.println("redelivery). The idempotency cache returns the ORIGINAL booking -");
        System.out.println("no second charge, no seat re-mutation.");
        Booking again = bookingService.confirmHold(arjunHold, "bk-1001");
        System.out.println("  Returned: " + again);

        System.out.println();
        System.out.println("=== Step 8: Meera locks seats but her payment FAILS ===");
        System.out.println("On payment failure the seats are released IMMEDIATELY (no need to");
        System.out.println("wait for the sweeper - we already know the outcome).");
        SeatHold meeraHold = bookingService.lockSeats("show1", "meera",
            java.util.Arrays.asList("scr1-D1", "scr1-D2"));
        System.out.println("  LOCKED: " + meeraHold);
        gateway.script(PaymentResult.failure("card declined by issuer (insufficient funds)"));
        try {
            bookingService.confirmHold(meeraHold, "bk-2002");
        } catch (BookingService.PaymentFailedException e) {
            System.out.println("  PAYMENT FAILED: " + e.getMessage());
        }
        System.out.println("  Seat D1 state now: " + show1.getShowSeat("scr1-D1").getStatus());
        System.out.println("  Seat D2 state now: " + show1.getShowSeat("scr1-D2").getStatus());
        printSeatMap(show1, priceCalculator);

        System.out.println();
        System.out.println("=== Step 9: Admin schedules a late-night show - live immediately ===");
        Movie tumbbad = new Movie("m3", "Tumbbad", "Hindi", 104, "Horror");
        adminService.scheduleShow("th1", "scr1", "show3", tumbbad,
            LocalDateTime.of(2026, 10, 2, 23, 15), 0.9);
        System.out.println("Search again: city=Bengaluru, after 22h:");
        for (Show show : searchService.findShows("Bengaluru", null, 22, null)) {
            System.out.println("  Found " + show);
        }

        System.out.println();
        System.out.println("=== Demo Complete ===");
        bookingService.shutdown();
        System.out.println("All holds either confirmed (BOOKED) or released (AVAILABLE);");
        System.out.println("no stranded inventory, no double bookings.");
    }

    // ---------------------------------------------------------------- helpers

    /** Compact seat map: one line per row with '.' free, 'L' locked, 'B' booked. */
    private static void printSeatMap(Show show, PriceCalculator prices) {
        Map<String, List<ShowSeat>> byRow = new java.util.LinkedHashMap<>();
        for (ShowSeat ss : show.getShowSeats()) {
            byRow.computeIfAbsent(ss.getSeat().getRow(), r -> new ArrayList<>())
                .add(ss);
        }
        System.out.println("  Seat map for " + show.getShowId() + " (.=available, L=locked, B=booked):");
        for (Map.Entry<String, List<ShowSeat>> row : byRow.entrySet()) {
            StringBuilder sb = new StringBuilder("    " + row.getKey() + ": ");
            for (ShowSeat ss : row.getValue()) {
                char c;
                switch (ss.getStatus()) {
                    case BOOKED: c = 'B'; break;
                    case LOCKED: c = 'L'; break;
                    default: c = '.'; break;
                }
                sb.append(c);
            }
            SeatType type = row.getValue().get(0).getSeatType();
            sb.append("   [").append(type.getLabel()).append(" Rs.")
                .append(String.format("%.2f", prices.unitPrice(show, type))).append("]");
            System.out.println(sb.toString());
        }
    }
}
