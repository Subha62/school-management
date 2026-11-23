package com.schoolmanagement.fee.entity;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** What every student of a grade is charged for a category in a year, and when it falls due. */
@Entity
@Table(name = "fee_structures")
public class FeeStructure extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_level_id", nullable = false, updatable = false)
    private GradeLevel gradeLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_category_id", nullable = false, updatable = false)
    private FeeCategory category;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean active = true;

    protected FeeStructure() {
    }

    public FeeStructure(UUID schoolId, AcademicYear academicYear, GradeLevel gradeLevel, FeeCategory category,
            String description, BigDecimal amount, LocalDate dueDate, boolean active) {
        this.schoolId = schoolId;
        this.academicYear = academicYear;
        this.gradeLevel = gradeLevel;
        this.category = category;
        update(description, amount, dueDate, active);
    }

    public void update(String description, BigDecimal amount, LocalDate dueDate, boolean active) {
        this.description = description;
        this.amount = amount;
        this.dueDate = dueDate;
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

    public FeeCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public boolean isActive() {
        return active;
    }
}
