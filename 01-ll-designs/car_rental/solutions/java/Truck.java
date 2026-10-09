/**
 * A truck variant, added alongside Bike to show fleet extension
 * requires only a new subclass + enum value (plus pricing config).
 */
public class Truck extends Vehicle {
    public Truck(String vehicleId, String licensePlate, double perDayRate, int dailyKmAllowance) {
        super(vehicleId, licensePlate, VehicleType.TRUCK, perDayRate, dailyKmAllowance);
    }
}
