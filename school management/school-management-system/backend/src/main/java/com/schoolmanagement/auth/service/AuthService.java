package com.schoolmanagement.auth.service;

import com.schoolmanagement.audit.AuditActions;
import com.schoolmanagement.audit.AuditEvent;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.dto.ChangePasswordRequest;
import com.schoolmanagement.auth.dto.CurrentUserResponse;
import com.schoolmanagement.auth.dto.LoginRequest;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.UnauthorizedException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schoolmanagement.config.AppSecurityProperties;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Used for every authentication failure so responses never reveal whether an account exists. */
    static final String INVALID_CREDENTIALS = "Invalid credentials.";
    private static final String USER_ENTITY = "User";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final AuthRateLimiter rateLimiter;
    private final AuditEventPublisher audit;
    private final AppSecurityProperties.Jwt jwtSettings;
    private final Clock clock;
    /** Compared against when the account does not exist, so response time does not reveal existence. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
            RefreshTokenService refreshTokens, LoginAttemptService loginAttempts, AuthRateLimiter rateLimiter,
            AuditEventPublisher audit, AppSecurityProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.jwtSettings = properties.jwt();
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(TokenSecrets.generate());
    }

    public AuthSession login(LoginRequest request, ClientInfo client) {
        String identifier = request.identifier().trim();
        rateLimiter.checkLoginAllowed(client.ipAddress(), identifier);

        List<User> candidates = users.findByLoginIdentifier(identifier);
        User user = candidates.size() == 1 ? candidates.get(0) : null;
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                user != null ? user.getPasswordHash() : dummyPasswordHash);
        Instant now = clock.instant();

        if (user == null || !passwordMatches) {
            handleFailedLogin(user, identifier, client, now);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!user.canAuthenticate(now)) {
            rateLimiter.recordLoginFailure(client.ipAddress(), identifier);
            audit(AuditActions.LOGIN_FAILURE, user, "Sign-in refused: account is " + user.getAccountStatus()
                    + (user.isTemporarilyLocked(now) ? " and temporarily locked" : ""), client);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        loginAttempts.recordSuccess(user.getId());
        rateLimiter.recordLoginSuccess(identifier);
        AuthSession session = newSession(user, client);
        audit(AuditActions.LOGIN_SUCCESS, user, "User signed in", client);
        return session;
    }

    public AuthSession refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Your session is no longer valid. Please sign in again.");
        }
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, client);
        return toSession(rotation.user(), rotation.token());
    }

    public void logout(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        Optional<UUID> userId = refreshTokens.revoke(rawRefreshToken);
        userId.ifPresent(id -> audit.publish(new AuditEvent(AuditActions.LOGOUT, id, null, USER_ENTITY,
                id.toString(), "User signed out", client.ipAddress(), client.userAgent())));
    }

    /** Verifies the current password, replaces it, ends every other session and starts a fresh one. */
    @Transactional
    public AuthSession changePassword(UUID userId, ChangePasswordRequest request, ClientInfo client) {
        String throttleKey = "change-password:" + userId;
        rateLimiter.checkSensitiveAttemptAllowed(throttleKey);

        User user = users.findWithRolesById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            rateLimiter.recordSensitiveFailure(throttleKey);
            throw new BusinessRuleException("The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from the current password.");
        }
        rateLimiter.clearSensitiveFailures(throttleKey);

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAllForUser(userId);
        AuthSession session = newSession(user, client);
        audit(AuditActions.PASSWORD_CHANGED, user, "User changed their password", client);
        return session;
    }

    private void handleFailedLogin(User user, String identifier, ClientInfo client, Instant now) {
        rateLimiter.recordLoginFailure(client.ipAddress(), identifier);
        if (user == null) {
            audit.publish(new AuditEvent(AuditActions.LOGIN_FAILURE, null, null, USER_ENTITY, null,
                    "Sign-in failed: unknown account or ambiguous identifier", client.ipAddress(),
                    client.userAgent()));
            return;
        }
        boolean newlyLocked = !user.isTemporarilyLocked(now) && loginAttempts.recordFailure(user.getId());
        audit(AuditActions.LOGIN_FAILURE, user, "Sign-in failed: incorrect password", client);
        if (newlyLocked) {
            audit(AuditActions.ACCOUNT_LOCKED, user, "Account temporarily locked after repeated failed sign-ins",
                    client);
        }
    }

    private AuthSession newSession(User user, ClientInfo client) {
        return toSession(user, refreshTokens.issueForNewFamily(user.getId(), client));
    }

    private AuthSession toSession(User user, RefreshTokenService.IssuedToken refreshToken) {
        return new AuthSession(CurrentUserResponse.from(user), accessTokens.issue(user),
                jwtSettings.accessTokenTtl(), refreshToken.value(), refreshToken.ttl());
    }

    private void audit(String action, User user, String summary, ClientInfo client) {
        audit.publish(new AuditEvent(action, user.getId(), user.getSchoolId(), USER_ENTITY,
                user.getId().toString(), summary, client.ipAddress(), client.userAgent()));
    }
}
