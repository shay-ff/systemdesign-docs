/** A sedan/hatchback-style car variant. */
public class Car extends Vehicle {
    public Car(String vehicleId, String licensePlate, double perDayRate, int dailyKmAllowance) {
        super(vehicleId, licensePlate, VehicleType.CAR, perDayRate, dailyKmAllowance);
    }
}
