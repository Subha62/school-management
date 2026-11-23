package com.schoolmanagement.auth.service;

import com.schoolmanagement.audit.AuditActions;
import com.schoolmanagement.audit.AuditEvent;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.entity.RefreshToken;
import com.schoolmanagement.auth.repository.RefreshTokenRepository;
import com.schoolmanagement.common.exception.UnauthorizedException;
import com.schoolmanagement.config.AppSecurityProperties;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, rotates and revokes refresh tokens. Only SHA-256 hashes are stored. */
@Service
public class RefreshTokenService {

    private static final String INVALID_SESSION = "Your session is no longer valid. Please sign in again.";
    /** A token revoked this recently is treated as a benign double submit rather than a theft attempt. */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);
    private static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(7);

    public record IssuedToken(String value, Duration ttl) {

        @Override
        public String toString() {
            return "IssuedToken[****]";
        }
    }

    public record Rotation(User user, IssuedToken token) {
    }

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final AuditEventPublisher audit;
    private final AppSecurityProperties.Jwt settings;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, AuditEventPublisher audit,
            AppSecurityProperties properties, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.audit = audit;
        this.settings = properties.jwt();
        this.clock = clock;
    }

    /** Starts a new token family, used at login. */
    @Transactional
    public IssuedToken issueForNewFamily(UUID userId, ClientInfo client) {
        return create(userId, UUID.randomUUID(), client);
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family and revokes the presented token.
     * Presenting an already-revoked token revokes the whole family, which forces a fresh login.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken stored = tokens.findByTokenHash(TokenSecrets.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID_SESSION));
        UUID tokenId = stored.getId();
        UUID familyId = stored.getFamilyId();
        UUID userId = stored.getUserId();

        if (stored.getRevokedAt() != null) {
            handleRevokedTokenReuse(stored.getRevokedAt(), familyId, userId, client, now);
            throw new UnauthorizedException(INVALID_SESSION);
        }
        if (stored.isExpired(now)) {
            throw new UnauthorizedException(INVALID_SESSION);
        }
        User user = users.findWithRolesById(userId).filter(candidate -> candidate.canAuthenticate(now))
                .orElse(null);
        if (user == null) {
            tokens.revokeFamily(familyId, now);
            throw new UnauthorizedException(INVALID_SESSION);
        }
        if (tokens.revokeIfActive(tokenId, now) == 0) {
            throw new UnauthorizedException(INVALID_SESSION);
        }
        return new Rotation(user, create(userId, familyId, client));
    }

    /** Revokes the token's whole family; returns the owning user when the token was recognised. */
    @Transactional
    public Optional<UUID> revoke(String rawToken) {
        Instant now = clock.instant();
        return tokens.findByTokenHash(TokenSecrets.hash(rawToken)).map(token -> {
            UUID familyId = token.getFamilyId();
            UUID userId = token.getUserId();
            tokens.revokeFamily(familyId, now);
            return userId;
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(clock.instant().minus(RETENTION_AFTER_EXPIRY));
    }

    private IssuedToken create(UUID userId, UUID familyId, ClientInfo client) {
        Instant now = clock.instant();
        String raw = TokenSecrets.generate();
        tokens.save(new RefreshToken(userId, familyId, TokenSecrets.hash(raw), now,
                now.plus(settings.refreshTokenTtl()), client.ipAddress(), client.userAgent()));
        return new IssuedToken(raw, settings.refreshTokenTtl());
    }

    private void handleRevokedTokenReuse(Instant revokedAt, UUID familyId, UUID userId, ClientInfo client,
            Instant now) {
        if (Duration.between(revokedAt, now).compareTo(REUSE_GRACE) <= 0) {
            return;
        }
        tokens.revokeFamily(familyId, now);
        audit.publish(new AuditEvent(AuditActions.TOKEN_REUSE_DETECTED, userId, null, "RefreshToken",
                familyId.toString(), "A revoked refresh token was presented again; its token family was revoked",
                client.ipAddress(), client.userAgent()));
    }
}
