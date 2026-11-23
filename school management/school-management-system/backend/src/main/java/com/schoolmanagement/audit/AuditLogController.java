package com.schoolmanagement.audit;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only: the audit trail has no write endpoint at all. */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Audit logs")
public class AuditLogController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "action", "entityType");

    private final AuditLogService service;
    private final PageRequestFactory pageRequests;

    public AuditLogController(AuditLogService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "The audit trail of the school, newest first (paged, filterable)")
    public PageResponse<AuditLogService.Entry> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String action, @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId, @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.search(actor.schoolId(),
                new AuditLogService.Filters(action, entityType, entityId, actorId, from, to, search), pageable);
    }

    @GetMapping("/actions")
    @Operation(summary = "The actions that appear in the trail, for the filter")
    public List<String> actions(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.actions(actor.schoolId());
    }

    @GetMapping("/entity-types")
    public List<String> entityTypes(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.entityTypes(actor.schoolId());
    }

    @GetMapping("/{id}")
    public AuditLogService.Entry get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.get(actor.schoolId(), id);
    }
}
