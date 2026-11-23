package com.schoolmanagement.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email or ID is required") @Size(max = 254, message = "Email or ID is too long") String identifier,
        @NotBlank(message = "Password is required") @Size(max = 128, message = "Password is too long") String password) {

    /** Keeps the secret out of logs, exception messages and debugger output. */
    @Override
    public String toString() {
        return "LoginRequest[identifier=" + identifier + ", password=****]";
    }
}
