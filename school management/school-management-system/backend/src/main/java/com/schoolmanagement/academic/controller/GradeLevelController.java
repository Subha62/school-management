package com.schoolmanagement.academic.controller;

import com.schoolmanagement.academic.dto.GradeLevelDtos;
import com.schoolmanagement.academic.service.GradeLevelService;
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

@RestController
@RequestMapping("/api/v1/admin/grade-levels")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Grade levels")
public class GradeLevelController {

    private static final Set<String> SORTABLE = Set.of("name", "displayOrder", "active");

    private final GradeLevelService service;
    private final PageRequestFactory pageRequests;

    public GradeLevelController(GradeLevelService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List grades in display order (paged, filterable)")
    public PageResponse<GradeLevelDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("displayOrder").and(Sort.by("name")));
        return PageResponse.from(service.search(actor.schoolId(), active, search, pageable),
                GradeLevelDtos.Response::from);
    }

    @GetMapping("/{id}")
    public GradeLevelDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return GradeLevelDtos.Response.from(service.get(actor.schoolId(), id));
    }

    @PostMapping
    public ResponseEntity<GradeLevelDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody GradeLevelDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GradeLevelDtos.Response.from(service.create(actor, request)));
    }

    @PutMapping("/{id}")
    public GradeLevelDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody GradeLevelDtos.Request request) {
        return GradeLevelDtos.Response.from(service.update(actor, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a grade that no section uses")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
