import java.util.*;

/**
 * Demonstration of the Parking Lot System.
 * Shows various operations including parking, unparking, and availability checking.
 */
public class ParkingLotDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Parking Lot System Demo ===\n");
        
        // Create a parking lot with 2 levels
        List<ParkingLevel> levels = Arrays.asList(
            new ParkingLevel(0, 2, 2, 1), // Level 0: 2 motorcycle, 2 compact, 1 large
            new ParkingLevel(1, 1, 2, 1)  // Level 1: 1 motorcycle, 2 compact, 1 large
        );
        
        ParkingLot parkingLot = new ParkingLot("CityCenter Mall", levels);
        
        // Show initial availability
        System.out.println("Initial state:");
        System.out.println(parkingLot);
        System.out.println();
        
        // Create some vehicles
        List<Vehicle> vehicles = Arrays.asList(
            new Vehicle("KA01AB1234", VehicleType.CAR),
            new Vehicle("KA02ZZ9999", VehicleType.MOTORCYCLE),
            new Vehicle("BUS777", VehicleType.BUS),
            new Vehicle("MH12CD5678", VehicleType.CAR),
            new Vehicle("BIKE123", VehicleType.MOTORCYCLE)
        );
        
        // Park vehicles
        List<Ticket> tickets = new ArrayList<>();
        System.out.println("Parking vehicles:");
        for (Vehicle vehicle : vehicles) {
            Ticket ticket = parkingLot.parkVehicle(vehicle);
            if (ticket != null) {
                tickets.add(ticket);
                System.out.println("✓ Parked " + vehicle + " -> " + ticket.getTicketId());
            } else {
                System.out.println("✗ Failed to park " + vehicle + " (lot full)");
            }
        }
        
        System.out.println();
        System.out.println("After parking:");
        System.out.println(parkingLot);
        System.out.println();
        
        // Show active tickets
        System.out.println("Active tickets:");
        for (Ticket ticket : parkingLot.getActiveTickets()) {
            System.out.println("  " + ticket);
        }
        System.out.println();
        
        // Simulate some time passing
        System.out.println("Waiting 2 seconds to simulate parking duration...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Unpark some vehicles
        System.out.println("Unparking vehicles:");
        for (int i = 0; i < Math.min(3, tickets.size()); i++) {
            Ticket ticket = tickets.get(i);
            double fee = parkingLot.unparkVehicle(ticket);
            if (fee >= 0) {
                System.out.printf("✓ Unparked %s, Fee: $%.2f%n", ticket.getLicensePlate(), fee);
            } else {
                System.out.println("✗ Failed to unpark " + ticket.getLicensePlate());
            }
        }
        
        System.out.println();
        System.out.println("Final state:");
        System.out.println(parkingLot);
        
        // Demonstrate error handling
        System.out.println();
        System.out.println("=== Error Handling Demo ===");
        
        // Try to park the same vehicle twice
        Vehicle duplicateVehicle = new Vehicle("KA01AB1234", VehicleType.CAR);
        Ticket duplicateTicket = parkingLot.parkVehicle(duplicateVehicle);
        System.out.println("Attempting to park duplicate vehicle: " + 
                          (duplicateTicket == null ? "Correctly rejected" : "Unexpectedly allowed"));
        
        // Try to unpark with invalid ticket
        Ticket invalidTicket = new Ticket("INVALID-ID", "FAKE123", VehicleType.CAR, 
                                         System.currentTimeMillis(), 0, 0, SpotType.COMPACT);
        double invalidFee = parkingLot.unparkVehicle(invalidTicket);
        System.out.println("Attempting to unpark with invalid ticket: " + 
                          (invalidFee < 0 ? "Correctly rejected" : "Unexpectedly allowed"));
        
        // Show pricing information
        System.out.println();
        System.out.println("=== Pricing Information ===");
        PricingPolicy pricing = new PricingPolicy();
        System.out.printf("Base fee: $%.2f%n", pricing.getBaseFee());
        System.out.printf("Motorcycle hourly rate: $%.2f%n", pricing.getHourlyRateForVehicle(VehicleType.MOTORCYCLE));
        System.out.printf("Car hourly rate: $%.2f%n", pricing.getHourlyRateForVehicle(VehicleType.CAR));
        System.out.printf("Bus hourly rate: $%.2f%n", pricing.getHourlyRateForVehicle(VehicleType.BUS));
        
        System.out.println("\n=== Demo Complete ===");
    }
}