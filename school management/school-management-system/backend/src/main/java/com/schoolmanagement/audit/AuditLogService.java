package com.schoolmanagement.audit;

import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.school.service.SchoolTime;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading the audit trail (administrators only, one school only). Nothing here can change it. */
@Service
public class AuditLogService {

    private final AuditLogRepository logs;
    private final TeacherDirectory directory;
    private final SchoolTime schoolTime;

    public AuditLogService(AuditLogRepository logs, TeacherDirectory directory, SchoolTime schoolTime) {
        this.logs = logs;
        this.directory = directory;
        this.schoolTime = schoolTime;
    }

    public record Entry(UUID id, Instant createdAt, String action, String entityType, String entityId,
            String summary, UUID actorId, String actorName, String ipAddress, String userAgent,
            Map<String, Object> oldValues, Map<String, Object> newValues) {
    }

    public record Filters(String action, String entityType, String entityId, UUID actorId, LocalDate from,
            LocalDate to, String search) {
    }

    @Transactional(readOnly = true)
    public PageResponse<Entry> search(UUID schoolId, Filters filters, Pageable pageable) {
        ZoneId zone = schoolTime.zone(schoolId);
        Specification<AuditLog> spec = Specification.<AuditLog>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("action", blankToNull(filters.action())))
                .and(SearchSpecs.equalTo("entityType", blankToNull(filters.entityType())))
                .and(SearchSpecs.equalTo("entityId", blankToNull(filters.entityId())))
                .and(SearchSpecs.equalTo("actorUserId", filters.actorId()))
                .and(SearchSpecs.containsIgnoreCase("summary", filters.search()));
        if (filters.from() != null) {
            Instant start = filters.from().atStartOfDay(zone).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
        }
        if (filters.to() != null) {
            Instant end = filters.to().plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), end));
        }
        Page<AuditLog> page = logs.findAll(spec, pageable);
        Map<UUID, String> names = names(page.getContent());
        return PageResponse.from(page, log -> entry(log, names));
    }

    @Transactional(readOnly = true)
    public Entry get(UUID schoolId, UUID id) {
        AuditLog log = logs.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log entry"));
        return entry(log, names(List.of(log)));
    }

    @Transactional(readOnly = true)
    public List<String> actions(UUID schoolId) {
        return logs.findActions(schoolId);
    }

    @Transactional(readOnly = true)
    public List<String> entityTypes(UUID schoolId) {
        return logs.findEntityTypes(schoolId);
    }

    /** The latest entries, for the administrator dashboard. */
    @Transactional(readOnly = true)
    public List<Entry> recent(UUID schoolId, int count) {
        return search(schoolId, new Filters(null, null, null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, count,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                                "createdAt"))).content();
    }

    private Map<UUID, String> names(List<AuditLog> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(log -> {
            if (log.getActorUserId() != null) {
                ids.add(log.getActorUserId());
            }
        });
        return directory.displayNames(ids);
    }

    private static Entry entry(AuditLog log, Map<UUID, String> names) {
        return new Entry(log.getId(), log.getCreatedAt(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getSummary(), log.getActorUserId(),
                log.getActorUserId() == null ? null : names.get(log.getActorUserId()), log.getIpAddress(),
                log.getUserAgent(), log.getOldValues(), log.getNewValues());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
