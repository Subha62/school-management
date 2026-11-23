package com.schoolmanagement.audit;

import com.schoolmanagement.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditEventPublisher {

    private static final int MAX_IP = 45;
    private static final int MAX_USER_AGENT = 255;

    private final ApplicationEventPublisher publisher;

    public AuditEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(AuditEvent event) {
        publisher.publishEvent(event);
    }

    /**
     * Records an action performed by a signed-in user. The client address and user agent are taken from the
     * current request, so services do not have to pass web details around.
     */
    public void record(AuthenticatedUser actor, String action, String entityType, Object entityId,
            String summary) {
        record(actor, action, entityType, entityId, summary, null, null);
    }

    /** As above, with the values before and after a change (credentials are redacted before they are stored). */
    public void record(AuthenticatedUser actor, String action, String entityType, Object entityId,
            String summary, java.util.Map<String, Object> oldValues, java.util.Map<String, Object> newValues) {
        String ip = null;
        String userAgent = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            ip = truncate(request.getRemoteAddr(), MAX_IP);
            userAgent = truncate(request.getHeader("User-Agent"), MAX_USER_AGENT);
        }
        publish(new AuditEvent(action, actor.id(), actor.schoolId(), entityType,
                entityId == null ? null : entityId.toString(), summary, ip, userAgent, oldValues, newValues));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
