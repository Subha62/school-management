package com.schoolmanagement.auth.dto;

import com.schoolmanagement.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required") @Size(max = 128, message = "Password is too long") String currentPassword,
        @NotBlank(message = "New password is required") @StrongPassword String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[****]";
    }
}
