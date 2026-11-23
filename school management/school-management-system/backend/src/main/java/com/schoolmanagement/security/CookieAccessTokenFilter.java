package com.schoolmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Authenticates requests from the access-token cookie. A missing, expired or invalid token leaves the
 * request anonymous instead of failing it, so public endpoints (login, refresh, password reset) keep working
 * with stale cookies, while protected endpoints are still rejected by the authorization rules.
 * Not a Spring bean on purpose: it is added to the security filter chain only.
 */
public class CookieAccessTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CookieAccessTokenFilter.class);

    private final JwtDecoder decoder;
    private final AccessTokenAuthenticationConverter converter;
    private final String cookieName;

    public CookieAccessTokenFilter(JwtDecoder decoder, AccessTokenAuthenticationConverter converter,
            String cookieName) {
        this.decoder = decoder;
        this.converter = converter;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, cookieName);
        if (cookie != null && !cookie.getValue().isBlank()) {
            authenticate(cookie.getValue());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Authentication authentication = converter.convert(jwt);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | AuthenticationException e) {
            log.debug("Ignoring unusable access token: {}", e.getClass().getSimpleName());
        }
    }
}
