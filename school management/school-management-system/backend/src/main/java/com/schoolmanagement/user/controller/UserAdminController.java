package com.schoolmanagement.user.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.user.dto.UserDtos;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.service.UserAdminService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "User management")
public class UserAdminController {

    private static final Set<String> SORTABLE = Set.of("username", "firstName", "lastName", "email",
            "accountStatus", "lastLoginAt");

    private final UserAdminService service;
    private final PageRequestFactory pageRequests;

    public UserAdminController(UserAdminService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "List accounts (paged; filter by role and status; search name, username or email)")
    public PageResponse<UserDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) RoleName role, @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("lastName").and(Sort.by("firstName")));
        return service.search(actor.schoolId(), role, status, search, pageable);
    }

    @PostMapping("/admins")
    @Operation(summary = "Create an administrator account")
    public ResponseEntity<UserDtos.Response> createAdmin(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody UserDtos.CreateAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAdmin(actor, request));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Activate, suspend or archive an account; suspending revokes its sessions")
    public UserDtos.Response changeStatus(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody UserDtos.StatusRequest request) {
        return service.changeStatus(actor, id, request);
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Clear a temporary sign-in lockout")
    public UserDtos.Response unlock(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.unlock(actor, id);
    }
}
