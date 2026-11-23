package com.schoolmanagement.fee.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A kind of charge: tuition, transport, laboratory and so on. */
@Entity
@Table(name = "fee_categories")
public class FeeCategory extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected FeeCategory() {
    }

    public FeeCategory(UUID schoolId, String name, String description, boolean active) {
        this.schoolId = schoolId;
        update(name, description, active);
    }

    public void update(String name, String description, boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
