package com.schoolmanagement.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Attendance rules an operator may tune. {@code teacherBackdateDays} limits how far back a teacher may open a
 * session (administrators are not limited); {@code defaultThresholdPercent} is the "low attendance" line.
 */
@Validated
@ConfigurationProperties(prefix = "app.attendance")
public record AttendanceProperties(
        @Min(0) @Max(60) int teacherBackdateDays,
        @Min(1) @Max(100) int defaultThresholdPercent) {
}
