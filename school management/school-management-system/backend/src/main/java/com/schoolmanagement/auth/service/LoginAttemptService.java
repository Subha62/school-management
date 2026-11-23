package com.schoolmanagement.auth.service;

import com.schoolmanagement.config.AppSecurityProperties;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists failed-login counters in their own transactions so they survive the exception that aborts a
 * failed login.
 */
@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final AppSecurityProperties.Lockout lockout;
    private final Clock clock;

    public LoginAttemptService(UserRepository users, AppSecurityProperties properties, Clock clock) {
        this.users = users;
        this.lockout = properties.lockout();
        this.clock = clock;
    }

    /** Returns true when this failure caused the account to become temporarily locked. */
    @Transactional
    public boolean recordFailure(UUID userId) {
        Instant now = clock.instant();
        users.registerFailedLogin(userId, lockout.maxFailedAttempts(), now.plus(lockout.lockDuration()));
        return users.findById(userId)
                .map(user -> user.isTemporarilyLocked(now)
                        && user.getFailedLoginAttempts() == lockout.maxFailedAttempts())
                .orElse(false);
    }

    @Transactional
    public void recordSuccess(UUID userId) {
        users.registerSuccessfulLogin(userId, clock.instant());
    }
}
