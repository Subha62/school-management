package com.schoolmanagement.dashboard;

import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One dashboard endpoint per role; each answers only about the signed-in person (or, for administrators, the school). */
@RestController
@Tag(name = "Dashboards")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/dashboard")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "School-wide overview for administrators")
    public DashboardDtos.Admin admin(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.admin(actor);
    }

    @GetMapping("/api/v1/teacher/dashboard")
    @PreAuthorize("hasAuthority('ROLE_TEACHER')")
    @Operation(summary = "Today's periods and outstanding work for the signed-in teacher")
    public DashboardDtos.Teacher teacher(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.teacher(actor);
    }

    @GetMapping("/api/v1/student/dashboard")
    @PreAuthorize("hasAuthority('ROLE_STUDENT')")
    @Operation(summary = "Today's periods, upcoming work, latest result and fees for the signed-in student")
    public DashboardDtos.Student student(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.student(actor);
    }
}
