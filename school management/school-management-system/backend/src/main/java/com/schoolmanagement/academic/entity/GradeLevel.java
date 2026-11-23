package com.schoolmanagement.academic.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "grade_levels")
public class GradeLevel extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected GradeLevel() {
    }

    public GradeLevel(UUID schoolId, String name, int displayOrder, boolean active) {
        this.schoolId = schoolId;
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public void update(String name, int displayOrder, boolean active) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getName() {
        return name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
