package com.schoolmanagement.auth.service;

import com.schoolmanagement.config.AppSecurityProperties;
import com.schoolmanagement.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens. Roles in the token are informational; authorization reloads them. */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final AppSecurityProperties.Jwt settings;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, AppSecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.settings = properties.jwt();
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(settings.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
