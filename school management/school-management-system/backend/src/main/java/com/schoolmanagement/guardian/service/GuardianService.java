package com.schoolmanagement.guardian.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.guardian.dto.GuardianDtos;
import com.schoolmanagement.guardian.entity.Guardian;
import com.schoolmanagement.guardian.repository.GuardianRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.repository.StudentGuardianRepository;
import jakarta.persistence.criteria.Expression;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardianService {

    private static final String ENTITY = "Guardian";

    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final AuditEventPublisher audit;

    public GuardianService(GuardianRepository guardians, StudentGuardianRepository links,
            AuditEventPublisher audit) {
        this.guardians = guardians;
        this.links = links;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<GuardianDtos.Response> search(UUID schoolId, String search, Pageable pageable) {
        Specification<Guardian> spec = Specification.<Guardian>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(matchesText(search));
        Page<Guardian> page = guardians.findAll(spec, pageable);
        Map<UUID, Long> counts = studentCounts(page.getContent().stream().map(Guardian::getId).toList());
        return PageResponse.from(page, g -> GuardianDtos.Response.from(g, counts.getOrDefault(g.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public GuardianDtos.Response getResponse(UUID schoolId, UUID id) {
        Guardian guardian = get(schoolId, id);
        return GuardianDtos.Response.from(guardian, studentCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional(readOnly = true)
    public Guardian get(UUID schoolId, UUID id) {
        return guardians.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian"));
    }

    @Transactional
    public GuardianDtos.Response create(AuthenticatedUser actor, GuardianDtos.Request r) {
        Guardian guardian = guardians.saveAndFlush(new Guardian(actor.schoolId(), r.firstName().trim(),
                r.lastName().trim(), r.relationship(), blankToNull(r.email()), r.phoneNumber().trim(),
                blankToNull(r.occupation()), blankToNull(r.address()), r.emergencyContact()));
        audit.record(actor, "GUARDIAN_CREATED", ENTITY, guardian.getId(),
                "Created guardian " + guardian.getFirstName() + " " + guardian.getLastName());
        return GuardianDtos.Response.from(guardian, 0);
    }

    @Transactional
    public GuardianDtos.Response update(AuthenticatedUser actor, UUID id, GuardianDtos.Request r) {
        Guardian guardian = get(actor.schoolId(), id);
        OptimisticVersion.check(r.version(), guardian.getVersion());
        guardian.update(r.firstName().trim(), r.lastName().trim(), r.relationship(), blankToNull(r.email()),
                r.phoneNumber().trim(), blankToNull(r.occupation()), blankToNull(r.address()), r.emergencyContact());
        guardians.saveAndFlush(guardian);
        audit.record(actor, "GUARDIAN_UPDATED", ENTITY, id,
                "Updated guardian " + guardian.getFirstName() + " " + guardian.getLastName());
        return GuardianDtos.Response.from(guardian, studentCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        Guardian guardian = get(actor.schoolId(), id);
        if (links.existsByGuardianId(id)) {
            throw new ConflictException("This guardian is linked to students. Unlink them first.");
        }
        guardians.delete(guardian);
        audit.record(actor, "GUARDIAN_DELETED", ENTITY, id,
                "Deleted guardian " + guardian.getFirstName() + " " + guardian.getLastName());
    }

    private Map<UUID, Long> studentCounts(Collection<UUID> ids) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : links.countStudentsByGuardian(ids)) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return counts;
    }

    private static Specification<Guardian> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = SearchSpecs.likePattern(search);
        return (root, query, cb) -> {
            Expression<String> first = cb.lower(root.get("firstName"));
            Expression<String> last = cb.lower(root.get("lastName"));
            return cb.or(
                    cb.like(first, pattern, '\\'),
                    cb.like(last, pattern, '\\'),
                    cb.like(cb.concat(cb.concat(first, " "), last), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\'),
                    cb.like(root.get("phoneNumber"), pattern, '\\'));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
