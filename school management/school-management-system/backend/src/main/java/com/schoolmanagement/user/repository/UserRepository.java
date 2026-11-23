package com.schoolmanagement.user.repository;

import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Loads roles for a page of users in one query. Fetching the role collection inside the paged query would
     * force Hibernate to paginate in memory.
     */
    @EntityGraph(attributePaths = "roles")
    List<User> findAllByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndUsernameIgnoreCase(UUID schoolId, String username);

    boolean existsBySchoolIdAndEmailIgnoreCaseAndIdNot(UUID schoolId, String email, UUID id);

    /**
     * Locks every active administrator row of a school until the transaction ends. Used before suspending or
     * archiving an administrator, so two concurrent requests cannot together remove the last one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u join u.roles r where u.schoolId = :schoolId "
            + "and r.name = com.schoolmanagement.user.entity.RoleName.ROLE_ADMIN "
            + "and u.accountStatus = com.schoolmanagement.user.entity.AccountStatus.ACTIVE order by u.id")
    List<User> lockActiveAdministrators(@Param("schoolId") UUID schoolId);

    /** Matches the login identifier against email or username, case-insensitively. */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.email) = lower(:identifier) or lower(u.username) = lower(:identifier)")
    List<User> findByLoginIdentifier(@Param("identifier") String identifier);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.email) = lower(:email)")
    List<User> findAllByEmailIgnoreCase(@Param("email") String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);

    @Query("select count(u) > 0 from User u join u.roles r "
            + "where r.name = :role and u.accountStatus = com.schoolmanagement.user.entity.AccountStatus.ACTIVE")
    boolean existsActiveWithRole(@Param("role") RoleName role);

    boolean existsByEmailIgnoreCase(String email);

    /** Active accounts of one role in a school whose name or email contains the (already escaped) pattern. */
    @Query("select u from User u join u.roles r where u.schoolId = :schoolId and r.name = :role "
            + "and u.accountStatus = com.schoolmanagement.user.entity.AccountStatus.ACTIVE "
            + "and (lower(u.firstName) like :pattern escape '\\' or lower(u.lastName) like :pattern escape '\\' "
            + "or lower(u.email) like :pattern escape '\\')")
    Page<User> searchActiveByRole(@Param("schoolId") UUID schoolId, @Param("role") RoleName role,
            @Param("pattern") String pattern, Pageable pageable);

    /**
     * Atomic so concurrent failures cannot be lost. The lock is applied in the same statement once the
     * threshold is reached.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginAttempts = u.failedLoginAttempts + 1, "
            + "u.lockedUntil = case when u.failedLoginAttempts + 1 >= :maxAttempts then :lockUntil "
            + "else u.lockedUntil end where u.id = :id")
    int registerFailedLogin(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts,
            @Param("lockUntil") Instant lockUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginAttempts = 0, u.lockedUntil = null, u.lastLoginAt = :now "
            + "where u.id = :id")
    int registerSuccessfulLogin(@Param("id") UUID id, @Param("now") Instant now);
}
