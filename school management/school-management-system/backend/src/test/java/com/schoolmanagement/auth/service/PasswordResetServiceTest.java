package com.schoolmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.entity.PasswordResetToken;
import com.schoolmanagement.auth.repository.PasswordResetTokenRepository;
import com.schoolmanagement.common.exception.InvalidRequestException;
import com.schoolmanagement.notification.service.EmailService;
import com.schoolmanagement.support.TestFixtures;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetServiceTest {

    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");

    private final Clock clock = Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final EmailService emailService = mock(EmailService.class);
    private AuthRateLimiter rateLimiter;
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        rateLimiter = new AuthRateLimiter(TestFixtures.securityProperties(), clock);
        service = new PasswordResetService(users, resetTokens, refreshTokens, encoder, emailService, rateLimiter,
                mock(AuditEventPublisher.class), TestFixtures.securityProperties(), clock);
        user = TestFixtures.user(RoleName.ROLE_STUDENT, encoder.encode("Old-Passw0rd!"), AccountStatus.ACTIVE);
    }

    @Test
    void requestForAnUnknownEmailSendsNothingAndStoresNothing() {
        when(users.findAllByEmailIgnoreCase("ghost@school.test")).thenReturn(List.of());

        service.requestReset("ghost@school.test", CLIENT);

        verify(emailService, never()).sendPasswordReset(any(), any(), any(), anyLong());
        verify(resetTokens, never()).save(any());
    }

    @Test
    void requestForAnInactiveAccountSendsNothing() {
        User suspended = TestFixtures.user(RoleName.ROLE_STUDENT, "hash", AccountStatus.SUSPENDED);
        when(users.findAllByEmailIgnoreCase(any())).thenReturn(List.of(suspended));

        service.requestReset("student@school.test", CLIENT);

        verify(emailService, never()).sendPasswordReset(any(), any(), any(), anyLong());
    }

    @Test
    void requestEmailsALinkWhoseTokenIsStoredOnlyAsAHash() {
        when(users.findAllByEmailIgnoreCase("student@school.test")).thenReturn(List.of(user));

        service.requestReset(" student@school.test ", CLIENT);

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq("student@school.test"), eq("Sam"), link.capture(), eq(30L));
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(link.getValue()).startsWith("http://localhost:5173/reset-password?token=");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(saved.capture());
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "tokenHash")).isEqualTo(TokenSecrets.hash(token))
                .isNotEqualTo(token);
        verify(resetTokens).invalidateAllForUser(user.getId(), TestFixtures.NOW);
    }

    @Test
    void requestsAboveTheQuotaAreDroppedSilently() {
        when(users.findAllByEmailIgnoreCase(any())).thenReturn(List.of(user));
        for (int i = 0; i < 5; i++) {
            service.requestReset("student@school.test", CLIENT);
        }

        service.requestReset("student@school.test", CLIENT);

        verify(emailService, org.mockito.Mockito.times(5)).sendPasswordReset(any(), any(), any(), anyLong());
    }

    private PasswordResetToken tokenFor(String raw, Instant expiresAt, Instant usedAt) {
        PasswordResetToken token = new PasswordResetToken(user.getId(), TokenSecrets.hash(raw),
                TestFixtures.NOW.minusSeconds(60), expiresAt);
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(token, "usedAt", usedAt);
        when(resetTokens.findByTokenHash(TokenSecrets.hash(raw))).thenReturn(Optional.of(token));
        when(resetTokens.markUsedIfUnused(token.getId(), TestFixtures.NOW)).thenReturn(1);
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));
        return token;
    }

    @Test
    void validTokenSetsTheNewPasswordAndEndsEverySession() {
        tokenFor("good", TestFixtures.NOW.plus(Duration.ofMinutes(10)), null);

        service.resetPassword("good", "New-Passw0rd!x", CLIENT);

        assertThat(encoder.matches("New-Passw0rd!x", user.getPasswordHash())).isTrue();
        verify(refreshTokens).revokeAllForUser(user.getId());
    }

    @Test
    void unknownExpiredAndAlreadyUsedTokensAreAllRejectedWithTheSameMessage() {
        tokenFor("expired", TestFixtures.NOW.minusSeconds(1), null);
        tokenFor("used", TestFixtures.NOW.plus(Duration.ofMinutes(10)), TestFixtures.NOW.minusSeconds(5));
        when(resetTokens.findByTokenHash(TokenSecrets.hash("unknown"))).thenReturn(Optional.empty());

        for (String token : List.of("unknown", "expired", "used")) {
            assertThatThrownBy(() -> service.resetPassword(token, "New-Passw0rd!x", CLIENT))
                    .as(token).isInstanceOf(InvalidRequestException.class)
                    .hasMessage(PasswordResetService.INVALID_LINK);
        }
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void aTokenConsumedByAConcurrentRequestCannotBeUsedTwice() {
        PasswordResetToken token = tokenFor("race", TestFixtures.NOW.plus(Duration.ofMinutes(10)), null);
        when(resetTokens.markUsedIfUnused(eq(token.getId()), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("race", "New-Passw0rd!x", CLIENT))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(encoder.matches("Old-Passw0rd!", user.getPasswordHash())).isTrue();
    }
}
