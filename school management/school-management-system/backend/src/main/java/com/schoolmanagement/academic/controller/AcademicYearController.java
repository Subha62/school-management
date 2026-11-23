package com.schoolmanagement.academic.controller;

import com.schoolmanagement.academic.dto.AcademicYearDtos;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.service.AcademicYearService;
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
@RequestMapping("/api/v1/admin/academic-years")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Academic years")
public class AcademicYearController {

    private static final Set<String> SORTABLE = Set.of("name", "startDate", "endDate", "status");

    private final AcademicYearService service;
    private final PageRequestFactory pageRequests;

    public AcademicYearController(AcademicYearService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List academic years (paged, filterable by status and name)")
    public PageResponse<AcademicYearDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) AcademicYearStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "startDate"));
        return PageResponse.from(service.search(actor.schoolId(), status, search, pageable),
                AcademicYearDtos.Response::from);
    }

    @GetMapping("/{id}")
    public AcademicYearDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return AcademicYearDtos.Response.from(service.get(actor.schoolId(), id));
    }

    @PostMapping
    @Operation(summary = "Create a planned academic year")
    public ResponseEntity<AcademicYearDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AcademicYearDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AcademicYearDtos.Response.from(service.create(actor, request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name and dates; send the current version")
    public AcademicYearDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody AcademicYearDtos.Request request) {
        return AcademicYearDtos.Response.from(service.update(actor, id, request));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a planned year; fails if another year is active")
    public AcademicYearDtos.Response activate(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return AcademicYearDtos.Response.from(service.activate(actor, id));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close the active year (irreversible)")
    public AcademicYearDtos.Response close(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return AcademicYearDtos.Response.from(service.close(actor, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a planned year that has no sections")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
