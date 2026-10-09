/**
 * A person registered with Splitwise. Users are identified by a unique,
 * immutable id; equality is id-based so the same person can appear in many
 * groups without creating duplicate ledger entries.
 *
 * Deliberately kept dumb (no balance fields): balances live in the
 * BalanceService ledger, not on the entity. Anemic? No - a User simply has
 * no behaviour of its own; putting money on it would couple it to groups.
 */
public class User {
    private final String id;
    private final String name;
    private final String email;

    public User(String id, String name, String email) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("User id cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("User email must contain '@': " + email);
        }
        this.id = id;
        this.name = name.trim();
        this.email = email.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        return id.equals(((User) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
