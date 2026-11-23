package com.schoolmanagement.announcement.controller;

import com.schoolmanagement.announcement.dto.AnnouncementDtos;
import com.schoolmanagement.announcement.service.AnnouncementService;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the signed-in person may read, whatever their role: announcements that are live and meant for them. */
@RestController
@RequestMapping("/api/v1/announcements")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Announcements (reading)")
public class AnnouncementFeedController {

    private static final Set<String> SORTABLE = Set.of("publishedAt", "title");

    private final AnnouncementService service;
    private final PageRequestFactory pageRequests;

    public AnnouncementFeedController(AnnouncementService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "My announcements, newest first; unread=true shows only the ones I have not read")
    public PageResponse<AnnouncementDtos.Response> feed(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(defaultValue = "false") boolean unread, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        return service.feed(actor, unread, pageable);
    }

    @GetMapping("/unread-count")
    public AnnouncementDtos.UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.unreadCount(actor);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One announcement meant for me (404 if it is not)")
    public AnnouncementDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.getVisible(actor, id);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark an announcement as read (idempotent)")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.markRead(actor, id);
        return ResponseEntity.noContent().build();
    }
}
