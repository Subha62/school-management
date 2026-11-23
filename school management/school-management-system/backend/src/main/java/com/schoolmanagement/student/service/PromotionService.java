package com.schoolmanagement.student.service;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.dto.EnrollmentDtos;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.PromotionBatch;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.student.repository.PromotionBatchRepository;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves students from one academic year to the next. The run is one transaction: each student either moves
 * completely (old enrollment promoted, new one created) or is reported as failed with a reason and left untouched.
 */
@Service
public class PromotionService {

    private static final String ENTITY = "PromotionBatch";

    private final AcademicYearRepository years;
    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final PromotionBatchRepository batches;
    private final EnrollmentService enrollmentService;
    private final UserRepository users;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public PromotionService(AcademicYearRepository years, StudentProfileRepository students,
            StudentEnrollmentRepository enrollments, PromotionBatchRepository batches,
            EnrollmentService enrollmentService, UserRepository users, AuditEventPublisher audit, Clock clock) {
        this.years = years;
        this.students = students;
        this.enrollments = enrollments;
        this.batches = batches;
        this.enrollmentService = enrollmentService;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public EnrollmentDtos.PromotionSummary promote(AuthenticatedUser actor, EnrollmentDtos.PromotionRequest request) {
        UUID schoolId = actor.schoolId();
        AcademicYear source = year(schoolId, request.sourceAcademicYearId());
        AcademicYear target = year(schoolId, request.targetAcademicYearId());
        validateYears(source, target);
        List<EnrollmentDtos.PromotionItem> items = request.items();
        if (items.stream().map(EnrollmentDtos.PromotionItem::studentId).distinct().count() != items.size()) {
            throw new BusinessRuleException("A student can only appear once in a promotion.");
        }

        List<UUID> studentIds = items.stream().map(EnrollmentDtos.PromotionItem::studentId).toList();
        Map<UUID, StudentProfile> profiles = students.findAllByIdInAndSchoolId(studentIds, schoolId).stream()
                .collect(Collectors.toMap(StudentProfile::getId, s -> s));
        Map<UUID, StudentEnrollment> sourceEnrollments = enrollments.findActiveInYear(studentIds, source.getId())
                .stream().collect(Collectors.toMap(e -> e.getStudent().getId(), e -> e));
        Set<UUID> alreadyInTarget = new HashSet<>();
        enrollments.findActiveInYear(studentIds, target.getId()).forEach(e -> alreadyInTarget.add(e.getStudent().getId()));

        // Locked in id order up front, so capacity is checked against a stable count for the whole run.
        List<UUID> sectionIds = items.stream().map(EnrollmentDtos.PromotionItem::targetSectionId).distinct().toList();
        Map<UUID, Section> sections = new HashMap<>();
        enrollmentService.lockSections(schoolId, sectionIds).forEach(s -> sections.put(s.getId(), s));

        PromotionBatch batch = batches.saveAndFlush(
                new PromotionBatch(schoolId, source.getId(), target.getId(), actor.id(), clock.instant()));
        LocalDate today = LocalDate.now(clock);
        List<EnrollmentDtos.PromotionResult> results = new ArrayList<>();
        int promoted = 0;
        for (EnrollmentDtos.PromotionItem item : items) {
            StudentProfile student = profiles.get(item.studentId());
            String failure = student == null ? "Student not found."
                    : check(student, sourceEnrollments.get(item.studentId()), alreadyInTarget, sections.get(item.targetSectionId()),
                            source, target);
            StudentEnrollment created = null;
            if (failure == null) {
                StudentEnrollment old = sourceEnrollments.get(item.studentId());
                try {
                    created = enrollmentService.place(actor, student, sections.get(item.targetSectionId()),
                            item.rollNumber(), today, old.getId(), batch.getId());
                    old.end(EnrollmentStatus.PROMOTED);
                    enrollments.saveAndFlush(old);
                    alreadyInTarget.add(student.getId());
                } catch (BusinessRuleException | ConflictException e) {
                    failure = e.getMessage();
                }
            }
            results.add(result(item, student, created, failure));
            if (created != null) {
                promoted++;
            }
        }
        batch.recordOutcome(promoted, items.size() - promoted);
        batches.saveAndFlush(batch);
        audit.record(actor, "STUDENTS_PROMOTED", ENTITY, batch.getId(), "Promoted " + promoted + " of " + items.size()
                + " students from " + source.getName() + " to " + target.getName());
        return new EnrollmentDtos.PromotionSummary(batch.getId(), source.getName(), target.getName(),
                actorName(actor), batch.getPerformedAt(), items.size(), promoted, items.size() - promoted, results);
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDtos.BatchResponse> history(UUID schoolId, Pageable pageable) {
        Page<PromotionBatch> page = batches.findAllBySchoolId(schoolId, pageable);
        Map<UUID, String> yearNames = new HashMap<>();
        years.findAllById(page.getContent().stream()
                .flatMap(b -> java.util.stream.Stream.of(b.getSourceAcademicYearId(), b.getTargetAcademicYearId()))
                .collect(Collectors.toSet())).forEach(y -> yearNames.put(y.getId(), y.getName()));
        Map<UUID, String> people = new HashMap<>();
        users.findAllById(page.getContent().stream().map(PromotionBatch::getPerformedBy).collect(Collectors.toSet()))
                .forEach(u -> people.put(u.getId(), (u.getFirstName() + " " + u.getLastName()).trim()));
        return PageResponse.from(page, b -> new EnrollmentDtos.BatchResponse(b.getId(),
                yearNames.getOrDefault(b.getSourceAcademicYearId(), "Unknown"),
                yearNames.getOrDefault(b.getTargetAcademicYearId(), "Unknown"),
                people.getOrDefault(b.getPerformedBy(), "Unknown"), b.getPerformedAt(), b.getTotalCount(),
                b.getPromotedCount(), b.getFailedCount()));
    }

    private static String check(StudentProfile student, StudentEnrollment current, Set<UUID> alreadyInTarget,
            Section section, AcademicYear source, AcademicYear target) {
        if (student.getStatus() != StudentStatus.ACTIVE) {
            return "The student is not active.";
        }
        if (current == null) {
            return "The student has no active enrollment in " + source.getName() + ".";
        }
        if (alreadyInTarget.contains(student.getId())) {
            return "The student is already enrolled in " + target.getName() + ".";
        }
        if (section == null) {
            return "The target section was not found.";
        }
        if (!section.getAcademicYear().getId().equals(target.getId())) {
            return "The target section does not belong to " + target.getName() + ".";
        }
        if (!section.isActive()) {
            return "The target section is inactive.";
        }
        return null;
    }

    private static EnrollmentDtos.PromotionResult result(EnrollmentDtos.PromotionItem item, StudentProfile student,
            StudentEnrollment created, String failure) {
        String admission = student == null ? null : student.getAdmissionNumber();
        String name = student == null ? null
                : (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim();
        return created != null
                ? new EnrollmentDtos.PromotionResult(item.studentId(), admission, name,
                        EnrollmentDtos.Outcome.PROMOTED, null, created.getId(), created.getRollNumber())
                : new EnrollmentDtos.PromotionResult(item.studentId(), admission, name,
                        EnrollmentDtos.Outcome.FAILED, failure, null, null);
    }

    private AcademicYear year(UUID schoolId, UUID id) {
        return years.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Academic year"));
    }

    private static void validateYears(AcademicYear source, AcademicYear target) {
        if (source.getId().equals(target.getId())) {
            throw new BusinessRuleException("The source and target academic years must be different.");
        }
        if (!target.getStartDate().isAfter(source.getStartDate())) {
            throw new BusinessRuleException("The target academic year must start after the source academic year.");
        }
        if (target.isClosed()) {
            throw new BusinessRuleException("The target academic year is closed.");
        }
    }

    private static String actorName(AuthenticatedUser actor) {
        return (actor.firstName() + " " + actor.lastName()).trim();
    }
}
