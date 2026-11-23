package com.schoolmanagement.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of the audit trail. Immutable: Hibernate never issues an update for it. */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(updatable = false)
    private UUID schoolId;

    @Column(updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(updatable = false)
    private String entityType;

    @Column(updatable = false)
    private String entityId;

    @Column(updatable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(updatable = false)
    private String ipAddress;

    @Column(updatable = false)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    /** Builds the row from an event, sanitizing everything that came from the caller. */
    public static AuditLog from(AuditEvent event, Instant now) {
        AuditLog log = new AuditLog();
        log.schoolId = event.schoolId();
        log.actorUserId = event.actorUserId();
        log.action = AuditSanitizer.text(event.action());
        log.entityType = AuditSanitizer.text(event.entityType());
        log.entityId = AuditSanitizer.text(event.entityId());
        log.summary = AuditSanitizer.text(event.summary());
        log.oldValues = AuditSanitizer.values(event.oldValues());
        log.newValues = AuditSanitizer.values(event.newValues());
        log.ipAddress = AuditSanitizer.text(event.ipAddress());
        log.userAgent = AuditSanitizer.text(event.userAgent());
        log.createdAt = now;
        return log;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getOldValues() {
        return oldValues;
    }

    public Map<String, Object> getNewValues() {
        return newValues;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
