/**
 * A two-wheeler variant, added to prove the OCP claim:
 * no service or inventory class was modified to support bikes.
 */
public class Bike extends Vehicle {
    public Bike(String vehicleId, String licensePlate, double perDayRate, int dailyKmAllowance) {
        super(vehicleId, licensePlate, VehicleType.BIKE, perDayRate, dailyKmAllowance);
    }
}
