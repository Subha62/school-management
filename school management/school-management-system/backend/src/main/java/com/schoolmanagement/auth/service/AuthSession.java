package com.schoolmanagement.auth.service;

import com.schoolmanagement.auth.dto.CurrentUserResponse;
import java.time.Duration;

/** Result of an operation that starts or renews a session. Tokens are delivered only through cookies. */
public record AuthSession(
        CurrentUserResponse user,
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl) {

    @Override
    public String toString() {
        return "AuthSession[user=" + user + ", tokens=****]";
    }
}
