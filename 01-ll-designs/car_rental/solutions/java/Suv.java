/** An SUV variant — typically higher rate and allowance than a car. */
public class Suv extends Vehicle {
    public Suv(String vehicleId, String licensePlate, double perDayRate, int dailyKmAllowance) {
        super(vehicleId, licensePlate, VehicleType.SUV, perDayRate, dailyKmAllowance);
    }
}
