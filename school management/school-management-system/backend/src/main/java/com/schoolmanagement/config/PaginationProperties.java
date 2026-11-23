package com.schoolmanagement.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(@Min(1) int defaultPageSize, @Min(1) int maxPageSize) {

    public PaginationProperties {
        if (defaultPageSize > maxPageSize) {
            throw new IllegalArgumentException("app.pagination.default-page-size must not exceed max-page-size");
        }
    }
}
