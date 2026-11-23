package com.schoolmanagement.teacher.dto;

import com.schoolmanagement.student.dto.StudentDtos;
import com.schoolmanagement.teacher.entity.EmploymentStatus;
import com.schoolmanagement.teacher.entity.TeacherProfile;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class TeacherDtos {

    private TeacherDtos() {
    }

    /** {@code employeeId} and {@code password} are used on create only; {@code version} on update only. */
    public record Request(
            @Size(max = 30, message = "Employee ID must be at most 30 characters") String employeeId,
            @Size(max = 128, message = "Password is too long") String password,
            @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must be at most 100 characters") String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must be at most 100 characters") String lastName,
            @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254, message = "Email is too long") String email,
            @Pattern(regexp = StudentDtos.PHONE, message = "Phone number is not valid") String phoneNumber,
            @NotNull(message = "Joining date is required") LocalDate joiningDate,
            @Size(max = 200, message = "Qualification must be at most 200 characters") String qualification,
            @Size(max = 200, message = "Specialization must be at most 200 characters") String specialization,
            @NotNull(message = "Employment status is required") EmploymentStatus employmentStatus,
            Long version) {
    }

    public record Response(UUID id, UUID userId, String employeeId, String firstName, String lastName,
            String email, String phoneNumber, LocalDate joiningDate, String qualification, String specialization,
            EmploymentStatus employmentStatus, AccountStatus accountStatus, long version) {

        public static Response from(TeacherProfile profile) {
            User user = profile.getUser();
            return new Response(profile.getId(), user.getId(), profile.getEmployeeId(), user.getFirstName(),
                    user.getLastName(), user.getEmail(), user.getPhoneNumber(), profile.getJoiningDate(),
                    profile.getQualification(), profile.getSpecialization(), profile.getEmploymentStatus(),
                    user.getAccountStatus(), profile.getVersion());
        }
    }
}
