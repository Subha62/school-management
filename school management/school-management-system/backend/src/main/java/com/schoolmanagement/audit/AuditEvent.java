package com.schoolmanagement.audit;

import java.util.Map;
import java.util.UUID;

/**
 * A security-relevant occurrence. Never put passwords, tokens or other secrets in any field; {@code oldValues} and
 * {@code newValues} are additionally redacted and shortened by {@link AuditSanitizer} before they are stored.
 */
public record AuditEvent(
        String action,
        UUID actorUserId,
        UUID schoolId,
        String entityType,
        String entityId,
        String summary,
        String ipAddress,
        String userAgent,
        Map<String, Object> oldValues,
        Map<String, Object> newValues) {

    public AuditEvent(String action, UUID actorUserId, UUID schoolId, String entityType, String entityId,
            String summary, String ipAddress, String userAgent) {
        this(action, actorUserId, schoolId, entityType, entityId, summary, ipAddress, userAgent, null, null);
    }
}
