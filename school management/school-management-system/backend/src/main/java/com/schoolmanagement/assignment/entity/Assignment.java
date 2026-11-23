package com.schoolmanagement.assignment.entity;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.AuditableEntity;
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

/** Work set by a teacher for one class and subject. Students see it only while it is published or closed. */
@Entity
@Table(name = "assignments")
public class Assignment extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false, updatable = false)
    private Subject subject;

    /** The owning teacher's user account. */
    @Column(nullable = false, updatable = false)
    private UUID teacherId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private BigDecimal maxMarks;

    @Column(nullable = false)
    private boolean allowLate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    private Instant publishedAt;
    private Instant closedAt;

    protected Assignment() {
    }

    public Assignment(Section section, Subject subject, UUID teacherId, String title, String description,
            Instant dueAt, BigDecimal maxMarks, boolean allowLate) {
        this.schoolId = section.getSchoolId();
        this.academicYear = section.getAcademicYear();
        this.section = section;
        this.subject = subject;
        this.teacherId = teacherId;
        update(title, description, dueAt, maxMarks, allowLate);
    }

    public void update(String title, String description, Instant dueAt, BigDecimal maxMarks, boolean allowLate) {
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.maxMarks = maxMarks;
        this.allowLate = allowLate;
    }

    public void publish(Instant now) {
        require(status == AssignmentStatus.DRAFT, "Only a draft can be published.");
        status = AssignmentStatus.PUBLISHED;
        publishedAt = now;
    }

    public void close(Instant now) {
        require(status == AssignmentStatus.PUBLISHED, "Only a published assignment can be closed.");
        status = AssignmentStatus.CLOSED;
        closedAt = now;
    }

    public void reopen() {
        require(status == AssignmentStatus.CLOSED, "Only a closed assignment can be reopened.");
        status = AssignmentStatus.PUBLISHED;
        closedAt = null;
    }

    public void archive() {
        require(status == AssignmentStatus.CLOSED, "Only a closed assignment can be archived.");
        status = AssignmentStatus.ARCHIVED;
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new BusinessRuleException(message);
        }
    }

    public boolean isDraft() {
        return status == AssignmentStatus.DRAFT;
    }

    public boolean isPublished() {
        return status == AssignmentStatus.PUBLISHED;
    }

    /** Students can see it (and teachers can review it) once it has been published, until it is archived. */
    public boolean isVisibleToStudents() {
        return status == AssignmentStatus.PUBLISHED || status == AssignmentStatus.CLOSED;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public Section getSection() {
        return section;
    }

    public Subject getSubject() {
        return subject;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public boolean isAllowLate() {
        return allowLate;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
