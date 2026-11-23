package com.schoolmanagement.attendance.entity;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One attendance-taking occasion for a section on a date: the whole day, or one subject and period. */
@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    @Column(nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, updatable = false)
    private SessionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", updatable = false)
    private Subject subject;

    @Column(updatable = false)
    private Integer periodNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    private UUID finalizedBy;
    private Instant finalizedAt;

    protected AttendanceSession() {
    }

    public AttendanceSession(Section section, LocalDate attendanceDate, Subject subject, Integer periodNumber,
            UUID createdBy) {
        this.schoolId = section.getSchoolId();
        this.academicYear = section.getAcademicYear();
        this.section = section;
        this.attendanceDate = attendanceDate;
        this.subject = subject;
        this.type = subject == null ? SessionType.DAILY : SessionType.SUBJECT;
        this.periodNumber = periodNumber;
        this.createdBy = createdBy;
    }

    public void finalizeBy(UUID userId, Instant when) {
        if (status == SessionStatus.FINALIZED) {
            throw new BusinessRuleException("This attendance has already been finalized.");
        }
        this.status = SessionStatus.FINALIZED;
        this.finalizedBy = userId;
        this.finalizedAt = when;
    }

    public boolean isFinalized() {
        return status == SessionStatus.FINALIZED;
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

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public SessionType getType() {
        return type;
    }

    public Subject getSubject() {
        return subject;
    }

    public Integer getPeriodNumber() {
        return periodNumber;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getFinalizedBy() {
        return finalizedBy;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }
}
