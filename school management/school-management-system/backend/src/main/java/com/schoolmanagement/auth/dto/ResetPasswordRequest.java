package com.schoolmanagement.auth.dto;

import com.schoolmanagement.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required") @Size(max = 200, message = "Reset token is invalid") String token,
        @NotBlank(message = "New password is required") @StrongPassword String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[****]";
    }
}
