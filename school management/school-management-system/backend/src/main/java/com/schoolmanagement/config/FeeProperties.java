package com.schoolmanagement.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code dueSoonDays}: how close to its due date an unpaid fee is shown as DUE instead of NOT_DUE. */
@Validated
@ConfigurationProperties(prefix = "app.fees")
public record FeeProperties(@Min(0) @Max(365) int dueSoonDays) {
}
