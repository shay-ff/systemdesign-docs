/** Renter identity: a customer with a driving licence. */
public class Customer {
    private final String customerId;
    private final String name;
    private final String drivingLicence;

    public Customer(String customerId, String name, String drivingLicence) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer id cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name cannot be null or empty");
        }
        if (drivingLicence == null || drivingLicence.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer driving licence cannot be null or empty");
        }
        this.customerId = customerId.trim();
        this.name = name.trim();
        this.drivingLicence = drivingLicence.trim();
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getDrivingLicence() {
        return drivingLicence;
    }

    @Override
    public String toString() {
        return name + " (" + customerId + ", licence " + drivingLicence + ")";
    }
}
