package com.schoolmanagement.academic.controller;

import com.schoolmanagement.academic.dto.SectionDtos;
import com.schoolmanagement.academic.service.SectionService;
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
@RequestMapping("/api/v1/admin/sections")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Sections")
public class SectionController {

    private static final Set<String> SORTABLE = Set.of("name", "capacity", "gradeLevel.displayOrder", "active");

    private final SectionService service;
    private final PageRequestFactory pageRequests;

    public SectionController(SectionService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List sections, ordered by grade then name (paged; filter by year, grade, name)")
    public PageResponse<SectionDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("gradeLevel.displayOrder").and(Sort.by("name")));
        return service.search(actor.schoolId(), academicYearId, gradeLevelId, active, search, pageable);
    }

    @GetMapping("/{id}")
    public SectionDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.getResponse(actor.schoolId(), id);
    }

    @PostMapping
    @Operation(summary = "Create a section in a year and grade, optionally with a class teacher")
    public ResponseEntity<SectionDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody SectionDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name, capacity, class teacher and status; send the current version")
    public SectionDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody SectionDtos.Request request) {
        return service.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a section without subject assignments")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
