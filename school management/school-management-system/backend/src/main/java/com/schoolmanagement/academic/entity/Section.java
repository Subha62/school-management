package com.schoolmanagement.academic.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sections")
public class Section extends AuditableEntity {

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

    @Column(nullable = false)
    private int capacity;

    /** The class teacher's user account. */
    private UUID classTeacherId;

    @Column(nullable = false)
    private boolean active = true;

    protected Section() {
    }

    public Section(UUID schoolId, AcademicYear academicYear, GradeLevel gradeLevel, String name, int capacity,
            UUID classTeacherId, boolean active) {
        this.schoolId = schoolId;
        this.academicYear = academicYear;
        this.gradeLevel = gradeLevel;
        this.name = name;
        this.capacity = capacity;
        this.classTeacherId = classTeacherId;
        this.active = active;
    }

    public void update(String name, int capacity, UUID classTeacherId, boolean active) {
        this.name = name;
        this.capacity = capacity;
        this.classTeacherId = classTeacherId;
        this.active = active;
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

    public int getCapacity() {
        return capacity;
    }

    public UUID getClassTeacherId() {
        return classTeacherId;
    }

    public boolean isActive() {
        return active;
    }
}
