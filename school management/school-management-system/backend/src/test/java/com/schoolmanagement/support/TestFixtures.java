package com.schoolmanagement.support;

import com.schoolmanagement.config.AppSecurityProperties;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.Role;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders for unit tests that must not touch the database. */
public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-11-19T06:00:00Z");

    private TestFixtures() {
    }

    public static AppSecurityProperties securityProperties() {
        return new AppSecurityProperties(
                new AppSecurityProperties.Jwt("unit-test-secret-that-is-at-least-32-bytes-long", "test-issuer",
                        Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppSecurityProperties.CookieSettings(false, "Lax", "access_token", "refresh_token"),
                new AppSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new AppSecurityProperties.RateLimit(30, 10, 5, Duration.ofMinutes(15)),
                new AppSecurityProperties.PasswordReset(Duration.ofMinutes(30), "http://localhost:5173"));
    }

    public static User user(RoleName roleName, String passwordHash, AccountStatus status) {
        User user = new User(UUID.randomUUID(), "S-0001", "student@school.test", passwordHash, "Sam", "Student",
                status);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.addRole(role(roleName));
        return user;
    }

    public static Role role(RoleName name) {
        Role role = BeanUtils.instantiateClass(Role.class);
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(role, "name", name);
        return role;
    }
}
