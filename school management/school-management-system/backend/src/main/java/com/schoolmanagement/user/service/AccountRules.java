package com.schoolmanagement.user.service;

import com.schoolmanagement.auth.validation.StrongPasswordValidator;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Checks shared by every flow that creates or edits a user account behind a student or teacher profile. */
@Service
public class AccountRules {

    private final UserRepository users;
    private final StrongPasswordValidator passwordValidator = new StrongPasswordValidator();

    public AccountRules(UserRepository users) {
        this.users = users;
    }

    /** The sign-in ID (admission number or employee ID) must be new within the school. */
    public void requireAvailableLoginId(UUID schoolId, String loginId, String label) {
        if (users.existsBySchoolIdAndUsernameIgnoreCase(schoolId, loginId)) {
            throw new ConflictException("A user with the " + label + " \"" + loginId + "\" already exists.");
        }
    }

    public void requireAvailableEmail(UUID schoolId, String email, UUID excludeUserId) {
        // Emails are unique across all schools (sign-in and reset use them), so the check is not school-scoped.
        boolean taken = users.findAllByEmailIgnoreCase(email).stream()
                .anyMatch(user -> !user.getId().equals(excludeUserId));
        if (taken) {
            throw new ConflictException("A user with the email \"" + email + "\" already exists.");
        }
    }

    public void requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new FieldValidationException(field, message);
        }
    }

    public void requireStrongPassword(String password) {
        requireText(password, "password", "Initial password is required");
        if (!passwordValidator.isValid(password, null)) {
            throw new FieldValidationException("password",
                    "Password must be 10-72 characters and include upper case, lower case, a digit and a special character");
        }
    }
}
