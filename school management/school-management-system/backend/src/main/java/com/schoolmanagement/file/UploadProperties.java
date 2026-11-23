package com.schoolmanagement.file;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * File storage and upload limits. {@code provider} selects the {@link FileStorage} implementation; only
 * {@code local} exists today. The multipart limits in {@code application.yml} must not be lower than
 * {@code maxFileBytes}.
 */
@Validated
@ConfigurationProperties(prefix = "app.uploads")
public record UploadProperties(
        @NotBlank String provider,
        @NotBlank String localPath,
        @Min(1024) @Max(50 * 1024 * 1024) long maxFileBytes,
        @Min(1) @Max(50) int maxAssignmentFiles,
        @Min(1) @Max(20) int maxSubmissionFiles) {
}
