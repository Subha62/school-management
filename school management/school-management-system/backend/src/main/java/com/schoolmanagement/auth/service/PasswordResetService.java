package com.schoolmanagement.auth.service;

import com.schoolmanagement.audit.AuditActions;
import com.schoolmanagement.audit.AuditEvent;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.entity.PasswordResetToken;
import com.schoolmanagement.auth.repository.PasswordResetTokenRepository;
import com.schoolmanagement.common.exception.InvalidRequestException;
import com.schoolmanagement.config.AppSecurityProperties;
import com.schoolmanagement.notification.service.EmailService;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    static final String INVALID_LINK = "This password reset link is invalid or has expired.";
    private static final String USER_ENTITY = "User";

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthRateLimiter rateLimiter;
    private final AuditEventPublisher audit;
    private final AppSecurityProperties.PasswordReset settings;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository resetTokens,
            RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder, EmailService emailService,
            AuthRateLimiter rateLimiter, AuditEventPublisher audit, AppSecurityProperties properties,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.settings = properties.passwordReset();
        this.clock = clock;
    }

    /**
     * Never reveals whether the email belongs to an account: the caller always gets the same outcome.
     * Requests above the per-IP quota are dropped silently for the same reason.
     */
    @Transactional
    public void requestReset(String email, ClientInfo client) {
        if (!rateLimiter.tryAcquireResetRequest(client.ipAddress())) {
            return;
        }
        List<User> matches = users.findAllByEmailIgnoreCase(email.trim());
        Instant now = clock.instant();
        for (User user : matches) {
            if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                continue;
            }
            resetTokens.invalidateAllForUser(user.getId(), now);
            String rawToken = TokenSecrets.generate();
            resetTokens.save(new PasswordResetToken(user.getId(), TokenSecrets.hash(rawToken), now,
                    now.plus(settings.tokenTtl())));
            emailService.sendPasswordReset(user.getEmail(), user.getFirstName(), buildLink(rawToken),
                    settings.tokenTtl().toMinutes());
            audit.publish(new AuditEvent(AuditActions.PASSWORD_RESET_REQUESTED, user.getId(),
                    user.getSchoolId(), USER_ENTITY, user.getId().toString(), "Password reset requested",
                    client.ipAddress(), client.userAgent()));
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword, ClientInfo client) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(TokenSecrets.hash(rawToken.trim()))
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        if (token.getUsedAt() != null || token.isExpired(now)
                || resetTokens.markUsedIfUnused(token.getId(), now) == 0) {
            throw new InvalidRequestException(INVALID_LINK);
        }
        User user = users.findWithRolesById(token.getUserId())
                .filter(candidate -> candidate.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user.getId());
        audit.publish(new AuditEvent(AuditActions.PASSWORD_RESET_COMPLETED, user.getId(), user.getSchoolId(),
                USER_ENTITY, user.getId().toString(), "Password reset completed with an emailed link",
                client.ipAddress(), client.userAgent()));
    }

    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeExpired() {
        resetTokens.deleteExpiredBefore(clock.instant().minusSeconds(7L * 24 * 3600));
    }

    private String buildLink(String rawToken) {
        String base = settings.frontendBaseUrl().replaceAll("/+$", "");
        return base + "/reset-password?token=" + rawToken;
    }
}
