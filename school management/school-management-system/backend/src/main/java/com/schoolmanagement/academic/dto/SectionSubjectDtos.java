package com.schoolmanagement.academic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class SectionSubjectDtos {

    private SectionSubjectDtos() {
    }

    /** {@code sectionId} and {@code subjectId} are used on create only; {@code version} on update only. */
    public record Request(
            UUID sectionId,
            UUID subjectId,
            UUID teacherId,
            @NotNull(message = "Weekly periods is required") @Min(value = 1, message = "Weekly periods must be at least 1") @Max(value = 40, message = "Weekly periods must be at most 40") Integer weeklyPeriods,
            Boolean active,
            Long version) {
    }

    public record Response(
            UUID id,
            UUID academicYearId,
            String academicYearName,
            UUID sectionId,
            String sectionName,
            String gradeLevelName,
            SubjectDtos.Ref subject,
            SectionDtos.NamedRef teacher,
            int weeklyPeriods,
            boolean active,
            long version) {
    }
}
