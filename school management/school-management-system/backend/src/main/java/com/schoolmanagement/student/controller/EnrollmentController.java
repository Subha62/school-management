package com.schoolmanagement.student.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.dto.EnrollmentDtos;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.service.EnrollmentService;
import com.schoolmanagement.student.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Enrollments")
public class EnrollmentController {

    private static final Set<String> SORTABLE = Set.of("enrollmentDate", "rollNumber", "status",
            "student.admissionNumber");
    private static final Set<String> BATCH_SORTABLE = Set.of("performedAt", "promotedCount", "failedCount");

    private final EnrollmentService enrollments;
    private final PromotionService promotions;
    private final PageRequestFactory pageRequests;

    public EnrollmentController(EnrollmentService enrollments, PromotionService promotions,
            PageRequestFactory pageRequests) {
        this.enrollments = enrollments;
        this.promotions = promotions;
        this.pageRequests = pageRequests;
    }

    @GetMapping("/enrollments")
    @Operation(summary = "List enrollments (paged; filter by year, grade, section, status; search student)")
    public PageResponse<EnrollmentDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) UUID sectionId, @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "enrollmentDate").and(Sort.by("rollNumber")));
        return enrollments.search(actor.schoolId(), academicYearId, gradeLevelId, sectionId, status, search, pageable);
    }

    @PostMapping("/enrollments")
    @Operation(summary = "Enroll an active student in a section; the roll number is assigned when omitted")
    public ResponseEntity<EnrollmentDtos.Response> enroll(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody EnrollmentDtos.EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollments.enroll(actor, request));
    }

    @PostMapping("/enrollments/{id}/transfer")
    @Operation(summary = "Move an active enrollment to another section of the same academic year")
    public EnrollmentDtos.Response transfer(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody EnrollmentDtos.TransferRequest request) {
        return enrollments.transfer(actor, id, request);
    }

    @PostMapping("/enrollments/{id}/withdraw")
    public EnrollmentDtos.Response withdraw(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return enrollments.withdraw(actor, id);
    }

    @PostMapping("/enrollments/{id}/complete")
    public EnrollmentDtos.Response complete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return enrollments.complete(actor, id);
    }

    @PostMapping("/promotions")
    @Operation(summary = "Promote students to the next academic year; students that cannot move are reported")
    public ResponseEntity<EnrollmentDtos.PromotionSummary> promote(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody EnrollmentDtos.PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotions.promote(actor, request));
    }

    @GetMapping("/promotions")
    @Operation(summary = "Promotion history, newest first")
    public PageResponse<EnrollmentDtos.BatchResponse> history(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, BATCH_SORTABLE,
                Sort.by(Sort.Direction.DESC, "performedAt"));
        return promotions.history(actor.schoolId(), pageable);
    }
}
