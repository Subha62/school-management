package com.schoolmanagement.academic.dto;

import com.schoolmanagement.academic.entity.GradeLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class GradeLevelDtos {

    private GradeLevelDtos() {
    }

    public record Request(
            @NotBlank(message = "Name is required") @Size(max = 50, message = "Name must be at most 50 characters") String name,
            @NotNull(message = "Display order is required") @Min(value = 0, message = "Display order cannot be negative") @Max(value = 1000, message = "Display order must be at most 1000") Integer displayOrder,
            @NotNull(message = "Active flag is required") Boolean active,
            Long version) {
    }

    public record Response(UUID id, String name, int displayOrder, boolean active, long version) {

        public static Response from(GradeLevel grade) {
            return new Response(grade.getId(), grade.getName(), grade.getDisplayOrder(), grade.isActive(),
                    grade.getVersion());
        }
    }
}
