package com.schoolmanagement.user.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    /** Optional login identifier such as an admission number or employee ID. */
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    @Column(nullable = false)
    private boolean emailVerified;

    private Instant lastLoginAt;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant lockedUntil;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(UUID schoolId, String username, String email, String passwordHash, String firstName,
            String lastName, AccountStatus accountStatus) {
        this.schoolId = schoolId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.accountStatus = accountStatus;
    }

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Whether the account may sign in or use an existing session right now. */
    public boolean canAuthenticate(Instant now) {
        return accountStatus == AccountStatus.ACTIVE && !isTemporarilyLocked(now);
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void updateContact(String firstName, String lastName, String email, String phoneNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public void changeAccountStatus(AccountStatus status) {
        this.accountStatus = status;
        if (status == AccountStatus.ACTIVE) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
    }

    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(role -> role.getName() == roleName);
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
