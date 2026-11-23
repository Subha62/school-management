package com.schoolmanagement.attendance.controller;

import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.entity.SessionStatus;
import com.schoolmanagement.attendance.entity.SessionType;
import com.schoolmanagement.attendance.service.AttendanceReportService;
import com.schoolmanagement.attendance.service.AttendanceService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/attendance")
@PreAuthorize("hasAuthority('ROLE_TEACHER')")
@Tag(name = "Teacher attendance")
public class TeacherAttendanceController {

    private static final Set<String> SORTABLE = Set.of("attendanceDate", "status", "type");

    private final AttendanceService service;
    private final AttendanceReportService reports;
    private final PageRequestFactory pageRequests;
    private final Clock clock;

    public TeacherAttendanceController(AttendanceService service, AttendanceReportService reports,
            PageRequestFactory pageRequests, Clock clock) {
        this.service = service;
        this.reports = reports;
        this.pageRequests = pageRequests;
        this.clock = clock;
    }

    @GetMapping("/classes")
    @Operation(summary = "Classes and subjects I may take attendance for")
    public List<AttendanceDtos.ClassOption> classes(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.classes(actor);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Sessions I opened (paged; newest first)")
    public PageResponse<AttendanceDtos.Summary> mine(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SessionStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "attendanceDate"));
        return service.listMine(actor, sectionId, from, to, status, pageable);
    }

    @PostMapping("/sessions")
    @Operation(summary = "Open the attendance session for a class and date; answers 200 if it already exists")
    public ResponseEntity<AttendanceDtos.Detail> open(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AttendanceDtos.OpenRequest request) {
        AttendanceService.Opened opened = service.open(actor, request);
        return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK).body(opened.detail());
    }

    @GetMapping("/sessions/{id}")
    public AttendanceDtos.Detail get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.get(actor, id);
    }

    @PutMapping("/sessions/{id}/records")
    @Operation(summary = "Mark students (only those enrolled in the section); not allowed once finalized")
    public AttendanceDtos.Detail mark(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody AttendanceDtos.MarkRequest request) {
        return service.mark(actor, id, request);
    }

    @PostMapping("/sessions/{id}/finalize")
    @Operation(summary = "Finalize a session once every enrolled student is marked")
    public AttendanceDtos.Detail finalizeSession(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return service.finalizeSession(actor, id);
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Delete a session that is not finalized yet")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.deleteOpen(actor, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports/monthly")
    @Operation(summary = "Monthly percentages for a class I am responsible for")
    public AttendanceDtos.Report monthly(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam UUID sectionId, @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "DAILY") SessionType type, @RequestParam(required = false) UUID subjectId) {
        return reports.monthlyForTeacher(actor, sectionId, AttendanceReportService.parseMonth(month, clock), type,
                subjectId);
    }
}
