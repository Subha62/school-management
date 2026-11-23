package com.schoolmanagement.guardian.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.guardian.dto.GuardianDtos;
import com.schoolmanagement.guardian.service.GuardianService;
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
@RequestMapping("/api/v1/admin/guardians")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Guardians")
public class GuardianController {

    private static final Set<String> SORTABLE = Set.of("firstName", "lastName", "relationship", "phoneNumber");

    private final GuardianService service;
    private final PageRequestFactory pageRequests;

    public GuardianController(GuardianService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List guardians (paged; search name, email or phone)")
    public PageResponse<GuardianDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("lastName").and(Sort.by("firstName")));
        return service.search(actor.schoolId(), search, pageable);
    }

    @GetMapping("/{id}")
    public GuardianDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.getResponse(actor.schoolId(), id);
    }

    @PostMapping
    public ResponseEntity<GuardianDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody GuardianDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a guardian; send the current version")
    public GuardianDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody GuardianDtos.Request request) {
        return service.update(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a guardian who is not linked to any student")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }
}
