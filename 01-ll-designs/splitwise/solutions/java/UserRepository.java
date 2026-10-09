import java.util.Optional;

/**
 * Storage contract for users. The service layer depends on this interface,
 * not on an in-memory map - the standard LLD move that lets you say "in
 * production this is Postgres-backed; the service code does not change".
 */
public interface UserRepository {
    /** Registers a new user; fails if the id is already taken. */
    User save(User user);

    Optional<User> findById(String userId);

    java.util.Collection<User> findAll();
}
