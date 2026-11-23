package com.schoolmanagement.auth.dto;

import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.user.entity.User;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        UUID schoolId,
        String username,
        String email,
        String firstName,
        String lastName,
        List<String> roles) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.schoolId(), user.username(), user.email(),
                user.firstName(), user.lastName(), user.roles().stream().sorted().toList());
    }

    public static CurrentUserResponse from(User user) {
        return from(AuthenticatedUser.from(user));
    }
}
