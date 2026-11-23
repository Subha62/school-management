package com.schoolmanagement.security;

import com.schoolmanagement.user.entity.User;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The authenticated caller, rebuilt from the database on every request so revoked access takes effect at once. */
public record AuthenticatedUser(
        UUID id,
        UUID schoolId,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<String> roles) {

    public static AuthenticatedUser from(User user) {
        Set<String> roles = user.getRoles().stream().map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(user.getId(), user.getSchoolId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), roles);
    }
}
