package com.schoolmanagement.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class SectionDtos {

    private SectionDtos() {
    }

    /**
     * The academic year and grade are chosen when the section is created and cannot change afterwards, so they
     * are ignored on update. {@code version} is required when updating.
     */
    public record Request(
            UUID academicYearId,
            UUID gradeLevelId,
            @NotBlank(message = "Name is required") @Size(max = 20, message = "Name must be at most 20 characters") String name,
            @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") @Max(value = 500, message = "Capacity must be at most 500") Integer capacity,
            UUID classTeacherId,
            @NotNull(message = "Active flag is required") Boolean active,
            Long version) {
    }

    public record NamedRef(UUID id, String name) {
    }

    public record Response(
            UUID id,
            NamedRef academicYear,
            NamedRef gradeLevel,
            String name,
            int capacity,
            NamedRef classTeacher,
            boolean active,
            long version) {
    }
}
