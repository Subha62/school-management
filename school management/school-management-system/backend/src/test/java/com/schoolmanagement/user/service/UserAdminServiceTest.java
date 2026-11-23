package com.schoolmanagement.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.auth.service.RefreshTokenService;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.support.TestFixtures;
import com.schoolmanagement.user.dto.UserDtos;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserAdminServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final UserAdminService service = new UserAdminService(users, mock(UserProvisioner.class),
            mock(AccountRules.class), refreshTokens, mock(AuditEventPublisher.class),
            Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC));

    private final UUID schoolId = UUID.randomUUID();
    private final AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), schoolId, "root", "r@x.test",
            "Root", "Admin", Set.of("ROLE_ADMIN"));

    private User admin(UUID id) {
        User user = TestFixtures.user(RoleName.ROLE_ADMIN, "hash", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "schoolId", schoolId);
        return user;
    }

    @Test
    void theLastActiveAdministratorCannotBeDisabled() {
        UUID targetId = UUID.randomUUID();
        User target = admin(targetId);
        // Only the target is left among the locked active administrators.
        when(users.lockActiveAdministrators(schoolId)).thenReturn(List.of(target));
        when(users.findWithRolesByIdAndSchoolId(targetId, schoolId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.changeStatus(actor, targetId,
                new UserDtos.StatusRequest(AccountStatus.SUSPENDED, 0L))).isInstanceOf(ConflictException.class);

        assertThat(target.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void disablingAnotherAdministratorRevokesTheirSessions() {
        UUID targetId = UUID.randomUUID();
        User target = admin(targetId);
        when(users.lockActiveAdministrators(schoolId)).thenReturn(List.of(target, admin(actor.id())));
        when(users.findWithRolesByIdAndSchoolId(targetId, schoolId)).thenReturn(Optional.of(target));
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserDtos.Response response = service.changeStatus(actor, targetId,
                new UserDtos.StatusRequest(AccountStatus.ARCHIVED, 0L));

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ARCHIVED);
        verify(refreshTokens).revokeAllForUser(targetId);
    }

    @Test
    void administratorsCannotChangeTheirOwnStatus() {
        assertThatThrownBy(() -> service.changeStatus(actor, actor.id(),
                new UserDtos.StatusRequest(AccountStatus.SUSPENDED, 0L))).isInstanceOf(BusinessRuleException.class);
    }
}
