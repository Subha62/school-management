package com.schoolmanagement.attendance.entity;

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
import java.util.UUID;

/** Permanent record of an administrator's change to a session: the old value, the new one and the reason. */
@Entity
@Table(name = "attendance_corrections")
public class AttendanceCorrection extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AttendanceStatus newStatus;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private UUID correctedBy;

    protected AttendanceCorrection() {
    }

    public AttendanceCorrection(AttendanceSession session, StudentProfile student, AttendanceStatus previousStatus,
            AttendanceStatus newStatus, String reason, UUID correctedBy) {
        this.session = session;
        this.student = student;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.correctedBy = correctedBy;
    }

    public AttendanceSession getSession() {
        return session;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public AttendanceStatus getPreviousStatus() {
        return previousStatus;
    }

    public AttendanceStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCorrectedBy() {
        return correctedBy;
    }
}
