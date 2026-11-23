package com.schoolmanagement.auth.service;

import com.schoolmanagement.common.exception.TooManyRequestsException;
import com.schoolmanagement.config.AppSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Brute-force protection for login and password-reset requests, keyed by client IP and identifier. */
@Component
public class AuthRateLimiter {

    private static final String LOGIN_BLOCKED_MESSAGE = "Too many attempts. Try again later.";

    private final SlidingWindowRateLimiter failedLoginsByIp;
    private final SlidingWindowRateLimiter failedAttemptsByIdentifier;
    private final SlidingWindowRateLimiter resetRequestsByIp;

    public AuthRateLimiter(AppSecurityProperties properties, Clock clock) {
        AppSecurityProperties.RateLimit limits = properties.rateLimit();
        this.failedLoginsByIp = new SlidingWindowRateLimiter(limits.maxFailedLoginsPerIp(), limits.window(), clock);
        this.failedAttemptsByIdentifier = new SlidingWindowRateLimiter(limits.maxFailedLoginsPerIdentifier(),
                limits.window(), clock);
        this.resetRequestsByIp = new SlidingWindowRateLimiter(limits.maxPasswordResetRequestsPerIp(),
                limits.window(), clock);
    }

    public void checkLoginAllowed(String ip, String identifier) {
        Duration byIp = failedLoginsByIp.retryAfter(key(ip));
        Duration byIdentifier = failedAttemptsByIdentifier.retryAfter(key(identifier));
        Duration retryAfter = byIp.compareTo(byIdentifier) >= 0 ? byIp : byIdentifier;
        if (retryAfter.compareTo(Duration.ZERO) > 0) {
            throw new TooManyRequestsException(LOGIN_BLOCKED_MESSAGE, retryAfter);
        }
    }

    public void recordLoginFailure(String ip, String identifier) {
        failedLoginsByIp.record(key(ip));
        failedAttemptsByIdentifier.record(key(identifier));
    }

    public void recordLoginSuccess(String identifier) {
        failedAttemptsByIdentifier.reset(key(identifier));
    }

    /** Throttles attempts to guess the current password of an authenticated account. */
    public void checkSensitiveAttemptAllowed(String subject) {
        Duration retryAfter = failedAttemptsByIdentifier.retryAfter(key(subject));
        if (retryAfter.compareTo(Duration.ZERO) > 0) {
            throw new TooManyRequestsException(LOGIN_BLOCKED_MESSAGE, retryAfter);
        }
    }

    public void recordSensitiveFailure(String subject) {
        failedAttemptsByIdentifier.record(key(subject));
    }

    public void clearSensitiveFailures(String subject) {
        failedAttemptsByIdentifier.reset(key(subject));
    }

    /** Returns false when the caller has exceeded the quota; otherwise counts the request. */
    public boolean tryAcquireResetRequest(String ip) {
        if (resetRequestsByIp.isBlocked(key(ip))) {
            return false;
        }
        resetRequestsByIp.record(key(ip));
        return true;
    }

    @Scheduled(fixedDelayString = "PT5M")
    void purgeIdleKeys() {
        failedLoginsByIp.purge();
        failedAttemptsByIdentifier.purge();
        resetRequestsByIp.purge();
    }

    private static String key(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }
}
