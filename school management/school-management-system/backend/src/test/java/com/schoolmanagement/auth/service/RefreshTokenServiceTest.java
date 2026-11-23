package com.schoolmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolmanagement.audit.AuditActions;
import com.schoolmanagement.audit.AuditEvent;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.entity.RefreshToken;
import com.schoolmanagement.auth.repository.RefreshTokenRepository;
import com.schoolmanagement.common.exception.UnauthorizedException;
import com.schoolmanagement.support.TestFixtures;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceTest {

    private static final String RAW = "raw-refresh-token";
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");
    private static final UUID FAMILY = UUID.randomUUID();

    private final Clock clock = Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(tokens, users, audit, TestFixtures.securityProperties(), clock);
        user = TestFixtures.user(RoleName.ROLE_TEACHER, "hash", AccountStatus.ACTIVE);
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));
    }

    private RefreshToken storedToken(Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(user.getId(), FAMILY, TokenSecrets.hash(RAW),
                TestFixtures.NOW.minusSeconds(3600), expiresAt, "10.0.0.1", "JUnit");
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(token, "revokedAt", revokedAt);
        when(tokens.findByTokenHash(TokenSecrets.hash(RAW))).thenReturn(Optional.of(token));
        when(tokens.revokeIfActive(token.getId(), TestFixtures.NOW)).thenReturn(1);
        return token;
    }

    @Test
    void rotationRevokesThePresentedTokenAndIssuesANewOneInTheSameFamilyStoringOnlyItsHash() {
        storedToken(TestFixtures.NOW.plus(Duration.ofDays(1)), null);

        RefreshTokenService.Rotation rotation = service.rotate(RAW, CLIENT);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.token().value()).isNotEqualTo(RAW);
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY);
        Object storedHash = ReflectionTestUtils.getField(saved.getValue(), "tokenHash");
        assertThat(storedHash).isEqualTo(TokenSecrets.hash(rotation.token().value()));
        assertThat(storedHash).isNotEqualTo(rotation.token().value());
    }

    @Test
    void unknownTokenIsRejected() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope", CLIENT)).isInstanceOf(UnauthorizedException.class);
        verify(tokens, never()).save(any());
    }

    @Test
    void expiredTokenIsRejected() {
        storedToken(TestFixtures.NOW.minusSeconds(1), null);

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(UnauthorizedException.class);
        verify(tokens, never()).save(any());
    }

    @Test
    void replayOfARevokedTokenRevokesTheWholeFamilyAndIsAudited() {
        storedToken(TestFixtures.NOW.plus(Duration.ofDays(1)), TestFixtures.NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(UnauthorizedException.class);

        verify(tokens).revokeFamily(FAMILY, TestFixtures.NOW);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditActions.TOKEN_REUSE_DETECTED);
        verify(tokens, never()).save(any());
    }

    @Test
    void replayWithinTheGracePeriodIsRejectedWithoutKillingTheSession() {
        storedToken(TestFixtures.NOW.plus(Duration.ofDays(1)), TestFixtures.NOW.minusSeconds(3));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(UnauthorizedException.class);

        verify(tokens, never()).revokeFamily(any(), any());
        verify(tokens, never()).save(any());
    }

    @Test
    void tokenOfAnAccountThatCanNoLongerSignInRevokesTheFamily() {
        storedToken(TestFixtures.NOW.plus(Duration.ofDays(1)), null);
        User suspended = TestFixtures.user(RoleName.ROLE_TEACHER, "hash", AccountStatus.SUSPENDED);
        ReflectionTestUtils.setField(suspended, "id", user.getId());
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(UnauthorizedException.class);

        verify(tokens).revokeFamily(FAMILY, TestFixtures.NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void losingARaceToRevokeTheSameTokenIssuesNothing() {
        RefreshToken token = storedToken(TestFixtures.NOW.plus(Duration.ofDays(1)), null);
        when(tokens.revokeIfActive(eq(token.getId()), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(UnauthorizedException.class);

        verify(tokens, never()).save(any());
    }

    @Test
    void revokeEndsTheWholeFamilyOfARecognisedToken() {
        storedToken(TestFixtures.NOW.plus(Duration.ofDays(1)), null);

        assertThat(service.revoke(RAW)).contains(user.getId());

        verify(tokens).revokeFamily(FAMILY, TestFixtures.NOW);
    }

    @Test
    void revokeOfAnUnknownTokenIsHarmless() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.revoke("unknown")).isEmpty();
        verify(tokens, never()).revokeFamily(any(), any());
    }
}
