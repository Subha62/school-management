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

/** One student's status in one session (unique per pair). */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    private String remarks;

    @Column(nullable = false)
    private UUID markedBy;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(AttendanceSession session, StudentProfile student, AttendanceStatus status,
            String remarks, UUID markedBy) {
        this.session = session;
        this.student = student;
        change(status, remarks, markedBy);
    }

    public void change(AttendanceStatus status, String remarks, UUID markedBy) {
        this.status = status;
        this.remarks = remarks;
        this.markedBy = markedBy;
    }

    public AttendanceSession getSession() {
        return session;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getMarkedBy() {
        return markedBy;
    }
}
