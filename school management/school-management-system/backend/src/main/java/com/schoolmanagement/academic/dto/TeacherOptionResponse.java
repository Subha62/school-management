package com.schoolmanagement.academic.dto;

import com.schoolmanagement.user.entity.User;
import java.util.UUID;

public record TeacherOptionResponse(UUID id, String firstName, String lastName, String email) {

    public static TeacherOptionResponse from(User user) {
        return new TeacherOptionResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
