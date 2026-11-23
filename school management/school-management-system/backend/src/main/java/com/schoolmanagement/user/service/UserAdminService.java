package com.schoolmanagement.user.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.service.RefreshTokenService;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.user.dto.UserDtos;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import jakarta.persistence.criteria.Expression;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator tools for accounts: listing, creating administrators, and changing account status. */
@Service
public class UserAdminService {

    private static final String ENTITY = "User";
    private static final Set<AccountStatus> SETTABLE = EnumSet.of(AccountStatus.ACTIVE, AccountStatus.SUSPENDED,
            AccountStatus.ARCHIVED);

    private final UserRepository users;
    private final UserProvisioner provisioner;
    private final AccountRules accountRules;
    private final RefreshTokenService refreshTokens;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public UserAdminService(UserRepository users, UserProvisioner provisioner, AccountRules accountRules,
            RefreshTokenService refreshTokens, AuditEventPublisher audit, Clock clock) {
        this.users = users;
        this.provisioner = provisioner;
        this.accountRules = accountRules;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.Response> search(UUID schoolId, RoleName role, AccountStatus status, String search,
            Pageable pageable) {
        Specification<User> spec = Specification.<User>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("accountStatus", status))
                .and(hasRole(role))
                .and(matchesText(search));
        Page<User> page = users.findAll(spec, pageable);
        // Loads the roles of the whole page in one query; the instances are shared with the page content.
        users.findAllByIdIn(page.getContent().stream().map(User::getId).toList());
        Instant now = clock.instant();
        return PageResponse.from(page, user -> UserDtos.Response.from(user, now));
    }

    @Transactional
    public UserDtos.Response createAdmin(AuthenticatedUser actor, UserDtos.CreateAdminRequest request) {
        UUID schoolId = actor.schoolId();
        accountRules.requireStrongPassword(request.password());
        String email = request.email().trim();
        accountRules.requireAvailableLoginId(schoolId, email, "email");
        accountRules.requireAvailableEmail(schoolId, email, null);
        User user = provisioner.createActiveUser(schoolId, email, email, request.password(),
                request.firstName().trim(), request.lastName().trim(), RoleName.ROLE_ADMIN);
        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank() ? null
                : request.phoneNumber().trim();
        user.updateContact(user.getFirstName(), user.getLastName(), user.getEmail(), phone);
        users.saveAndFlush(user);
        audit.record(actor, "ADMIN_CREATED", ENTITY, user.getId(), "Created administrator " + email);
        return UserDtos.Response.from(user, clock.instant());
    }

    @Transactional
    public UserDtos.Response changeStatus(AuthenticatedUser actor, UUID id, UserDtos.StatusRequest request) {
        if (!SETTABLE.contains(request.status())) {
            throw new BusinessRuleException("The status can only be set to ACTIVE, SUSPENDED or ARCHIVED.");
        }
        if (actor.id().equals(id)) {
            throw new BusinessRuleException("You cannot change the status of your own account.");
        }
        boolean disabling = request.status() != AccountStatus.ACTIVE;
        // Serialises concurrent disables: both would otherwise see the other administrator as still active.
        List<User> activeAdmins = disabling ? users.lockActiveAdministrators(actor.schoolId()) : List.of();
        User user = find(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), user.getVersion());
        AccountStatus previous = user.getAccountStatus();
        if (disabling && previous == AccountStatus.ACTIVE && user.hasRole(RoleName.ROLE_ADMIN)
                && activeAdmins.stream().noneMatch(admin -> !admin.getId().equals(id))) {
            throw new ConflictException("The last active administrator cannot be suspended or archived.");
        }
        user.changeAccountStatus(request.status());
        users.saveAndFlush(user);
        if (disabling) {
            refreshTokens.revokeAllForUser(id);
        }
        audit.record(actor, "ACCOUNT_STATUS_CHANGED", ENTITY, id, "Account " + user.getUsername() + " changed from "
                + previous + " to " + request.status(), java.util.Map.of("accountStatus", previous.name()),
                java.util.Map.of("accountStatus", request.status().name()));
        return UserDtos.Response.from(user, clock.instant());
    }

    @Transactional
    public UserDtos.Response unlock(AuthenticatedUser actor, UUID id) {
        User user = find(actor.schoolId(), id);
        user.unlock();
        users.saveAndFlush(user);
        audit.record(actor, "ACCOUNT_UNLOCKED", ENTITY, id, "Unlocked account " + user.getUsername());
        return UserDtos.Response.from(user, clock.instant());
    }

    private User find(UUID schoolId, UUID id) {
        return users.findWithRolesByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("User"));
    }

    private static Specification<User> hasRole(RoleName role) {
        if (role == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.join("roles").get("name"), role);
    }

    private static Specification<User> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = SearchSpecs.likePattern(search);
        return (root, query, cb) -> {
            Expression<String> first = cb.lower(root.get("firstName"));
            Expression<String> last = cb.lower(root.get("lastName"));
            return cb.or(
                    cb.like(cb.lower(root.get("username")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\'),
                    cb.like(first, pattern, '\\'),
                    cb.like(last, pattern, '\\'),
                    cb.like(cb.concat(cb.concat(first, " "), last), pattern, '\\'));
        };
    }
}
