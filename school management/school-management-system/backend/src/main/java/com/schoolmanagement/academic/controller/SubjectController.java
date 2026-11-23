package com.schoolmanagement.academic.controller;

import com.schoolmanagement.academic.dto.SubjectDtos;
import com.schoolmanagement.academic.service.SubjectService;
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
@RequestMapping("/api/v1/admin/subjects")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Subjects")
public class SubjectController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "active");

    private final SubjectService service;
    private final PageRequestFactory pageRequests;

    public SubjectController(SubjectService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List subjects (paged; search matches code or name)")
    public PageResponse<SubjectDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE, Sort.by("code"));
        return PageResponse.from(service.search(actor.schoolId(), active, search, pageable),
                SubjectDtos.Response::from);
    }

    @GetMapping("/{id}")
    public SubjectDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return SubjectDtos.Response.from(service.get(actor.schoolId(), id));
    }

    @PostMapping
    public ResponseEntity<SubjectDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody SubjectDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SubjectDtos.Response.from(service.create(actor, request)));
    }

    @PutMapping("/{id}")
    public SubjectDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody SubjectDtos.Request request) {
        return SubjectDtos.Response.from(service.update(actor, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a subject that is not assigned to any section")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
