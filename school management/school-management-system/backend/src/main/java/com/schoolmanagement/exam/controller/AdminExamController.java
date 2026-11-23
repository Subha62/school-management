package com.schoolmanagement.exam.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.entity.ExamStatus;
import com.schoolmanagement.exam.entity.GradeRule;
import com.schoolmanagement.exam.service.ExamService;
import com.schoolmanagement.exam.service.GradeRuleService;
import com.schoolmanagement.exam.service.ResultService;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Examinations (administrators)")
public class AdminExamController {

    private static final Set<String> EXAM_SORT = Set.of("name", "status", "startDate", "createdAt");
    private static final Set<String> RULE_SORT = Set.of("grade", "minPercent", "maxPercent");

    private final ExamService exams;
    private final GradeRuleService rules;
    private final ResultService results;
    private final PageRequestFactory pageRequests;

    public AdminExamController(ExamService exams, GradeRuleService rules, ResultService results,
            PageRequestFactory pageRequests) {
        this.exams = exams;
        this.rules = rules;
        this.results = results;
        this.pageRequests = pageRequests;
    }

    // ---- grade scale ------------------------------------------------------------------------------------

    @GetMapping("/grade-rules")
    @Operation(summary = "The grade scale, highest band first (paged)")
    public PageResponse<ExamDtos.GradeRuleResponse> listRules(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, RULE_SORT,
                Sort.by(Sort.Direction.DESC, "minPercent"));
        return PageResponse.from(rules.search(actor.schoolId(), pageable), AdminExamController::toResponse);
    }

    @PostMapping("/grade-rules")
    @Operation(summary = "Add a grade band; bands must not overlap")
    public ResponseEntity<ExamDtos.GradeRuleResponse> createRule(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ExamDtos.GradeRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(rules.create(actor, request)));
    }

    @PutMapping("/grade-rules/{id}")
    public ExamDtos.GradeRuleResponse updateRule(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @Valid @RequestBody ExamDtos.GradeRuleRequest request) {
        return toResponse(rules.update(actor, id, request));
    }

    @DeleteMapping("/grade-rules/{id}")
    public ResponseEntity<Void> deleteRule(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        rules.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static ExamDtos.GradeRuleResponse toResponse(GradeRule rule) {
        return new ExamDtos.GradeRuleResponse(rule.getId(), rule.getGrade(), rule.getDescription(),
                rule.getMinPercent(), rule.getMaxPercent(), rule.getVersion());
    }

    // ---- exams ------------------------------------------------------------------------------------------

    @GetMapping("/exams")
    @Operation(summary = "Exams (paged, filterable)")
    public PageResponse<ExamDtos.ExamResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) ExamStatus status, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, EXAM_SORT,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return exams.list(actor.schoolId(), academicYearId, gradeLevelId, status, search, pageable);
    }

    @GetMapping("/exams/{id}")
    public ExamDtos.ExamResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return exams.get(actor.schoolId(), id);
    }

    @PostMapping("/exams")
    @Operation(summary = "Create a draft exam for a grade in the active academic year")
    public ResponseEntity<ExamDtos.ExamResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ExamDtos.ExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exams.create(actor, request));
    }

    @PutMapping("/exams/{id}")
    @Operation(summary = "Edit a draft exam; send the current version")
    public ExamDtos.ExamResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody ExamDtos.ExamRequest request) {
        return exams.update(actor, id, request);
    }

    @DeleteMapping("/exams/{id}")
    @Operation(summary = "Delete a draft exam")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        exams.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exams/{id}/open")
    @Operation(summary = "Open a draft exam for marks entry")
    public ExamDtos.ExamResponse open(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return exams.open(actor, id);
    }

    @PostMapping("/exams/{id}/lock")
    @Operation(summary = "Lock marks; needs complete marks and a grade scale covering 0-100")
    public ExamDtos.ExamResponse lock(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return exams.lock(actor, id);
    }

    @PostMapping("/exams/{id}/reopen")
    @Operation(summary = "Send a locked (not yet published) exam back to marks entry")
    public ExamDtos.ExamResponse reopen(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return exams.reopen(actor, id);
    }

    @PostMapping("/exams/{id}/publish")
    @Operation(summary = "Publish a locked exam: freezes the results and shows them to students")
    public ExamDtos.ExamResponse publish(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return exams.publish(actor, id);
    }

    @GetMapping("/exams/{id}/progress")
    @Operation(summary = "How much of the marks entry is done, per subject and class")
    public ExamDtos.ProgressResponse progress(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return exams.progress(actor.schoolId(), id);
    }

    @GetMapping("/exams/{id}/results")
    @Operation(summary = "Results of one class (a preview while locked, the frozen copy once published)")
    public ExamDtos.ResultsResponse results(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @RequestParam UUID sectionId) {
        return results.adminResults(actor.schoolId(), id, sectionId);
    }

    @GetMapping("/exams/{id}/results/{studentId}")
    @Operation(summary = "One student's report card")
    public ExamDtos.ReportCard reportCard(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @PathVariable UUID studentId) {
        return results.adminReportCard(actor.schoolId(), id, studentId);
    }
}
