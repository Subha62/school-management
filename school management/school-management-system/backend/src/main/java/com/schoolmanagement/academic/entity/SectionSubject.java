package com.schoolmanagement.academic.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** A subject taught in a section during an academic year, optionally with an assigned teacher. */
@Entity
@Table(name = "section_subjects")
public class SectionSubject extends AuditableEntity {

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

    /** The teacher's user account. */
    private UUID teacherId;

    @Column(nullable = false)
    private int weeklyPeriods;

    @Column(nullable = false)
    private boolean active = true;

    protected SectionSubject() {
    }

    public SectionSubject(Section section, Subject subject, UUID teacherId, int weeklyPeriods) {
        this.schoolId = section.getSchoolId();
        this.academicYear = section.getAcademicYear();
        this.section = section;
        this.subject = subject;
        this.teacherId = teacherId;
        this.weeklyPeriods = weeklyPeriods;
    }

    public void update(UUID teacherId, int weeklyPeriods, boolean active) {
        this.teacherId = teacherId;
        this.weeklyPeriods = weeklyPeriods;
        this.active = active;
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

    public int getWeeklyPeriods() {
        return weeklyPeriods;
    }

    public boolean isActive() {
        return active;
    }
}
