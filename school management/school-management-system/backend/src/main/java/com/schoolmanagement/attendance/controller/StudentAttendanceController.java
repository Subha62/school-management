package com.schoolmanagement.attendance.controller;

import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.service.AttendanceReportService;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A student's own attendance. The student is always the signed-in user; no id is accepted. */
@RestController
@RequestMapping("/api/v1/student/attendance")
@PreAuthorize("hasAuthority('ROLE_STUDENT')")
@Tag(name = "Student attendance")
public class StudentAttendanceController {

    private final AttendanceReportService reports;
    private final Clock clock;

    public StudentAttendanceController(AttendanceReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "My finalized attendance for a month (YYYY-MM, default this month)")
    public AttendanceDtos.StudentMonth month(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String month) {
        return reports.studentMonth(actor, AttendanceReportService.parseMonth(month, clock));
    }
}
