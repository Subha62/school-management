package com.schoolmanagement.auth.controller;

import com.schoolmanagement.auth.service.AuthSession;
import com.schoolmanagement.config.AppSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/** Reads and writes the HttpOnly authentication cookies. Tokens never appear in response bodies. */
@Component
public class AuthCookies {

    /** The access cookie is sent to every API call; the refresh cookie only to the auth endpoints. */
    static final String ACCESS_COOKIE_PATH = "/api";
    static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AppSecurityProperties.CookieSettings settings;

    public AuthCookies(AppSecurityProperties properties) {
        this.settings = properties.cookie();
    }

    public void write(HttpServletResponse response, AuthSession session) {
        add(response, build(settings.accessTokenName(), session.accessToken(), ACCESS_COOKIE_PATH,
                session.accessTokenTtl()));
        add(response, build(settings.refreshTokenName(), session.refreshToken(), REFRESH_COOKIE_PATH,
                session.refreshTokenTtl()));
    }

    public void clear(HttpServletResponse response) {
        add(response, build(settings.accessTokenName(), "", ACCESS_COOKIE_PATH, Duration.ZERO));
        add(response, build(settings.refreshTokenName(), "", REFRESH_COOKIE_PATH, Duration.ZERO));
    }

    public String readRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, settings.refreshTokenName());
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite(settings.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
