package com.schoolmanagement.academic.controller;

import com.schoolmanagement.academic.dto.TeacherOptionResponse;
import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/teacher-options")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Teacher assignments")
public class TeacherOptionController {

    private static final Set<String> SORTABLE = Set.of("lastName", "firstName", "email");

    private final TeacherDirectory teachers;
    private final PageRequestFactory pageRequests;

    public TeacherOptionController(TeacherDirectory teachers, PageRequestFactory pageRequests) {
        this.teachers = teachers;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "Active teachers that can be assigned to sections and subjects")
    public PageResponse<TeacherOptionResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by("lastName").and(Sort.by("firstName")));
        return PageResponse.from(teachers.searchActiveTeachers(actor.schoolId(), search, pageable),
                TeacherOptionResponse::from);
    }
}
