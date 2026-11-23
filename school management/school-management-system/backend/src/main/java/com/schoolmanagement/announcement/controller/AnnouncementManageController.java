package com.schoolmanagement.announcement.controller;

import com.schoolmanagement.announcement.dto.AnnouncementDtos;
import com.schoolmanagement.announcement.entity.AnnouncementAudience;
import com.schoolmanagement.announcement.entity.AnnouncementStatus;
import com.schoolmanagement.announcement.service.AnnouncementService;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Writing announcements. The same operations are served under {@code /admin} and {@code /teacher}; the URL rule of
 * the security chain decides who gets in, and the service limits a teacher to their own announcements and classes.
 */
@RestController
@RequestMapping({"/api/v1/admin/announcements", "/api/v1/teacher/announcements"})
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_TEACHER')")
@Tag(name = "Announcements (authoring)")
public class AnnouncementManageController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "title", "status", "publishedAt");

    private final AnnouncementService service;
    private final PageRequestFactory pageRequests;

    public AnnouncementManageController(AnnouncementService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "Announcements I manage (administrators: all of the school; teachers: their own)")
    public PageResponse<AnnouncementDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) AnnouncementStatus status,
            @RequestParam(required = false) AnnouncementAudience audience,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.manage(actor, status, audience, search, pageable);
    }

    @GetMapping("/{id}")
    public AnnouncementDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.getManaged(actor, id);
    }

    @PostMapping
    @Operation(summary = "Write a draft announcement")
    public ResponseEntity<AnnouncementDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AnnouncementDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a draft or published announcement; send the current version")
    public AnnouncementDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody AnnouncementDtos.Request request) {
        return service.update(actor, id, request);
    }

    @PostMapping("/{id}/publish")
    public AnnouncementDtos.Response publish(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.publish(actor, id);
    }

    @PostMapping("/{id}/archive")
    public AnnouncementDtos.Response archive(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.archive(actor, id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
