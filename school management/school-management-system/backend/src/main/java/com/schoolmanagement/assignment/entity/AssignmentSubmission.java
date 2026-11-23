package com.schoolmanagement.assignment.entity;

import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One student's answer to one assignment, and the teacher's review of it. */
@Entity
@Table(name = "assignment_submissions")
public class AssignmentSubmission extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    private String textAnswer;

    @Column(nullable = false)
    private Instant submittedAt;

    private BigDecimal marks;
    private String feedback;
    private UUID reviewedBy;
    private Instant reviewedAt;

    protected AssignmentSubmission() {
    }

    public AssignmentSubmission(Assignment assignment, StudentProfile student, String textAnswer, Instant now,
            boolean late) {
        this.assignment = assignment;
        this.student = student;
        submit(textAnswer, now, late);
    }

    /** Saves the student's answer. A resubmission asked for by the teacher is never counted as late. */
    public void submit(String textAnswer, Instant now, boolean late) {
        if (!isEditableByStudent() && status != null) {
            throw new BusinessRuleException("This submission has already been reviewed and cannot be changed.");
        }
        this.textAnswer = textAnswer;
        this.submittedAt = now;
        this.status = late ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED;
    }

    public void review(BigDecimal marks, String feedback, UUID reviewer, Instant now) {
        requireReviewable();
        this.status = SubmissionStatus.REVIEWED;
        this.marks = marks;
        this.feedback = feedback;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }

    public void requestResubmission(String feedback, UUID reviewer, Instant now) {
        requireReviewable();
        this.status = SubmissionStatus.RESUBMISSION_REQUESTED;
        this.marks = null;
        this.feedback = feedback;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }

    private void requireReviewable() {
        if (status == SubmissionStatus.RESUBMISSION_REQUESTED) {
            throw new BusinessRuleException("A resubmission has been requested; wait for the student's new answer.");
        }
    }

    /** Students may change an answer until it is reviewed, and again if a resubmission was requested. */
    public boolean isEditableByStudent() {
        return status == SubmissionStatus.SUBMITTED || status == SubmissionStatus.LATE
                || status == SubmissionStatus.RESUBMISSION_REQUESTED;
    }

    public boolean isResubmissionRequested() {
        return status == SubmissionStatus.RESUBMISSION_REQUESTED;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public String getTextAnswer() {
        return textAnswer;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public String getFeedback() {
        return feedback;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
