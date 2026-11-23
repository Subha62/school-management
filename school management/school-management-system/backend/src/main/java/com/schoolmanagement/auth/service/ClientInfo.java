package com.schoolmanagement.auth.service;

import jakarta.servlet.http.HttpServletRequest;

/** Network details recorded with security events. Values are length-limited to fit their columns. */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_IP = 45;
    private static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(truncate(request.getRemoteAddr(), MAX_IP),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
