package com.schoolmanagement.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Writes each event to the AUDIT log (the operational copy; the database copy is made by the persisting listener). Strips control characters to prevent log injection. */
@Component
class LoggingAuditEventListener {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final int MAX_FIELD_LENGTH = 200;

    @EventListener
    void on(AuditEvent event) {
        AUDIT.info("action={} actor={} school={} entity={}:{} ip={} summary=\"{}\" userAgent=\"{}\"",
                clean(event.action()), event.actorUserId(), event.schoolId(), clean(event.entityType()),
                clean(event.entityId()), clean(event.ipAddress()), clean(event.summary()),
                clean(event.userAgent()));
    }

    static String clean(String value) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}\"]", " ");
        return cleaned.length() > MAX_FIELD_LENGTH ? cleaned.substring(0, MAX_FIELD_LENGTH) : cleaned;
    }
}
