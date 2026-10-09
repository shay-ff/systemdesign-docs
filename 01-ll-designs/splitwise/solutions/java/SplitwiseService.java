import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Facade wiring the pieces together - what a controller would talk to.
 *
 * Sequence for addExpense: validate membership via the group -> compute
 * shares (ExpenseService) -> persist the expense on the group -> book
 * transfers into the ledger (BalanceService). Group.addExpense and the
 * repos do validation; this class orchestrates.
 *
 * Why a facade at all? In the real system controllers call several services;
 * in an LLD interview having ONE entry point keeps the class count honest
 * while still showing service separation behind it.
 */
public class SplitwiseService {
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final SimplifyDebtService simplifyDebtService;

    public SplitwiseService(UserRepository userRepository, GroupRepository groupRepository) {
        this(userRepository, groupRepository,
            new ExpenseService(), new BalanceService(), new SimplifyDebtService());
    }

    public SplitwiseService(UserRepository userRepository, GroupRepository groupRepository,
                            ExpenseService expenseService, BalanceService balanceService,
                            SimplifyDebtService simplifyDebtService) {
        if (userRepository == null || groupRepository == null || expenseService == null
            || balanceService == null || simplifyDebtService == null) {
            throw new IllegalArgumentException("SplitwiseService dependencies cannot be null");
        }
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
        this.simplifyDebtService = simplifyDebtService;
    }

    // ---------------------------------------------------------------- users

    public User addUser(String userId, String name, String email) {
        User user = new User(userId, name, email);
        return userRepository.save(user);
    }

    public Optional<User> getUser(String userId) {
        return userRepository.findById(userId);
    }

    // --------------------------------------------------------------- groups

    public Group createGroup(String groupId, String groupName, String... memberUserIds) {
        if (memberUserIds == null || memberUserIds.length == 0) {
            throw new IllegalArgumentException("A group needs at least one member");
        }
        Set<String> members = new LinkedHashSet<>();
        for (String memberId : memberUserIds) {
            if (!userRepository.findById(memberId).isPresent()) {
                throw new IllegalArgumentException(
                    "Cannot create group: user '" + memberId + "' is not registered");
            }
            members.add(memberId);
        }
        Group group = new Group(groupId, groupName, members);
        return groupRepository.save(group);
    }

    public void addMemberToGroup(String groupId, String userId) {
        Group group = requireGroup(groupId);
        if (!userRepository.findById(userId).isPresent()) {
            throw new IllegalArgumentException(
                "Cannot add member: user '" + userId + "' is not registered");
        }
        group.addMember(userId);
    }

    public Group requireGroup(String groupId) {
        Optional<Group> group = groupRepository.findById(groupId);
        if (!group.isPresent()) {
            throw new IllegalArgumentException("Group not found: " + groupId);
        }
        return group.get();
    }

    // ------------------------------------------------------------- expenses

    /**
     * Adds an expense to a group and books its transfers into the ledger.
     * Everything downstream (shares, ledger) derives from the expense, so
     * this is the only write path for group debt.
     */
    public Expense addExpense(String groupId, Expense expense) {
        Group group = requireGroup(groupId);
        group.addExpense(expense); // validates membership + split integrity
        balanceService.applyTransfers(groupId, expenseService.deriveTransfers(expense));
        return expense;
    }

    // ------------------------------------------------------------- balances

    /** userId -> net amount in paise (positive = is owed money). */
    public Map<String, Long> getNetBalances(String groupId) {
        requireGroup(groupId);
        return balanceService.getNetBalances(groupId);
    }

    /** Raw pairwise debts ("who owes whom") before simplification. */
    public List<Transfer> getPairwiseBalances(String groupId) {
        requireGroup(groupId);
        return balanceService.getPairwiseBalances(groupId);
    }

    /** The group a settlement was booked against - used by the demo's output. */
    public BalanceService getBalanceService() {
        return balanceService;
    }

    /**
     * Suggested minimum set of transfers to settle the group - the greedy
     * min-cash-flow result, derived from current net positions.
     */
    public List<Transfer> suggestSettlement(String groupId) {
        requireGroup(groupId);
        return simplifyDebtService.simplify(balanceService.getNetBalances(groupId));
    }

    /**
     * Records a REAL payment between two users (not a suggestion): fromUser
     * paid toUser amountInPaise.
     *
     * The subtlety: if fromUser owed toUser less than they paid (they may be
     * settling several suggested transfers with one UPI payment), the surplus
     * must not vanish. Rule: first apply the payment against the direct pair
     * (shrinking/flipping that pair), then any remaining surplus is booked as
     * a fresh "toUser owes fromUser" entry. This conserves the group's total
     * net exactly - nothing is created or destroyed.
     */
    public void recordSettlement(String groupId, String fromUserId, String toUserId,
                                 long amountInPaise) {
        Group group = requireGroup(groupId);
        if (!group.hasMember(fromUserId) || !group.hasMember(toUserId)) {
            throw new IllegalArgumentException(
                "Both users must be members of group '" + group.getName() + "'");
        }
        balanceService.recordSettlement(groupId, fromUserId, toUserId, amountInPaise);
    }

    /**
     * Closes out a fully settled group: books the suggested settlement, then
     * clears residual pairwise cycles (see BalanceService.settleUpGroup for
     * why crossing payment routes leave offsetting entries behind).
     */
    public void settleUpGroup(String groupId) {
        requireGroup(groupId);
        for (Transfer payment : suggestSettlement(groupId)) {
            balanceService.recordSettlement(groupId, payment.getFromUserId(),
                payment.getToUserId(), payment.getAmountInPaise());
        }
        balanceService.settleUpGroup(groupId);
    }

    public List<Expense> getGroupExpenses(String groupId) {
        return Collections.unmodifiableList(new ArrayList<>(requireGroup(groupId).getExpenses()));
    }
}
