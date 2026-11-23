package com.schoolmanagement.student.entity;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.academic.entity.Section;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One student's place in one section for one academic year. Rows are history: they end (transferred, promoted,
 * withdrawn, completed) but are never rewritten or deleted.
 */
@Entity
@Table(name = "student_enrollments")
public class StudentEnrollment extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_level_id", nullable = false, updatable = false)
    private GradeLevel gradeLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    private Integer rollNumber;

    @Column(nullable = false, updatable = false)
    private LocalDate enrollmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(updatable = false)
    private UUID sourceEnrollmentId;

    @Column(updatable = false)
    private UUID promotionBatchId;

    @Column(updatable = false)
    private UUID createdBy;

    protected StudentEnrollment() {
    }

    public StudentEnrollment(StudentProfile student, Section section, Integer rollNumber, LocalDate enrollmentDate,
            UUID createdBy, UUID sourceEnrollmentId, UUID promotionBatchId) {
        this.schoolId = section.getSchoolId();
        this.student = student;
        this.academicYear = section.getAcademicYear();
        this.gradeLevel = section.getGradeLevel();
        this.section = section;
        this.rollNumber = rollNumber;
        this.enrollmentDate = enrollmentDate;
        this.createdBy = createdBy;
        this.sourceEnrollmentId = sourceEnrollmentId;
        this.promotionBatchId = promotionBatchId;
    }

    /** Ends an active enrollment with the given outcome. */
    public void end(EnrollmentStatus outcome) {
        if (status != EnrollmentStatus.ACTIVE) {
            throw new BusinessRuleException("Only an active enrollment can be changed.");
        }
        if (outcome == EnrollmentStatus.ACTIVE) {
            throw new IllegalArgumentException("An enrollment cannot end as active");
        }
        this.status = outcome;
    }

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public GradeLevel getGradeLevel() {
        return gradeLevel;
    }

    public Section getSection() {
        return section;
    }

    public Integer getRollNumber() {
        return rollNumber;
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public UUID getSourceEnrollmentId() {
        return sourceEnrollmentId;
    }

    public UUID getPromotionBatchId() {
        return promotionBatchId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
