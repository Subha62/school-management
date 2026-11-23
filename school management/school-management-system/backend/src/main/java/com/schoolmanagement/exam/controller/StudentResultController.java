package com.schoolmanagement.exam.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.service.ResultService;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Students see only published results, and only their own. */
@RestController
@RequestMapping("/api/v1/student/results")
@PreAuthorize("hasAuthority('ROLE_STUDENT')")
@Tag(name = "Results (students)")
public class StudentResultController {

    private static final Set<String> SORTABLE = Set.of("createdAt");

    private final ResultService service;
    private final PageRequestFactory pageRequests;

    public StudentResultController(ResultService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "My published results, newest first")
    public PageResponse<ExamDtos.ResultSummary> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.studentResults(actor, pageable);
    }

    @GetMapping("/{examId}")
    @Operation(summary = "My report card for a published exam")
    public ExamDtos.ReportCard reportCard(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID examId) {
        return service.studentReportCard(actor, examId);
    }
}
