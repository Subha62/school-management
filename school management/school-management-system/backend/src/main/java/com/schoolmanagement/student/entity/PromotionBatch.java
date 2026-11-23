package com.schoolmanagement.student.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Record of one promotion run: who ran it, when, and how many students were promoted or refused. */
@Entity
@Table(name = "promotion_batches")
public class PromotionBatch extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false, updatable = false)
    private UUID sourceAcademicYearId;

    @Column(nullable = false, updatable = false)
    private UUID targetAcademicYearId;

    @Column(nullable = false, updatable = false)
    private UUID performedBy;

    @Column(nullable = false, updatable = false)
    private Instant performedAt;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int promotedCount;

    @Column(nullable = false)
    private int failedCount;

    protected PromotionBatch() {
    }

    public PromotionBatch(UUID schoolId, UUID sourceAcademicYearId, UUID targetAcademicYearId, UUID performedBy,
            Instant performedAt) {
        this.schoolId = schoolId;
        this.sourceAcademicYearId = sourceAcademicYearId;
        this.targetAcademicYearId = targetAcademicYearId;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }

    public void recordOutcome(int promoted, int failed) {
        this.promotedCount = promoted;
        this.failedCount = failed;
        this.totalCount = promoted + failed;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getSourceAcademicYearId() {
        return sourceAcademicYearId;
    }

    public UUID getTargetAcademicYearId() {
        return targetAcademicYearId;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getPromotedCount() {
        return promotedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }
}
