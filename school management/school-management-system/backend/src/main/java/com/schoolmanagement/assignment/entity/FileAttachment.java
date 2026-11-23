package com.schoolmanagement.assignment.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/** Metadata of a stored file. {@code originalName} is display text only; the file is found by {@code storageKey}. */
@MappedSuperclass
public abstract class FileAttachment extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private String originalName;

    @Column(nullable = false, updatable = false)
    private String storageKey;

    @Column(nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private UUID uploadedBy;

    protected FileAttachment() {
    }

    protected FileAttachment(String originalName, String storageKey, String contentType, long sizeBytes,
            UUID uploadedBy) {
        this.originalName = originalName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }
}
