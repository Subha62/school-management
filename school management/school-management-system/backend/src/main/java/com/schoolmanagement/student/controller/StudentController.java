package com.schoolmanagement.student.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.dto.EnrollmentDtos;
import com.schoolmanagement.student.dto.StudentDtos;
import com.schoolmanagement.student.dto.StudentGuardianDtos;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.student.service.EnrollmentService;
import com.schoolmanagement.student.service.StudentGuardianService;
import com.schoolmanagement.student.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/admin/students")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Students")
public class StudentController {

    private static final Set<String> SORTABLE = Set.of("admissionNumber", "user.firstName", "user.lastName",
            "status", "admissionDate");

    private final StudentService service;
    private final StudentGuardianService guardianLinks;
    private final EnrollmentService enrollments;
    private final PageRequestFactory pageRequests;

    public StudentController(StudentService service, StudentGuardianService guardianLinks,
            EnrollmentService enrollments, PageRequestFactory pageRequests) {
        this.service = service;
        this.guardianLinks = guardianLinks;
        this.enrollments = enrollments;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List students (paged; filter by status; search name, admission number or email)")
    public PageResponse<StudentDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) StudentStatus status, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("user.lastName").and(Sort.by("user.firstName")));
        return service.search(actor.schoolId(), status, search, pageable);
    }

    @GetMapping("/{id}")
    public StudentDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.getResponse(actor.schoolId(), id);
    }

    @PostMapping
    @Operation(summary = "Create a student and the sign-in account (admission number is the username)")
    public ResponseEntity<StudentDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody StudentDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a student; send the current version. The admission number cannot change")
    public StudentDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody StudentDtos.Request request) {
        return service.update(actor, id, request);
    }

    @GetMapping("/{id}/enrollments")
    @Operation(summary = "Enrollment history of a student, newest first")
    public List<EnrollmentDtos.Response> enrollmentHistory(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return enrollments.historyOf(actor.schoolId(), id);
    }

    @GetMapping("/{id}/guardians")
    public List<StudentGuardianDtos.Response> guardians(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return guardianLinks.list(actor.schoolId(), id);
    }

    @PostMapping("/{id}/guardians")
    @Operation(summary = "Link an existing guardian to the student")
    public ResponseEntity<StudentGuardianDtos.Response> linkGuardian(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @Valid @RequestBody StudentGuardianDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guardianLinks.link(actor, id, request));
    }

    @PutMapping("/{id}/guardians/{linkId}")
    @Operation(summary = "Change the primary, notification and pickup flags of a guardian link")
    public StudentGuardianDtos.Response updateGuardianLink(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID linkId,
            @Valid @RequestBody StudentGuardianDtos.Request request) {
        return guardianLinks.update(actor, id, linkId, request);
    }

    @DeleteMapping("/{id}/guardians/{linkId}")
    public ResponseEntity<Void> unlinkGuardian(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID linkId) {
        guardianLinks.unlink(actor, id, linkId);
        return ResponseEntity.noContent().build();
    }
}
