import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory GroupRepository. */
public class InMemoryGroupRepository implements GroupRepository {
    private final ConcurrentHashMap<String, Group> groups = new ConcurrentHashMap<>();

    @Override
    public Group save(Group group) {
        Group existing = groups.putIfAbsent(group.getId(), group);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Group id '" + group.getId() + "' already exists");
        }
        return group;
    }

    @Override
    public Optional<Group> findById(String groupId) {
        return Optional.ofNullable(groups.get(groupId));
    }
}
