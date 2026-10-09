import java.util.Objects;

/**
 * A sellable product. Immutable — prices and codes do not mutate mid-transaction.
 *
 * A product is a CATALOG object (what the machine sells); it is deliberately
 * NOT the inventory (how many it holds). Keeping the two apart is what lets
 * one Product appear in many slots/shelves and lets inventory be a separate,
 * per-code quantity map owned by one component.
 */
public final class Product {

    private final String code;
    private final String name;
    private final int priceInRupees;

    public Product(String code, String name, int priceInRupees) {
        this.code = validateCode(code);
        this.name = validateName(name);
        if (priceInRupees < 1) {
            throw new IllegalArgumentException(
                    "Product price must be at least 1 rupee, got " + priceInRupees
                    + " for " + this.name);
        }
        this.priceInRupees = priceInRupees;
    }

    private static String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Product code cannot be null or empty");
        }
        return code.trim();
    }

    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }
        return name.trim();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getPriceInRupees() {
        return priceInRupees;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product)) {
            return false;
        }
        Product other = (Product) o;
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return name + " (code " + code + ", INR " + priceInRupees + ")";
    }
}
