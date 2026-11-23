package com.schoolmanagement.teacher.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.teacher.dto.TeacherDtos;
import com.schoolmanagement.teacher.entity.EmploymentStatus;
import com.schoolmanagement.teacher.entity.TeacherProfile;
import com.schoolmanagement.teacher.repository.TeacherProfileRepository;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.service.AccountRules;
import com.schoolmanagement.user.service.UserProvisioner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherService {

    private static final String ENTITY = "TeacherProfile";

    private final TeacherProfileRepository teachers;
    private final UserProvisioner provisioner;
    private final AccountRules accountRules;
    private final AuditEventPublisher audit;
    private final EntityManager entityManager;

    public TeacherService(TeacherProfileRepository teachers, UserProvisioner provisioner,
            AccountRules accountRules, AuditEventPublisher audit, EntityManager entityManager) {
        this.teachers = teachers;
        this.provisioner = provisioner;
        this.accountRules = accountRules;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherDtos.Response> search(UUID schoolId, EmploymentStatus status, String search,
            Pageable pageable) {
        Specification<TeacherProfile> spec = Specification
                .<TeacherProfile>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("employmentStatus", status))
                .and(matchesText(search));
        return PageResponse.from(teachers.findAll(spec, pageable), TeacherDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public TeacherProfile get(UUID schoolId, UUID id) {
        return teachers.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Teacher"));
    }

    @Transactional
    public TeacherDtos.Response create(AuthenticatedUser actor, TeacherDtos.Request request) {
        UUID schoolId = actor.schoolId();
        accountRules.requireText(request.employeeId(), "employeeId", "Employee ID is required");
        accountRules.requireStrongPassword(request.password());
        String employeeId = request.employeeId().trim();
        if (teachers.existsBySchoolIdAndEmployeeIdIgnoreCase(schoolId, employeeId)) {
            throw new ConflictException("A teacher with the employee ID \"" + employeeId + "\" already exists.");
        }
        accountRules.requireAvailableLoginId(schoolId, employeeId, "employee ID");
        String email = request.email().trim();
        accountRules.requireAvailableEmail(schoolId, email, null);

        User user = provisioner.createActiveUser(schoolId, employeeId, email, request.password(),
                request.firstName().trim(), request.lastName().trim(), RoleName.ROLE_TEACHER);
        user.updateContact(user.getFirstName(), user.getLastName(), user.getEmail(), blankToNull(request.phoneNumber()));

        TeacherProfile profile = new TeacherProfile(schoolId, user, employeeId);
        profile.update(request.joiningDate(), blankToNull(request.qualification()),
                blankToNull(request.specialization()), request.employmentStatus());
        teachers.saveAndFlush(profile);
        audit.record(actor, "TEACHER_CREATED", ENTITY, profile.getId(), "Created teacher " + employeeId);
        return TeacherDtos.Response.from(profile);
    }

    @Transactional
    public TeacherDtos.Response update(AuthenticatedUser actor, UUID id, TeacherDtos.Request request) {
        TeacherProfile profile = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), profile.getVersion());
        // Contact details live on the user row, so the profile version must be bumped even if only they change.
        // The pessimistic variant bumps it immediately, so the response carries the new version.
        entityManager.lock(profile, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        User user = profile.getUser();
        String email = request.email().trim();
        accountRules.requireAvailableEmail(actor.schoolId(), email, user.getId());

        EmploymentStatus previous = profile.getEmploymentStatus();
        user.updateContact(request.firstName().trim(), request.lastName().trim(), email,
                blankToNull(request.phoneNumber()));
        profile.update(request.joiningDate(), blankToNull(request.qualification()),
                blankToNull(request.specialization()), request.employmentStatus());
        teachers.saveAndFlush(profile);
        audit.record(actor, "TEACHER_UPDATED", ENTITY, id, "Updated teacher " + profile.getEmployeeId()
                + (previous != request.employmentStatus()
                        ? " (employment " + previous + " to " + request.employmentStatus() + ")" : ""));
        return TeacherDtos.Response.from(profile);
    }

    private static Specification<TeacherProfile> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = SearchSpecs.likePattern(search);
        return (root, query, cb) -> {
            Join<Object, Object> user = root.join("user");
            Expression<String> first = cb.lower(user.get("firstName"));
            Expression<String> last = cb.lower(user.get("lastName"));
            return cb.or(
                    cb.like(cb.lower(root.get("employeeId")), pattern, '\\'),
                    cb.like(first, pattern, '\\'),
                    cb.like(last, pattern, '\\'),
                    cb.like(cb.concat(cb.concat(first, " "), last), pattern, '\\'),
                    cb.like(cb.lower(user.get("email")), pattern, '\\'));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
