package com.schoolmanagement.assignment.controller;

import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.AssignmentStatus;
import com.schoolmanagement.assignment.service.StudentAssignmentService;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** A student's assignments and their own submissions. The student is always the signed-in user. */
@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasAuthority('ROLE_STUDENT')")
@Tag(name = "Student assignments")
public class StudentAssignmentController {

    private static final Set<String> ASSIGNMENT_SORT = Set.of("dueAt", "title", "publishedAt");
    private static final Set<String> SUBMISSION_SORT = Set.of("submittedAt", "status");

    private final StudentAssignmentService service;
    private final PageRequestFactory pageRequests;

    public StudentAssignmentController(StudentAssignmentService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping("/assignments")
    @Operation(summary = "Published and closed assignments of my class, with my own submission")
    public PageResponse<AssignmentDtos.StudentView> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) AssignmentStatus status, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, ASSIGNMENT_SORT,
                Sort.by(Sort.Direction.DESC, "dueAt"));
        return service.list(actor, status, search, pageable);
    }

    @GetMapping("/assignments/{id}")
    public AssignmentDtos.StudentView get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.get(actor, id);
    }

    @PostMapping(value = "/assignments/{id}/submission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit or update my answer (multipart: `textAnswer` and/or `files`)")
    public AssignmentDtos.StudentView submit(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @RequestParam(value = "textAnswer", required = false) String textAnswer,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return service.submit(actor, id, textAnswer, files);
    }

    @DeleteMapping("/assignments/{id}/submission/attachments/{attachmentId}")
    public AssignmentDtos.StudentView removeFile(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID attachmentId) {
        return service.removeAttachment(actor, id, attachmentId);
    }

    @GetMapping("/assignments/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> downloadAssignmentFile(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @PathVariable UUID attachmentId) {
        return Downloads.respond(service.downloadAssignmentFile(actor, id, attachmentId));
    }

    @GetMapping("/assignments/{id}/submission/attachments/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> downloadOwnFile(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID attachmentId) {
        return Downloads.respond(service.downloadOwnFile(actor, id, attachmentId));
    }

    @GetMapping("/submissions")
    @Operation(summary = "My submissions with their status and, once reviewed, marks")
    public PageResponse<AssignmentDtos.SubmissionListItem> mine(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SUBMISSION_SORT,
                Sort.by(Sort.Direction.DESC, "submittedAt"));
        return service.mine(actor, pageable);
    }
}
