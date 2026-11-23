package com.schoolmanagement.announcement.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Read receipts are written with a single insert-or-ignore statement (see the repository), so this mapping exists
 * for reading and schema validation only.
 */
@Entity
@Table(name = "announcement_reads")
public class AnnouncementRead extends AuditableEntity {

    @jakarta.persistence.Column(name = "announcement_id", nullable = false, updatable = false)
    private java.util.UUID announcementId;

    @jakarta.persistence.Column(name = "user_id", nullable = false, updatable = false)
    private java.util.UUID userId;

    @jakarta.persistence.Column(nullable = false, updatable = false)
    private java.time.Instant readAt;

    protected AnnouncementRead() {
    }

    public java.util.UUID getAnnouncementId() {
        return announcementId;
    }

    public java.util.UUID getUserId() {
        return userId;
    }

    public java.time.Instant getReadAt() {
        return readAt;
    }
}
