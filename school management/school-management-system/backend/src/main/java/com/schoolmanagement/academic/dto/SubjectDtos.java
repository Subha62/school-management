package com.schoolmanagement.academic.dto;

import com.schoolmanagement.academic.entity.Subject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class SubjectDtos {

    private SubjectDtos() {
    }

    public record Request(
            @NotBlank(message = "Code is required") @Size(max = 20, message = "Code must be at most 20 characters") @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "Code may contain letters, digits, dots, dashes and underscores") String code,
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @Size(max = 500, message = "Description must be at most 500 characters") String description,
            @NotNull(message = "Active flag is required") Boolean active,
            Long version) {
    }

    public record Response(UUID id, String code, String name, String description, boolean active, long version) {

        public static Response from(Subject subject) {
            return new Response(subject.getId(), subject.getCode(), subject.getName(), subject.getDescription(),
                    subject.isActive(), subject.getVersion());
        }
    }

    /** Compact reference embedded in other responses. */
    public record Ref(UUID id, String code, String name) {

        public static Ref from(Subject subject) {
            return new Ref(subject.getId(), subject.getCode(), subject.getName());
        }
    }
}
