import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A group of users sharing expenses (flat-share, trip, dinner club...).
 * Owns the list of members and the expenses added against it.
 *
 * A group is also the natural scope for "settle up this group" - net balances
 * are computed per group, which is exactly what the real Splitwise UI does.
 */
public class Group {
    private final String id;
    private final String name;
    private final Set<String> memberIds;
    private final List<Expense> expenses;

    public Group(String id, String name, Set<String> initialMemberIds) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Group id cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be null or empty");
        }
        if (initialMemberIds == null || initialMemberIds.isEmpty()) {
            throw new IllegalArgumentException("Group must be created with at least one member");
        }
        this.id = id;
        this.name = name.trim();
        this.memberIds = new LinkedHashSet<>(initialMemberIds);
        this.expenses = new ArrayList<>();
    }

    /**
     * Adds a member. Idempotent - re-adding an existing member is a no-op,
     * which keeps callers (and demos) simple.
     */
    public void addMember(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Member id cannot be null or empty");
        }
        memberIds.add(userId);
    }

    /**
     * Removes a member. Rejected while the member still owes or is owed
     * money in this group - otherwise their balances would become
     * unenforceable.
     */
    public void removeMember(String userId, BalanceService balances) {
        if (!memberIds.contains(userId)) {
            throw new IllegalArgumentException(
                "User " + userId + " is not a member of group " + name);
        }
        if (balances != null && balances.userHasOpenBalanceInGroup(id, userId)) {
            throw new IllegalStateException("User " + userId
                + " still has open balances in group '" + name + "' and cannot be removed."
                + " Settle up first.");
        }
        memberIds.remove(userId);
    }

    public boolean hasMember(String userId) {
        return memberIds.contains(userId);
    }

    /**
     * Records an expense against this group. Validates that the payer and
     * every participant are members - an expense involving a non-member is a
     * modelling error, not a runtime condition to swallow.
     */
    public void addExpense(Expense expense) {
        if (expense == null) {
            throw new IllegalArgumentException("Expense cannot be null");
        }
        if (!memberIds.contains(expense.getPaidByUserId())) {
            throw new IllegalArgumentException("Payer " + expense.getPaidByUserId()
                + " is not a member of group '" + name + "'");
        }
        for (Split split : expense.getSplits()) {
            if (!memberIds.contains(split.getUserId())) {
                throw new IllegalArgumentException("Participant " + split.getUserId()
                    + " is not a member of group '" + name + "'");
            }
        }
        expenses.add(expense);
    }

    public Set<String> getMemberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + " (" + memberIds.size() + " members, " + expenses.size() + " expenses)";
    }
}
