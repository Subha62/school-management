package com.schoolmanagement.assignment.controller;

import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.SubmissionStatus;
import com.schoolmanagement.assignment.service.AssignmentService;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/submissions")
@PreAuthorize("hasAuthority('ROLE_TEACHER')")
@Tag(name = "Submission review")
public class TeacherSubmissionController {

    private static final Set<String> SORTABLE = Set.of("submittedAt", "status");

    private final AssignmentService service;
    private final PageRequestFactory pageRequests;

    public TeacherSubmissionController(AssignmentService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "Work handed in to my assignments; `pending=true` (default) lists work awaiting review")
    public PageResponse<AssignmentDtos.SubmissionListItem> queue(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID assignmentId, @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(defaultValue = "true") boolean pending, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.ASC, "submittedAt"));
        // An explicit status filter replaces the "awaiting review" default.
        return service.reviewQueue(actor, assignmentId, status, pending && status == null, pageable);
    }
}
