package com.schoolmanagement.assignment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "assignment_attachments")
public class AssignmentAttachment extends FileAttachment {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private Assignment assignment;

    protected AssignmentAttachment() {
    }

    public AssignmentAttachment(Assignment assignment, String originalName, String storageKey, String contentType,
            long sizeBytes, UUID uploadedBy) {
        super(originalName, storageKey, contentType, sizeBytes, uploadedBy);
        this.assignment = assignment;
    }

    public Assignment getAssignment() {
        return assignment;
    }
}
