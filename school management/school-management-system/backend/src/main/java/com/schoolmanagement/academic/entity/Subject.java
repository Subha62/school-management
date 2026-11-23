package com.schoolmanagement.academic.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "subjects")
public class Subject extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected Subject() {
    }

    public Subject(UUID schoolId, String code, String name, String description, boolean active) {
        this.schoolId = schoolId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public void update(String code, String name, String description, boolean active) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getCode() {
        return code;
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
