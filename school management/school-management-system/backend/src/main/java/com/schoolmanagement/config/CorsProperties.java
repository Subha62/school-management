package com.schoolmanagement.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins allowed to call the API. Credentials are always allowed (cookie authentication),
 * so a wildcard origin is rejected at startup.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins must list explicit origins; wildcards are not allowed with credentials");
        }
    }
}
