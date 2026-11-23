package com.schoolmanagement.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stand-ins for future role-specific endpoints, used to verify the URL-level role rules. */
@RestController
public class RoleProbeController {

    @GetMapping("/api/v1/admin/probe")
    public String adminOnly() {
        return "admin";
    }

    @GetMapping("/api/v1/teacher/probe")
    public String teacherOnly() {
        return "teacher";
    }

    @GetMapping("/api/v1/student/probe")
    public String studentOnly() {
        return "student";
    }
}
