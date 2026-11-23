package com.schoolmanagement.exam.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One band of the school's grade scale: an inclusive percentage range with two decimals. */
@Entity
@Table(name = "grade_rules")
public class GradeRule extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String grade;

    private String description;

    @Column(nullable = false)
    private BigDecimal minPercent;

    @Column(nullable = false)
    private BigDecimal maxPercent;

    protected GradeRule() {
    }

    public GradeRule(UUID schoolId, String grade, String description, BigDecimal minPercent, BigDecimal maxPercent) {
        this.schoolId = schoolId;
        update(grade, description, minPercent, maxPercent);
    }

    public void update(String grade, String description, BigDecimal minPercent, BigDecimal maxPercent) {
        this.grade = grade;
        this.description = description;
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
    }

    public boolean contains(BigDecimal percentage) {
        return percentage.compareTo(minPercent) >= 0 && percentage.compareTo(maxPercent) <= 0;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getGrade() {
        return grade;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMinPercent() {
        return minPercent;
    }

    public BigDecimal getMaxPercent() {
        return maxPercent;
    }
}
