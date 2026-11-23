package com.schoolmanagement.academic.dto;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class AcademicYearDtos {

    private AcademicYearDtos() {
    }

    /** {@code version} is required when updating and ignored when creating. */
    public record Request(
            @NotBlank(message = "Name is required") @Size(max = 50, message = "Name must be at most 50 characters") String name,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            Long version) {
    }

    public record Response(UUID id, String name, LocalDate startDate, LocalDate endDate,
            AcademicYearStatus status, long version) {

        public static Response from(AcademicYear year) {
            return new Response(year.getId(), year.getName(), year.getStartDate(), year.getEndDate(),
                    year.getStatus(), year.getVersion());
        }
    }
}
