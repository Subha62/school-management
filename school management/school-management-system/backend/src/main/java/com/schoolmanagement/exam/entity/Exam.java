package com.schoolmanagement.exam.entity;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An examination for all classes of one grade in one academic year. The lifecycle only moves forward, except that
 * an administrator can send a locked exam back to marks entry before it is published.
 */
@Entity
@Table(name = "exams")
public class Exam extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_level_id", nullable = false, updatable = false)
    private GradeLevel gradeLevel;

    @Column(nullable = false)
    private String name;

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamStatus status = ExamStatus.DRAFT;

    private Instant lockedAt;
    private UUID lockedBy;
    private Instant publishedAt;
    private UUID publishedBy;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt, id")
    private List<ExamSubject> subjects = new ArrayList<>();

    protected Exam() {
    }

    public Exam(UUID schoolId, AcademicYear academicYear, GradeLevel gradeLevel, String name, LocalDate startDate,
            LocalDate endDate) {
        this.schoolId = schoolId;
        this.academicYear = academicYear;
        this.gradeLevel = gradeLevel;
        rename(name, startDate, endDate);
    }

    public void rename(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void open() {
        require(status == ExamStatus.DRAFT, "Only a draft exam can be opened for marks.");
        status = ExamStatus.OPEN_FOR_MARKS;
    }

    public void lock(Instant now, UUID by) {
        require(status == ExamStatus.OPEN_FOR_MARKS, "Only an exam that is open for marks can be locked.");
        status = ExamStatus.LOCKED;
        lockedAt = now;
        lockedBy = by;
    }

    public void reopen() {
        require(status == ExamStatus.LOCKED, "Only a locked exam can be reopened for marks.");
        status = ExamStatus.OPEN_FOR_MARKS;
        lockedAt = null;
        lockedBy = null;
    }

    public void publish(Instant now, UUID by) {
        require(status == ExamStatus.LOCKED, "Lock the exam before publishing it.");
        status = ExamStatus.PUBLISHED;
        publishedAt = now;
        publishedBy = by;
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new BusinessRuleException(message);
        }
    }

    public boolean isDraft() {
        return status == ExamStatus.DRAFT;
    }

    public boolean isOpen() {
        return status == ExamStatus.OPEN_FOR_MARKS;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public GradeLevel getGradeLevel() {
        return gradeLevel;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ExamStatus getStatus() {
        return status;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public List<ExamSubject> getSubjects() {
        return subjects;
    }
}
