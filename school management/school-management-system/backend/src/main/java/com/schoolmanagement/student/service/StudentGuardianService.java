package com.schoolmanagement.student.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.guardian.entity.Guardian;
import com.schoolmanagement.guardian.service.GuardianService;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.dto.StudentGuardianDtos;
import com.schoolmanagement.student.entity.StudentGuardian;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentGuardianRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Links guardians to students. A student has at most one primary guardian. */
@Service
public class StudentGuardianService {

    private static final String ENTITY = "StudentGuardian";

    private final StudentGuardianRepository links;
    private final StudentService students;
    private final GuardianService guardians;
    private final AuditEventPublisher audit;

    public StudentGuardianService(StudentGuardianRepository links, StudentService students,
            GuardianService guardians, AuditEventPublisher audit) {
        this.links = links;
        this.students = students;
        this.guardians = guardians;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StudentGuardianDtos.Response> list(UUID schoolId, UUID studentId) {
        students.get(schoolId, studentId);
        return links.findAllByStudentIdOrderByCreatedAtAsc(studentId).stream()
                .map(StudentGuardianDtos.Response::from).toList();
    }

    @Transactional
    public StudentGuardianDtos.Response link(AuthenticatedUser actor, UUID studentId,
            StudentGuardianDtos.Request request) {
        if (request.guardianId() == null) {
            throw new FieldValidationException("guardianId", "Guardian is required");
        }
        StudentProfile student = students.get(actor.schoolId(), studentId);
        Guardian guardian = guardians.get(actor.schoolId(), request.guardianId());
        if (links.existsByStudentIdAndGuardianId(studentId, guardian.getId())) {
            throw new ConflictException("This guardian is already linked to the student.");
        }
        // The first guardian of a student is always the primary one.
        boolean primary = request.primaryGuardian() || !links.existsByStudentId(studentId);
        if (primary) {
            demoteCurrentPrimary(studentId, null);
        }
        StudentGuardian link = links.saveAndFlush(new StudentGuardian(student, guardian, primary,
                request.receivesNotifications(), request.authorizedPickup()));
        audit.record(actor, "GUARDIAN_LINKED", ENTITY, link.getId(), "Linked guardian " + guardian.getId()
                + " to student " + student.getAdmissionNumber());
        return StudentGuardianDtos.Response.from(link);
    }

    @Transactional
    public StudentGuardianDtos.Response update(AuthenticatedUser actor, UUID studentId, UUID linkId,
            StudentGuardianDtos.Request request) {
        students.get(actor.schoolId(), studentId);
        StudentGuardian link = find(studentId, linkId);
        OptimisticVersion.check(request.version(), link.getVersion());
        boolean primary = request.primaryGuardian();
        if (!primary && link.isPrimaryGuardian()) {
            // Keep exactly one primary: promote another guardian first instead of leaving none.
            throw new ConflictException("Choose another guardian as primary instead of clearing this one.");
        }
        if (primary && !link.isPrimaryGuardian()) {
            demoteCurrentPrimary(studentId, link.getId());
        }
        link.update(primary, request.receivesNotifications(), request.authorizedPickup());
        links.saveAndFlush(link);
        audit.record(actor, "GUARDIAN_LINK_UPDATED", ENTITY, linkId, "Updated guardian link");
        return StudentGuardianDtos.Response.from(link);
    }

    @Transactional
    public void unlink(AuthenticatedUser actor, UUID studentId, UUID linkId) {
        students.get(actor.schoolId(), studentId);
        StudentGuardian link = find(studentId, linkId);
        boolean wasPrimary = link.isPrimaryGuardian();
        links.delete(link);
        links.flush();
        if (wasPrimary) {
            // Promote the longest-linked remaining guardian so the student still has a primary contact.
            links.findAllByStudentIdOrderByCreatedAtAsc(studentId).stream().findFirst().ifPresent(next -> {
                next.update(true, next.isReceivesNotifications(), next.isAuthorizedPickup());
                links.saveAndFlush(next);
            });
        }
        audit.record(actor, "GUARDIAN_UNLINKED", ENTITY, linkId, "Unlinked guardian from student");
    }

    private StudentGuardian find(UUID studentId, UUID linkId) {
        return links.findByIdAndStudentId(linkId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian link"));
    }

    private void demoteCurrentPrimary(UUID studentId, UUID exceptLinkId) {
        links.findByStudentIdAndPrimaryGuardianTrue(studentId)
                .filter(current -> !current.getId().equals(exceptLinkId))
                .ifPresent(current -> {
                    current.demoteFromPrimary();
                    // Flush before the new primary is saved so the partial unique index is never violated.
                    links.saveAndFlush(current);
                });
    }
}
