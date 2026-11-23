package com.schoolmanagement.timetable.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.timetable.dto.TimetableDtos;
import com.schoolmanagement.timetable.entity.TimetableStatus;
import com.schoolmanagement.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Timetable")
public class AdminTimetableController {

    private static final Set<String> SORTABLE = Set.of("dayOfWeek", "periodNumber", "startTime", "status");

    private final TimetableService service;
    private final PageRequestFactory pageRequests;

    public AdminTimetableController(TimetableService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping("/timetable-entries")
    @Operation(summary = "List timetable entries (paged; filter by year, section, teacher, day, status)")
    public PageResponse<TimetableDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID teacherId, @RequestParam(required = false) DayOfWeek day,
            @RequestParam(required = false) TimetableStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("startTime").and(Sort.by("periodNumber")));
        return service.search(actor.schoolId(), academicYearId, sectionId, teacherId, day, status, pageable);
    }

    @GetMapping("/timetable-entries/{id}")
    public TimetableDtos.Response get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return service.getResponse(actor.schoolId(), id);
    }

    @PostMapping("/timetable-entries")
    @Operation(summary = "Create a slot; the subject must be assigned to the section and nothing may clash")
    public ResponseEntity<TimetableDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody TimetableDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor, request));
    }

    @PutMapping("/timetable-entries/{id}")
    @Operation(summary = "Update a slot; send the current version. The section cannot change")
    public TimetableDtos.Response update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody TimetableDtos.Request request) {
        return service.update(actor, id, request);
    }

    @DeleteMapping("/timetable-entries/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/timetable/sections/{sectionId}")
    @Operation(summary = "Weekly timetable of a section (the week containing `date`, default this week)")
    public TimetableDtos.Week sectionWeek(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.sectionWeek(actor.schoolId(), sectionId, date);
    }

    @GetMapping("/timetable/teachers/{teacherId}")
    @Operation(summary = "Weekly timetable of a teacher (the week containing `date`, default this week)")
    public TimetableDtos.Week teacherWeek(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.teacherWeek(actor.schoolId(), teacherId, date);
    }
}
