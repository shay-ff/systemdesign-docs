/** Immutable location value object: city + pincode + street address. */
public final class Location {
    private final String city;
    private final String pincode;
    private final String address;

    public Location(String city, String pincode, String address) {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("Location city cannot be null or empty");
        }
        if (pincode == null || pincode.trim().isEmpty()) {
            throw new IllegalArgumentException("Location pincode cannot be null or empty");
        }
        this.city = city.trim();
        this.pincode = pincode.trim();
        this.address = address == null ? "" : address.trim();
    }

    public String getCity() {
        return city;
    }

    public String getPincode() {
        return pincode;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return city + " " + pincode + (address.isEmpty() ? "" : " - " + address);
    }
}
