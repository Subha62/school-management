package com.schoolmanagement.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Authentication, cookie, lockout, rate-limit and password-reset settings. All come from configuration. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull CookieSettings cookie,
        @Valid @NotNull Lockout lockout,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull PasswordReset passwordReset) {

    private static final int MIN_SECRET_BYTES = 32;

    public AppSecurityProperties {
        if (jwt != null && jwt.secret() != null
                && jwt.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
    }

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record CookieSettings(
            boolean secure,
            @NotBlank String sameSite,
            @NotBlank String accessTokenName,
            @NotBlank String refreshTokenName) {
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration) {
    }

    public record RateLimit(
            @Min(1) int maxFailedLoginsPerIp,
            @Min(1) int maxFailedLoginsPerIdentifier,
            @Min(1) int maxPasswordResetRequestsPerIp,
            @NotNull Duration window) {
    }

    public record PasswordReset(@NotNull Duration tokenTtl, @NotBlank String frontendBaseUrl) {
    }
}
