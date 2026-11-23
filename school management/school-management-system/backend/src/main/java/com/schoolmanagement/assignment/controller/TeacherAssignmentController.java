package com.schoolmanagement.assignment.controller;

import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.AssignmentStatus;
import com.schoolmanagement.assignment.service.AssignmentService;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/teacher/assignments")
@PreAuthorize("hasAuthority('ROLE_TEACHER')")
@Tag(name = "Teacher assignments")
public class TeacherAssignmentController {

    private static final Set<String> SORTABLE = Set.of("dueAt", "title", "status", "createdAt");

    private final AssignmentService service;
    private final PageRequestFactory pageRequests;

    public TeacherAssignmentController(AssignmentService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "My assignments (paged; archived ones only when asked for)")
    public PageResponse<AssignmentDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID sectionId, @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "dueAt"));
        return service.list(actor, sectionId, status, search, pageable);
    }

    @GetMapping("/{id}")
    public AssignmentDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.get(actor, id);
    }

    @PostMapping
    @Operation(summary = "Create a draft for a class and subject I teach")
    public ResponseEntity<AssignmentDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AssignmentDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an assignment; send the current version")
    public AssignmentDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody AssignmentDtos.Request request) {
        return service.update(actor, id, request);
    }

    @PostMapping("/{id}/publish")
    public AssignmentDtos.Response publish(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.publish(actor, id);
    }

    @PostMapping("/{id}/close")
    public AssignmentDtos.Response close(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.close(actor, id);
    }

    @PostMapping("/{id}/reopen")
    public AssignmentDtos.Response reopen(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.reopen(actor, id);
    }

    @PostMapping("/{id}/archive")
    public AssignmentDtos.Response archive(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.archive(actor, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach files (multipart field `files`); type, size and count are validated")
    public AssignmentDtos.Response addAttachments(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return service.addAttachments(actor, id, files);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public AssignmentDtos.Response removeAttachment(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID attachmentId) {
        return service.removeAttachment(actor, id, attachmentId);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID attachmentId) {
        return Downloads.respond(service.downloadAttachment(actor, id, attachmentId));
    }

    @GetMapping("/{id}/submissions")
    @Operation(summary = "Every student of the class with the state of their work")
    public List<AssignmentDtos.RosterRow> roster(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return service.roster(actor, id);
    }

    @GetMapping("/{id}/submissions/{submissionId}")
    public AssignmentDtos.SubmissionResponse submission(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID submissionId) {
        return service.submission(actor, id, submissionId);
    }

    @GetMapping("/{id}/submissions/{submissionId}/attachments/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> downloadSubmissionFile(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @PathVariable UUID submissionId, @PathVariable UUID attachmentId) {
        return Downloads.respond(service.downloadSubmissionAttachment(actor, id, submissionId, attachmentId));
    }

    @PutMapping("/{id}/submissions/{submissionId}/review")
    @Operation(summary = "Review a submission: marks and feedback, or ask for a resubmission")
    public AssignmentDtos.SubmissionResponse review(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @PathVariable UUID submissionId,
            @Valid @RequestBody AssignmentDtos.ReviewRequest request) {
        return service.review(actor, id, submissionId, request);
    }
}
