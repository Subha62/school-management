package com.schoolmanagement.student.service;

import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.dto.EnrollmentDtos;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

    private static final String ENTITY = "StudentEnrollment";

    private final StudentEnrollmentRepository enrollments;
    private final SectionRepository sections;
    private final StudentService students;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public EnrollmentService(StudentEnrollmentRepository enrollments, SectionRepository sections,
            StudentService students, AuditEventPublisher audit, Clock clock) {
        this.enrollments = enrollments;
        this.sections = sections;
        this.students = students;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentDtos.Response> search(UUID schoolId, UUID academicYearId, UUID gradeLevelId,
            UUID sectionId, EnrollmentStatus status, String search, Pageable pageable) {
        Specification<StudentEnrollment> spec = Specification
                .<StudentEnrollment>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("status", status))
                .and(refersTo("academicYear", academicYearId))
                .and(refersTo("gradeLevel", gradeLevelId))
                .and(refersTo("section", sectionId))
                .and(matchesStudent(search));
        return PageResponse.from(enrollments.findAll(spec, pageable), EnrollmentDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDtos.Response> historyOf(UUID schoolId, UUID studentId) {
        students.get(schoolId, studentId);
        return enrollments.findAllByStudentIdOrderByEnrollmentDateDescCreatedAtDesc(studentId).stream()
                .map(EnrollmentDtos.Response::from).toList();
    }

    @Transactional
    public EnrollmentDtos.Response enroll(AuthenticatedUser actor, EnrollmentDtos.EnrollRequest request) {
        StudentProfile student = students.get(actor.schoolId(), request.studentId());
        requireActiveStudent(student);
        Section section = lockSection(actor.schoolId(), request.sectionId());
        requireOpenSection(section);
        if (enrollments.existsByStudentIdAndAcademicYearIdAndStatus(student.getId(),
                section.getAcademicYear().getId(), EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("The student is already enrolled in " + section.getAcademicYear().getName()
                    + ". Transfer the student to change section.");
        }
        LocalDate date = request.enrollmentDate() == null ? LocalDate.now(clock) : request.enrollmentDate();
        StudentEnrollment created = place(actor, student, section, request.rollNumber(), date, null, null);
        audit.record(actor, "STUDENT_ENROLLED", ENTITY, created.getId(), "Enrolled " + student.getAdmissionNumber()
                + " in " + describe(section));
        return EnrollmentDtos.Response.from(created);
    }

    @Transactional
    public EnrollmentDtos.Response transfer(AuthenticatedUser actor, UUID enrollmentId,
            EnrollmentDtos.TransferRequest request) {
        StudentEnrollment current = find(actor.schoolId(), enrollmentId);
        requireActive(current);
        Section target = lockSection(actor.schoolId(), request.targetSectionId());
        requireOpenSection(target);
        if (!target.getAcademicYear().getId().equals(current.getAcademicYear().getId())) {
            throw new BusinessRuleException(
                    "A transfer stays within one academic year. Use promotion to move to another year.");
        }
        if (target.getId().equals(current.getSection().getId())) {
            throw new BusinessRuleException("The student is already in this section.");
        }
        StudentProfile student = current.getStudent();
        current.end(EnrollmentStatus.TRANSFERRED);
        // The old row must be saved first: only one active enrollment per student and year is allowed.
        enrollments.saveAndFlush(current);
        StudentEnrollment created = place(actor, student, target, request.rollNumber(), LocalDate.now(clock),
                current.getId(), null);
        audit.record(actor, "STUDENT_TRANSFERRED", ENTITY, created.getId(), "Transferred "
                + student.getAdmissionNumber() + " from " + describe(current.getSection()) + " to " + describe(target));
        return EnrollmentDtos.Response.from(created);
    }

    @Transactional
    public EnrollmentDtos.Response withdraw(AuthenticatedUser actor, UUID enrollmentId) {
        return finish(actor, enrollmentId, EnrollmentStatus.WITHDRAWN, "STUDENT_WITHDRAWN", "Withdrew");
    }

    @Transactional
    public EnrollmentDtos.Response complete(AuthenticatedUser actor, UUID enrollmentId) {
        return finish(actor, enrollmentId, EnrollmentStatus.COMPLETED, "ENROLLMENT_COMPLETED", "Completed");
    }

    private EnrollmentDtos.Response finish(AuthenticatedUser actor, UUID enrollmentId, EnrollmentStatus outcome,
            String action, String verb) {
        StudentEnrollment enrollment = find(actor.schoolId(), enrollmentId);
        requireActive(enrollment);
        enrollment.end(outcome);
        enrollments.saveAndFlush(enrollment);
        audit.record(actor, action, ENTITY, enrollmentId, verb + " enrollment of "
                + enrollment.getStudent().getAdmissionNumber() + " in " + describe(enrollment.getSection()));
        return EnrollmentDtos.Response.from(enrollment);
    }

    /**
     * Creates the active enrollment after checking capacity and the roll number. The caller must already hold the
     * section lock, so the count cannot change underneath this check.
     */
    StudentEnrollment place(AuthenticatedUser actor, StudentProfile student, Section section, Integer rollNumber,
            LocalDate date, UUID sourceEnrollmentId, UUID promotionBatchId) {
        requireSeat(section);
        Integer roll = rollNumber;
        if (roll == null) {
            roll = enrollments.maxActiveRollNumber(section.getId()) + 1;
        } else if (enrollments.existsBySectionIdAndRollNumberAndStatus(section.getId(), roll,
                EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("Roll number " + roll + " is already used in " + describe(section) + ".");
        }
        return enrollments.saveAndFlush(new StudentEnrollment(student, section, roll, date, actor.id(),
                sourceEnrollmentId, promotionBatchId));
    }

    void requireSeat(Section section) {
        long taken = enrollments.countBySectionIdAndStatus(section.getId(), EnrollmentStatus.ACTIVE);
        if (taken >= section.getCapacity()) {
            throw new BusinessRuleException(describe(section) + " is full (" + section.getCapacity() + " seats).");
        }
    }

    static void requireActiveStudent(StudentProfile student) {
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessRuleException("Only active students can be enrolled.");
        }
    }

    static void requireOpenSection(Section section) {
        if (!section.isActive()) {
            throw new BusinessRuleException(describe(section) + " is inactive.");
        }
        if (section.getAcademicYear().getStatus() == AcademicYearStatus.CLOSED) {
            throw new BusinessRuleException("The academic year " + section.getAcademicYear().getName()
                    + " is closed.");
        }
    }

    private static void requireActive(StudentEnrollment enrollment) {
        if (!enrollment.isActive()) {
            throw new BusinessRuleException("Only an active enrollment can be changed.");
        }
    }

    Section lockSection(UUID schoolId, UUID sectionId) {
        return lockSections(schoolId, List.of(sectionId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
    }

    /** Sections that do not exist in this school are simply absent from the result. */
    List<Section> lockSections(UUID schoolId, List<UUID> sectionIds) {
        return sections.lockAllByIdIn(sectionIds, schoolId);
    }

    private StudentEnrollment find(UUID schoolId, UUID id) {
        return enrollments.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment"));
    }

    static String describe(Section section) {
        return section.getGradeLevel().getName() + "-" + section.getName() + " ("
                + section.getAcademicYear().getName() + ")";
    }

    private static Specification<StudentEnrollment> refersTo(String attribute, UUID id) {
        if (id == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get(attribute).get("id"), id);
    }

    private static Specification<StudentEnrollment> matchesStudent(String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = SearchSpecs.likePattern(search);
        return (root, query, cb) -> {
            Join<Object, Object> student = root.join("student");
            Join<Object, Object> user = student.join("user");
            Expression<String> first = cb.lower(user.get("firstName"));
            Expression<String> last = cb.lower(user.get("lastName"));
            return cb.or(
                    cb.like(cb.lower(student.get("admissionNumber")), pattern, '\\'),
                    cb.like(first, pattern, '\\'),
                    cb.like(last, pattern, '\\'),
                    cb.like(cb.concat(cb.concat(first, " "), last), pattern, '\\'));
        };
    }
}
