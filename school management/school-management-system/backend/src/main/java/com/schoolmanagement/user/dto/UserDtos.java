package com.schoolmanagement.user.dto;

import com.schoolmanagement.student.dto.StudentDtos;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    /** Creates an administrator account. Teachers and students are created from their own pages. */
    public record CreateAdminRequest(
            @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must be at most 100 characters") String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must be at most 100 characters") String lastName,
            @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254, message = "Email is too long") String email,
            @Pattern(regexp = StudentDtos.PHONE, message = "Phone number is not valid") String phoneNumber,
            @NotBlank(message = "Password is required") @Size(max = 128, message = "Password is too long") String password) {

        @Override
        public String toString() {
            return "CreateAdminRequest[email=" + email + ", password=****]";
        }
    }

    public record StatusRequest(
            @NotNull(message = "Status is required") AccountStatus status,
            @NotNull(message = "The current record version is required") Long version) {
    }

    public record Response(UUID id, String username, String email, String firstName, String lastName,
            String phoneNumber, List<String> roles, AccountStatus accountStatus, boolean temporarilyLocked,
            Instant lastLoginAt, long version) {

        public static Response from(User user, Instant now) {
            List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).sorted().toList();
            return new Response(user.getId(), user.getUsername(), user.getEmail(), user.getFirstName(),
                    user.getLastName(), user.getPhoneNumber(), roles, user.getAccountStatus(),
                    user.isTemporarilyLocked(now), user.getLastLoginAt(), user.getVersion());
        }
    }
}
