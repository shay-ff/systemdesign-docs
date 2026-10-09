import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory UserRepository.
 *
 * ConcurrentHashMap rather than synchronized HashMap: the read paths
 * (findById during every expense add) are lock-free, and single-map
 * operations (putIfAbsent) are atomic, which is all the atomicity this
 * repository needs. A ConcurrentSkipListMap would add ordering we don't
 * use; Collections.synchronizedMap would serialize all reads.
 */
public class InMemoryUserRepository implements UserRepository {
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        User existing = users.putIfAbsent(user.getId(), user);
        if (existing != null) {
            throw new IllegalArgumentException(
                "User id '" + user.getId() + "' is already registered");
        }
        return user;
    }

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Collection<User> findAll() {
        return new ArrayList<>(users.values());
    }
}
