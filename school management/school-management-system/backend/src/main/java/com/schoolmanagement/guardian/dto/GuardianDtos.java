package com.schoolmanagement.guardian.dto;

import com.schoolmanagement.guardian.entity.Guardian;
import com.schoolmanagement.guardian.entity.GuardianRelationship;
import com.schoolmanagement.student.dto.StudentDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class GuardianDtos {

    private GuardianDtos() {
    }

    public record Request(
            @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must be at most 100 characters") String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must be at most 100 characters") String lastName,
            @NotNull(message = "Relationship is required") GuardianRelationship relationship,
            @Email(message = "Email must be valid") @Size(max = 254, message = "Email is too long") String email,
            @NotBlank(message = "Phone number is required") @Pattern(regexp = StudentDtos.PHONE, message = "Phone number is not valid") String phoneNumber,
            @Size(max = 100, message = "Occupation must be at most 100 characters") String occupation,
            @Size(max = 500, message = "Address must be at most 500 characters") String address,
            @NotNull(message = "Emergency contact flag is required") Boolean emergencyContact,
            Long version) {
    }

    public record Response(UUID id, String firstName, String lastName, GuardianRelationship relationship,
            String email, String phoneNumber, String occupation, String address, boolean emergencyContact,
            long studentCount, long version) {

        public static Response from(Guardian guardian, long studentCount) {
            return new Response(guardian.getId(), guardian.getFirstName(), guardian.getLastName(),
                    guardian.getRelationship(), guardian.getEmail(), guardian.getPhoneNumber(),
                    guardian.getOccupation(), guardian.getAddress(), guardian.isEmergencyContact(), studentCount,
                    guardian.getVersion());
        }
    }
}
