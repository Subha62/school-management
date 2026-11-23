package com.schoolmanagement.student.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.dto.StudentDtos;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.service.AccountRules;
import com.schoolmanagement.user.service.UserProvisioner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
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
public class StudentService {

    private static final String ENTITY = "StudentProfile";

    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final UserProvisioner provisioner;
    private final AccountRules accountRules;
    private final AuditEventPublisher audit;
    private final EntityManager entityManager;

    public StudentService(StudentProfileRepository students, StudentEnrollmentRepository enrollments,
            UserProvisioner provisioner, AccountRules accountRules, AuditEventPublisher audit,
            EntityManager entityManager) {
        this.students = students;
        this.enrollments = enrollments;
        this.provisioner = provisioner;
        this.accountRules = accountRules;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.Response> search(UUID schoolId, StudentStatus status, String search,
            Pageable pageable) {
        Specification<StudentProfile> spec = Specification
                .<StudentProfile>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("status", status))
                .and(matchesText(search));
        Page<StudentProfile> page = students.findAll(spec, pageable);
        Map<UUID, StudentDtos.EnrollmentRef> current = currentPlacements(page.getContent());
        return PageResponse.from(page, profile -> StudentDtos.Response.from(profile, current.get(profile.getId())));
    }

    @Transactional(readOnly = true)
    public StudentDtos.Response getResponse(UUID schoolId, UUID id) {
        StudentProfile profile = get(schoolId, id);
        return StudentDtos.Response.from(profile, currentPlacements(List.of(profile)).get(id));
    }

    @Transactional(readOnly = true)
    public StudentProfile get(UUID schoolId, UUID id) {
        return students.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Student"));
    }

    @Transactional
    public StudentDtos.Response create(AuthenticatedUser actor, StudentDtos.Request request) {
        UUID schoolId = actor.schoolId();
        accountRules.requireText(request.admissionNumber(), "admissionNumber", "Admission number is required");
        accountRules.requireStrongPassword(request.password());
        validateDates(request);
        String admissionNumber = request.admissionNumber().trim();
        if (students.existsBySchoolIdAndAdmissionNumberIgnoreCase(schoolId, admissionNumber)) {
            throw new ConflictException("A student with the admission number \"" + admissionNumber
                    + "\" already exists.");
        }
        accountRules.requireAvailableLoginId(schoolId, admissionNumber, "admission number");
        String email = request.email().trim();
        accountRules.requireAvailableEmail(schoolId, email, null);

        User user = provisioner.createActiveUser(schoolId, admissionNumber, email, request.password(),
                request.firstName().trim(), request.lastName().trim(), RoleName.ROLE_STUDENT);
        user.updateContact(user.getFirstName(), user.getLastName(), user.getEmail(), blankToNull(request.phoneNumber()));

        StudentProfile profile = new StudentProfile(schoolId, user, admissionNumber);
        applyProfile(profile, request, StudentStatus.ACTIVE);
        students.saveAndFlush(profile);
        audit.record(actor, "STUDENT_CREATED", ENTITY, profile.getId(), "Created student " + admissionNumber);
        return StudentDtos.Response.from(profile, null);
    }

    @Transactional
    public StudentDtos.Response update(AuthenticatedUser actor, UUID id, StudentDtos.Request request) {
        StudentProfile profile = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), profile.getVersion());
        // Contact details live on the user row, so the profile version must be bumped even if only they change.
        // The pessimistic variant bumps it immediately, so the response carries the new version.
        entityManager.lock(profile, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        validateDates(request);
        User user = profile.getUser();
        String email = request.email().trim();
        accountRules.requireAvailableEmail(actor.schoolId(), email, user.getId());

        StudentStatus newStatus = request.status() == null ? profile.getStatus() : request.status();
        user.updateContact(request.firstName().trim(), request.lastName().trim(), email,
                blankToNull(request.phoneNumber()));
        StudentStatus previous = profile.getStatus();
        applyProfile(profile, request, newStatus);
        endEnrollmentsIfLeaving(profile, previous, newStatus);
        students.saveAndFlush(profile);
        audit.record(actor, "STUDENT_UPDATED", ENTITY, id, "Updated student " + profile.getAdmissionNumber()
                + (previous != newStatus ? " (status " + previous + " to " + newStatus + ")" : ""));
        return StudentDtos.Response.from(profile, currentPlacements(List.of(profile)).get(id));
    }

    /** Withdrawing or graduating a student ends the active enrollments; the history rows are kept. */
    private void endEnrollmentsIfLeaving(StudentProfile profile, StudentStatus previous, StudentStatus next) {
        if (previous == next) {
            return;
        }
        EnrollmentStatus outcome = switch (next) {
            case WITHDRAWN -> EnrollmentStatus.WITHDRAWN;
            case GRADUATED -> EnrollmentStatus.COMPLETED;
            default -> null;
        };
        if (outcome != null) {
            enrollments.findAllActiveByStudentId(profile.getId()).forEach(enrollment -> enrollment.end(outcome));
        }
    }

    private void applyProfile(StudentProfile profile, StudentDtos.Request r, StudentStatus status) {
        profile.update(r.dateOfBirth(), r.gender(), r.admissionDate(), blankToNull(r.bloodGroup()),
                r.addressLine1().trim(), blankToNull(r.addressLine2()), r.city().trim(), blankToNull(r.state()),
                blankToNull(r.postalCode()), r.emergencyContactName().trim(), r.emergencyContactPhone().trim(),
                status);
    }

    private static void validateDates(StudentDtos.Request request) {
        if (request.admissionDate().isBefore(request.dateOfBirth())) {
            throw new BusinessRuleException("The admission date cannot be before the date of birth.");
        }
    }

    private Map<UUID, StudentDtos.EnrollmentRef> currentPlacements(Collection<StudentProfile> profiles) {
        Map<UUID, StudentDtos.EnrollmentRef> result = new HashMap<>();
        if (profiles.isEmpty()) {
            return result;
        }
        List<UUID> ids = profiles.stream().map(StudentProfile::getId).toList();
        // Newest academic year first, so the first row per student is the current placement.
        for (StudentEnrollment e : enrollments.findActiveByStudentIds(ids)) {
            result.putIfAbsent(e.getStudent().getId(), new StudentDtos.EnrollmentRef(e.getId(),
                    e.getAcademicYear().getName(), e.getGradeLevel().getName(), e.getSection().getName(),
                    e.getRollNumber()));
        }
        return result;
    }

    private static Specification<StudentProfile> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = SearchSpecs.likePattern(search);
        return (root, query, cb) -> {
            Join<Object, Object> user = root.join("user");
            Expression<String> first = cb.lower(user.get("firstName"));
            Expression<String> last = cb.lower(user.get("lastName"));
            Expression<String> fullName = cb.concat(cb.concat(first, " "), last);
            return cb.or(
                    cb.like(cb.lower(root.get("admissionNumber")), pattern, '\\'),
                    cb.like(first, pattern, '\\'),
                    cb.like(last, pattern, '\\'),
                    cb.like(fullName, pattern, '\\'),
                    cb.like(cb.lower(user.get("email")), pattern, '\\'));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
