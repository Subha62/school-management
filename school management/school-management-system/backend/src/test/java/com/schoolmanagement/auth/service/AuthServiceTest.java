package com.schoolmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolmanagement.audit.AuditActions;
import com.schoolmanagement.audit.AuditEvent;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.dto.ChangePasswordRequest;
import com.schoolmanagement.auth.dto.LoginRequest;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.TooManyRequestsException;
import com.schoolmanagement.common.exception.UnauthorizedException;
import com.schoolmanagement.support.TestFixtures;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC);
    private final UserRepository users = mock(UserRepository.class);
    private final AccessTokenService accessTokens = mock(AccessTokenService.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final LoginAttemptService loginAttempts = mock(LoginAttemptService.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private AuthRateLimiter rateLimiter;
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        rateLimiter = new AuthRateLimiter(TestFixtures.securityProperties(), clock);
        service = new AuthService(users, encoder, accessTokens, refreshTokens, loginAttempts, rateLimiter, audit,
                TestFixtures.securityProperties(), clock);
        user = TestFixtures.user(RoleName.ROLE_STUDENT, encoder.encode(PASSWORD), AccountStatus.ACTIVE);
        when(users.findByLoginIdentifier("S-0001")).thenReturn(List.of(user));
        when(refreshTokens.issueForNewFamily(any(), any()))
                .thenReturn(new RefreshTokenService.IssuedToken("refresh", Duration.ofDays(7)));
        when(accessTokens.issue(any())).thenReturn("access");
    }

    @Test
    void successfulLoginIssuesBothTokensResetsCountersAndAudits() {
        AuthSession session = service.login(new LoginRequest(" S-0001 ", PASSWORD), CLIENT);

        assertThat(session.accessToken()).isEqualTo("access");
        assertThat(session.refreshToken()).isEqualTo("refresh");
        assertThat(session.user().roles()).containsExactly("ROLE_STUDENT");
        verify(loginAttempts).recordSuccess(user.getId());
        assertThat(auditedActions()).containsExactly(AuditActions.LOGIN_SUCCESS);
    }

    @Test
    void unknownAccountAndWrongPasswordProduceTheSameGenericFailure() {
        when(users.findByLoginIdentifier("ghost")).thenReturn(List.of());

        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("ghost", PASSWORD), CLIENT));
        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("S-0001", "Wrong-Pass-1!"), CLIENT));

        assertThat(unknown).isInstanceOf(UnauthorizedException.class).hasMessage(AuthService.INVALID_CREDENTIALS);
        assertThat(wrongPassword).isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        verify(loginAttempts, times(1)).recordFailure(user.getId());
    }

    @Test
    void ambiguousIdentifierIsTreatedAsUnknown() {
        User other = TestFixtures.user(RoleName.ROLE_TEACHER, encoder.encode(PASSWORD), AccountStatus.ACTIVE);
        when(users.findByLoginIdentifier("shared")).thenReturn(List.of(user, other));

        assertThatThrownBy(() -> service.login(new LoginRequest("shared", PASSWORD), CLIENT))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokens, never()).issueForNewFamily(any(), any());
    }

    @Test
    void failedLoginThatTriggersLockoutIsAudited() {
        when(loginAttempts.recordFailure(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("S-0001", "Wrong-Pass-1!"), CLIENT))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(auditedActions()).containsExactly(AuditActions.LOGIN_FAILURE, AuditActions.ACCOUNT_LOCKED);
    }

    @Test
    void correctPasswordIsRefusedWhileTheAccountIsLockedWithoutExtendingTheLock() {
        ReflectionTestUtils.setField(user, "lockedUntil", TestFixtures.NOW.plusSeconds(600));

        assertThatThrownBy(() -> service.login(new LoginRequest("S-0001", PASSWORD), CLIENT))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthService.INVALID_CREDENTIALS);

        verify(loginAttempts, never()).recordFailure(any());
        verify(refreshTokens, never()).issueForNewFamily(any(), any());
    }

    @Test
    void wrongPasswordOnLockedAccountDoesNotCountAgainstTheCounter() {
        ReflectionTestUtils.setField(user, "lockedUntil", TestFixtures.NOW.plusSeconds(600));

        assertThatThrownBy(() -> service.login(new LoginRequest("S-0001", "Wrong-Pass-1!"), CLIENT))
                .isInstanceOf(UnauthorizedException.class);

        verify(loginAttempts, never()).recordFailure(any());
    }

    @Test
    void nonActiveAccountsCannotSignInEvenWithTheRightPassword() {
        for (AccountStatus status : List.of(AccountStatus.PENDING, AccountStatus.LOCKED, AccountStatus.SUSPENDED,
                AccountStatus.ARCHIVED)) {
            User inactive = TestFixtures.user(RoleName.ROLE_STUDENT, encoder.encode(PASSWORD), status);
            when(users.findByLoginIdentifier("inactive")).thenReturn(List.of(inactive));

            assertThatThrownBy(() -> service.login(new LoginRequest("inactive", PASSWORD), CLIENT))
                    .as(status.name()).isInstanceOf(UnauthorizedException.class)
                    .hasMessage(AuthService.INVALID_CREDENTIALS);
        }
        verify(refreshTokens, never()).issueForNewFamily(any(), any());
    }

    @Test
    void rateLimitBlocksFurtherAttemptsBeforeTouchingTheDatabase() {
        for (int i = 0; i < 10; i++) {
            rateLimiter.recordLoginFailure("10.0.0.9", "S-0001");
        }

        assertThatThrownBy(() -> service.login(new LoginRequest("S-0001", PASSWORD), CLIENT))
                .isInstanceOf(TooManyRequestsException.class);
        verify(users, never()).findByLoginIdentifier(any());
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndNeverRevokesSessions() {
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("Wrong-Pass-1!", "New-Passw0rd!x"), CLIENT))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("current password");
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void changePasswordRejectsReusingTheSamePassword() {
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest(PASSWORD, PASSWORD), CLIENT))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different");
    }

    @Test
    void changePasswordStoresAHashRevokesAllSessionsAndAudits() {
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));

        AuthSession session = service.changePassword(user.getId(),
                new ChangePasswordRequest(PASSWORD, "New-Passw0rd!x"), CLIENT);

        assertThat(encoder.matches("New-Passw0rd!x", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).doesNotContain("New-Passw0rd");
        verify(refreshTokens).revokeAllForUser(user.getId());
        assertThat(session.refreshToken()).isEqualTo("refresh");
        assertThat(auditedActions()).containsExactly(AuditActions.PASSWORD_CHANGED);
    }

    @Test
    void repeatedWrongCurrentPasswordsAreThrottled() {
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));
        ChangePasswordRequest wrong = new ChangePasswordRequest("Wrong-Pass-1!", "New-Passw0rd!x");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> service.changePassword(user.getId(), wrong, CLIENT))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThatThrownBy(() -> service.changePassword(user.getId(), wrong, CLIENT))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void logoutWithoutACookieIsANoOp() {
        service.logout(null, CLIENT);
        service.logout("  ", CLIENT);

        verify(refreshTokens, never()).revoke(any());
    }

    @Test
    void refreshWithoutATokenIsUnauthorizedAndNeverReachesTheTokenStore() {
        assertThatThrownBy(() -> service.refresh(null, CLIENT)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.refresh("  ", CLIENT)).isInstanceOf(UnauthorizedException.class);

        verify(refreshTokens, never()).rotate(any(), any());
    }

    private List<String> auditedActions() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return captor.getAllValues().stream().map(AuditEvent::action).toList();
    }
}
