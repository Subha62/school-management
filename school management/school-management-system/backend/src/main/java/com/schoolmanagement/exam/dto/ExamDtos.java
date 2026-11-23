package com.schoolmanagement.exam.dto;

import com.schoolmanagement.exam.entity.ExamStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExamDtos {

    private ExamDtos() {
    }

    // ---- grade scale ------------------------------------------------------------------------------------

    public record GradeRuleRequest(
            @NotBlank(message = "Grade is required") @Size(max = 10, message = "Grade must be at most 10 characters") String grade,
            @Size(max = 100, message = "Description must be at most 100 characters") String description,
            @NotNull(message = "Minimum percentage is required") @DecimalMin(value = "0", message = "Minimum must be at least 0") @DecimalMax(value = "100", message = "Minimum must be at most 100") @Digits(integer = 3, fraction = 2, message = "Use at most two decimal places") BigDecimal minPercent,
            @NotNull(message = "Maximum percentage is required") @DecimalMin(value = "0", message = "Maximum must be at least 0") @DecimalMax(value = "100", message = "Maximum must be at most 100") @Digits(integer = 3, fraction = 2, message = "Use at most two decimal places") BigDecimal maxPercent,
            Long version) {
    }

    public record GradeRuleResponse(UUID id, String grade, String description, BigDecimal minPercent,
            BigDecimal maxPercent, long version) {
    }

    // ---- exams (administrators) -------------------------------------------------------------------------

    public record ExamSubjectRequest(
            @NotNull(message = "Subject is required") UUID subjectId,
            @NotNull(message = "Maximum marks are required") @DecimalMin(value = "0.01", message = "Maximum marks must be more than 0") @DecimalMax(value = "1000", message = "Maximum marks must be at most 1000") @Digits(integer = 4, fraction = 2, message = "Use at most two decimal places") BigDecimal maxMarks,
            @NotNull(message = "Pass marks are required") @DecimalMin(value = "0", message = "Pass marks cannot be negative") @Digits(integer = 4, fraction = 2, message = "Use at most two decimal places") BigDecimal passMarks,
            LocalDate examDate) {
    }

    /** {@code academicYearId} and {@code gradeLevelId} are used when creating and ignored on update. */
    public record ExamRequest(
            UUID academicYearId,
            UUID gradeLevelId,
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            LocalDate startDate,
            LocalDate endDate,
            @NotEmpty(message = "Add at least one subject") @Size(max = 30, message = "An exam can have at most 30 subjects") List<@Valid ExamSubjectRequest> subjects,
            Long version) {
    }

    public record ExamSubjectResponse(UUID id, UUID subjectId, String subjectCode, String subjectName,
            BigDecimal maxMarks, BigDecimal passMarks, LocalDate examDate) {
    }

    public record ExamResponse(UUID id, UUID academicYearId, String academicYearName, UUID gradeLevelId,
            String gradeLevelName, String name, LocalDate startDate, LocalDate endDate, ExamStatus status,
            List<ExamSubjectResponse> subjects, Instant lockedAt, Instant publishedAt, long version) {
    }

    /** How much of the required marks entry is done for one subject in one class. */
    public record ProgressRow(UUID examSubjectId, String subjectName, UUID sectionId, String sectionName,
            String teacherName, int expected, int entered) {
    }

    public record ProgressResponse(UUID examId, ExamStatus status, boolean complete, int missing,
            List<ProgressRow> rows) {
    }

    // ---- marks entry (teachers) -------------------------------------------------------------------------

    public record SectionRef(UUID id, String name) {
    }

    /** One subject of an exam in one class that the teacher teaches. */
    public record Task(UUID examSubjectId, String subjectName, UUID sectionId, String sectionName,
            BigDecimal maxMarks, BigDecimal passMarks, int expected, int entered) {
    }

    public record TeacherExam(UUID id, String name, ExamStatus status, String academicYearName,
            String gradeLevelName, LocalDate startDate, LocalDate endDate, List<Task> tasks,
            List<SectionRef> commentSections) {
    }

    public record MarkRow(UUID studentId, String admissionNumber, String studentName, Integer rollNumber,
            BigDecimal marks, boolean absent, String remarks, boolean entered) {
    }

    public record MarksSheet(UUID examId, String examName, ExamStatus status, UUID examSubjectId,
            String subjectName, UUID sectionId, String sectionName, BigDecimal maxMarks, BigDecimal passMarks,
            boolean editable, List<MarkRow> rows) {
    }

    /**
     * An entry with no marks and not absent clears what was entered. Absent students must not have marks (that is
     * checked by the service, which reports the field).
     */
    public record MarkEntry(
            @NotNull(message = "Student is required") UUID studentId,
            @DecimalMin(value = "0", message = "Marks cannot be negative") @Digits(integer = 4, fraction = 2, message = "Use at most two decimal places") BigDecimal marks,
            boolean absent,
            @Size(max = 200, message = "Remarks must be at most 200 characters") String remarks) {
    }

    public record SaveMarksRequest(
            @NotEmpty(message = "Nothing to save") @Size(max = 500, message = "At most 500 students at a time") List<@Valid MarkEntry> entries) {
    }

    public record CommentRow(UUID studentId, String admissionNumber, String studentName, Integer rollNumber,
            String comment) {
    }

    public record CommentSheet(UUID examId, String examName, ExamStatus status, UUID sectionId,
            String sectionName, boolean editable, List<CommentRow> rows) {
    }

    public record SaveCommentRequest(
            @Size(max = 500, message = "The comment must be at most 500 characters") String comment) {
    }

    // ---- results ----------------------------------------------------------------------------------------

    public enum SubjectStatus {
        PASS,
        FAIL,
        ABSENT
    }

    /** A subject line of a result. Stored as JSON in published results, so keep the fields stable. */
    public record SubjectResult(UUID subjectId, String subjectCode, String subjectName, BigDecimal maxMarks,
            BigDecimal passMarks, BigDecimal marks, boolean absent, BigDecimal percentage, String grade,
            SubjectStatus status, String remarks) {
    }

    public record StudentResult(UUID studentId, String admissionNumber, String studentName, UUID sectionId,
            String sectionName, Integer rollNumber, List<SubjectResult> subjects, BigDecimal totalMarks,
            BigDecimal totalMax, BigDecimal percentage, String grade, String result, String comment) {
    }

    /** {@code preview} is true while the exam is locked and nothing is frozen yet. */
    public record ResultsResponse(UUID examId, String examName, ExamStatus status, boolean preview, UUID sectionId,
            List<StudentResult> results) {
    }

    public record ReportCard(UUID examId, String examName, String schoolName, String academicYearName,
            String gradeLevelName, LocalDate startDate, LocalDate endDate, Instant publishedAt,
            StudentResult result) {
    }

    public record ResultSummary(UUID examId, String examName, String academicYearName, String gradeLevelName,
            Instant publishedAt, BigDecimal percentage, String grade, String result) {
    }
}
