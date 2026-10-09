import java.util.Optional;

/** Storage contract for groups. See InMemoryUserRepository rationale. */
public interface GroupRepository {
    Group save(Group group);

    Optional<Group> findById(String groupId);
}
