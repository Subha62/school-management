package com.schoolmanagement.assignment.dto;

import com.schoolmanagement.assignment.entity.AssignmentStatus;
import com.schoolmanagement.assignment.entity.SubmissionStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    /** {@code sectionId} and {@code subjectId} are used when creating and ignored on update. */
    public record Request(
            UUID sectionId,
            UUID subjectId,
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be at most 200 characters") String title,
            @Size(max = 5000, message = "Instructions must be at most 5000 characters") String description,
            @NotNull(message = "Due date is required") Instant dueAt,
            @NotNull(message = "Maximum marks are required") @DecimalMin(value = "0.01", message = "Maximum marks must be more than 0") @DecimalMax(value = "1000", message = "Maximum marks must be at most 1000") @Digits(integer = 4, fraction = 2, message = "Use at most two decimal places") BigDecimal maxMarks,
            @NotNull(message = "Late submission choice is required") Boolean allowLate,
            Long version) {
    }

    public record AttachmentResponse(UUID id, String name, String contentType, long sizeBytes, Instant uploadedAt) {
    }

    /** {@code awaitingReview} counts submitted and late work; {@code submitted} counts everything handed in. */
    public record Counts(int enrolled, int submitted, int awaitingReview, int reviewed) {
        public static final Counts NONE = new Counts(0, 0, 0, 0);
    }

    public record Response(UUID id, UUID sectionId, String sectionName, String academicYearName, UUID subjectId,
            String subjectName, UUID teacherId, String teacherName, String title, String description,
            Instant dueAt, BigDecimal maxMarks, boolean allowLate, AssignmentStatus status, Instant publishedAt,
            Instant closedAt, List<AttachmentResponse> attachments, Counts counts, long version) {
    }

    public record SubmissionResponse(UUID id, UUID assignmentId, UUID studentId, String admissionNumber,
            String studentName, SubmissionStatus status, String textAnswer, Instant submittedAt, BigDecimal marks,
            String feedback, Instant reviewedAt, String reviewedBy, List<AttachmentResponse> attachments,
            long version) {
    }

    /** What a student sees of an assignment: no counts, and only their own submission. */
    public record StudentView(UUID id, String subjectName, String sectionName, String teacherName, String title,
            String description, Instant dueAt, BigDecimal maxMarks, boolean allowLate, AssignmentStatus status,
            List<AttachmentResponse> attachments, SubmissionResponse submission, boolean canSubmit) {
    }

    /** A student of the class with the state of their work; {@code status} is null until they submit. */
    public record RosterRow(UUID studentId, String admissionNumber, String studentName, Integer rollNumber,
            UUID submissionId, SubmissionStatus status, Instant submittedAt, BigDecimal marks) {
    }

    public enum ReviewOutcome {
        REVIEWED,
        RESUBMISSION_REQUESTED
    }

    /** Marks are required to complete a review; feedback is required to ask for a resubmission. */
    public record ReviewRequest(
            @NotNull(message = "Outcome is required") ReviewOutcome outcome,
            @DecimalMin(value = "0", message = "Marks cannot be negative") @Digits(integer = 4, fraction = 2, message = "Use at most two decimal places") BigDecimal marks,
            @Size(max = 2000, message = "Feedback must be at most 2000 characters") String feedback,
            @NotNull(message = "The current record version is required") Long version) {
    }

    /** One row of a list of submissions (the review queue, or a student's own work). */
    public record SubmissionListItem(UUID id, UUID assignmentId, String assignmentTitle, String subjectName,
            String sectionName, UUID studentId, String admissionNumber, String studentName,
            SubmissionStatus status, Instant submittedAt, Instant dueAt, BigDecimal marks, BigDecimal maxMarks) {
    }
}
