package com.schoolmanagement.academic.entity;

import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_years")
public class AcademicYear extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcademicYearStatus status;

    protected AcademicYear() {
    }

    public AcademicYear(UUID schoolId, String name, LocalDate startDate, LocalDate endDate) {
        this.schoolId = schoolId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = AcademicYearStatus.PLANNED;
    }

    public void update(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void activate() {
        if (status != AcademicYearStatus.PLANNED) {
            throw new BusinessRuleException("Only a planned academic year can be activated.");
        }
        status = AcademicYearStatus.ACTIVE;
    }

    public void close() {
        if (status != AcademicYearStatus.ACTIVE) {
            throw new BusinessRuleException("Only the active academic year can be closed.");
        }
        status = AcademicYearStatus.CLOSED;
    }

    public boolean isClosed() {
        return status == AcademicYearStatus.CLOSED;
    }

    public UUID getSchoolId() {
        return schoolId;
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

    public AcademicYearStatus getStatus() {
        return status;
    }
}
