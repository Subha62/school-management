package com.schoolmanagement.attendance.controller;

import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.entity.SessionStatus;
import com.schoolmanagement.attendance.entity.SessionType;
import com.schoolmanagement.attendance.service.AttendanceAdminService;
import com.schoolmanagement.attendance.service.AttendanceReportService;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/attendance")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Attendance administration")
public class AdminAttendanceController {

    private static final Set<String> SORTABLE = Set.of("attendanceDate", "status", "type");

    private final AttendanceAdminService service;
    private final AttendanceReportService reports;
    private final PageRequestFactory pageRequests;
    private final Clock clock;

    public AdminAttendanceController(AttendanceAdminService service, AttendanceReportService reports,
            PageRequestFactory pageRequests, Clock clock) {
        this.service = service;
        this.reports = reports;
        this.pageRequests = pageRequests;
        this.clock = clock;
    }

    @GetMapping("/sessions")
    @Operation(summary = "List attendance sessions (paged; filter by section, dates, type, status)")
    public PageResponse<AttendanceDtos.Summary> sessions(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SessionType type,
            @RequestParam(required = false) SessionStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "attendanceDate"));
        return service.search(actor.schoolId(), sectionId, from, to, type, status, pageable);
    }

    @GetMapping("/sessions/{id}")
    public AttendanceDtos.Detail session(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.detail(actor.schoolId(), id);
    }

    @GetMapping("/sessions/{id}/corrections")
    @Operation(summary = "History of corrections made to a session")
    public List<AttendanceDtos.CorrectionResponse> corrections(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return service.corrections(actor.schoolId(), id);
    }

    @PostMapping("/sessions/{id}/corrections")
    @Operation(summary = "Correct one student's mark; a reason is required and the change is audited")
    public AttendanceDtos.Detail correct(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody AttendanceDtos.CorrectionRequest request) {
        return service.correct(actor, id, request);
    }

    @GetMapping("/reports/monthly")
    @Operation(summary = "Monthly attendance percentage of every student in a section")
    public AttendanceDtos.Report monthly(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam UUID sectionId, @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "DAILY") SessionType type, @RequestParam(required = false) UUID subjectId) {
        return reports.monthly(actor.schoolId(), sectionId, AttendanceReportService.parseMonth(month, clock), type,
                subjectId);
    }

    @GetMapping("/reports/below-threshold")
    @Operation(summary = "Students under the attendance threshold (default 75%) for a period")
    public AttendanceDtos.Report belowThreshold(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID sectionId, @RequestParam(defaultValue = "DAILY") SessionType type,
            @RequestParam(required = false) UUID subjectId, @RequestParam(required = false) Integer threshold) {
        return reports.belowThreshold(actor.schoolId(), from, to, sectionId, type, subjectId, threshold);
    }
}
