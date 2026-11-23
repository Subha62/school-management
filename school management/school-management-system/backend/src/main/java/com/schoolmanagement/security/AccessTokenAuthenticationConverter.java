package com.schoolmanagement.security;

import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a verified access token into an authentication. The user and roles are reloaded from the database
 * so suspensions, lockouts and role changes apply immediately rather than when the token expires.
 */
@Component
public class AccessTokenAuthenticationConverter {

    private final UserRepository users;
    private final Clock clock;

    public AccessTokenAuthenticationConverter(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public Authentication convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadCredentialsException("Invalid token subject");
        }
        User user = users.findWithRolesById(userId)
                .filter(candidate -> candidate.canAuthenticate(clock.instant()))
                .orElseThrow(() -> new BadCredentialsException("Account is not available"));
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        List<SimpleGrantedAuthority> authorities = principal.roles().stream().map(SimpleGrantedAuthority::new)
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
}
