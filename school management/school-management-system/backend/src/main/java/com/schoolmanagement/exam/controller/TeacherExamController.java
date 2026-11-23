package com.schoolmanagement.exam.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.service.MarksService;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/exams")
@PreAuthorize("hasAuthority('ROLE_TEACHER')")
@Tag(name = "Examinations (teachers)")
public class TeacherExamController {

    private static final Set<String> SORTABLE = Set.of("name", "status", "startDate", "createdAt");

    private final MarksService service;
    private final PageRequestFactory pageRequests;

    public TeacherExamController(MarksService service, PageRequestFactory pageRequests) {
        this.service = service;
        this.pageRequests = pageRequests;
    }

    @GetMapping
    @Operation(summary = "Exams I have marks to enter for, or comment on as class teacher (paged)")
    public PageResponse<ExamDtos.TeacherExam> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.myExams(actor, pageable);
    }

    @GetMapping("/{examId}/marks")
    @Operation(summary = "The marks sheet of a subject I teach in a class")
    public ExamDtos.MarksSheet sheet(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID examId,
            @RequestParam UUID examSubjectId, @RequestParam UUID sectionId) {
        return service.sheet(actor, examId, examSubjectId, sectionId);
    }

    @PutMapping("/{examId}/marks")
    @Operation(summary = "Save marks while the exam is open; an entry with no marks and not absent clears it")
    public ExamDtos.MarksSheet save(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID examId,
            @RequestParam UUID examSubjectId, @RequestParam UUID sectionId,
            @Valid @RequestBody ExamDtos.SaveMarksRequest request) {
        return service.save(actor, examId, examSubjectId, sectionId, request);
    }

    @GetMapping("/{examId}/comments")
    @Operation(summary = "Comments on the students of a class I am the class teacher of")
    public ExamDtos.CommentSheet comments(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID examId,
            @RequestParam UUID sectionId) {
        return service.commentSheet(actor, examId, sectionId);
    }

    @PutMapping("/{examId}/comments/{studentId}")
    @Operation(summary = "Write (or, with a blank text, remove) the overall comment on a student")
    public ExamDtos.CommentRow saveComment(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID examId, @PathVariable UUID studentId,
            @Valid @RequestBody ExamDtos.SaveCommentRequest request) {
        return service.saveComment(actor, examId, studentId, request);
    }
}
