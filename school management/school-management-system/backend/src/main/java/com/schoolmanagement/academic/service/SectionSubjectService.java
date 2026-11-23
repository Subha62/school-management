package com.schoolmanagement.academic.service;

import com.schoolmanagement.academic.dto.SectionDtos;
import com.schoolmanagement.academic.dto.SectionSubjectDtos;
import com.schoolmanagement.academic.dto.SubjectDtos;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.assignment.repository.AssignmentRepository;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import com.schoolmanagement.timetable.repository.TimetableEntryRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assigns subjects (and their teachers) to sections for an academic year. */
@Service
public class SectionSubjectService {

    private static final String ENTITY = "SectionSubject";

    private final SectionSubjectRepository assignments;
    private final SectionRepository sections;
    private final SubjectRepository subjects;
    private final TimetableEntryRepository timetable;
    private final AssignmentRepository homework;
    private final TeacherDirectory teachers;
    private final AuditEventPublisher audit;

    public SectionSubjectService(SectionSubjectRepository assignments, SectionRepository sections,
            SubjectRepository subjects, TimetableEntryRepository timetable, AssignmentRepository homework,
            TeacherDirectory teachers, AuditEventPublisher audit) {
        this.homework = homework;
        this.assignments = assignments;
        this.sections = sections;
        this.subjects = subjects;
        this.timetable = timetable;
        this.teachers = teachers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<SectionSubjectDtos.Response> search(UUID schoolId, UUID academicYearId, UUID sectionId,
            UUID teacherId, UUID subjectId, Pageable pageable) {
        Specification<SectionSubject> spec = Specification
                .<SectionSubject>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("teacherId", teacherId));
        if (academicYearId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), academicYearId));
        }
        if (sectionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("section").get("id"), sectionId));
        }
        if (subjectId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("subject").get("id"), subjectId));
        }
        Page<SectionSubject> page = assignments.findAll(spec, pageable);
        Map<UUID, String> names = teachers.displayNames(page.getContent().stream()
                .map(SectionSubject::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResponse.from(page, assignment -> toResponse(assignment, names));
    }

    @Transactional
    public SectionSubjectDtos.Response create(AuthenticatedUser actor, SectionSubjectDtos.Request request) {
        if (request.sectionId() == null || request.subjectId() == null) {
            throw new BusinessRuleException("Section and subject are required.");
        }
        Section section = sections.findByIdAndSchoolId(request.sectionId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        Subject subject = subjects.findByIdAndSchoolId(request.subjectId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject"));
        if (section.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("Subjects cannot be assigned in a closed academic year.");
        }
        if (!section.isActive()) {
            throw new BusinessRuleException("The selected section is inactive.");
        }
        if (!subject.isActive()) {
            throw new BusinessRuleException("The selected subject is inactive.");
        }
        if (assignments.existsBySectionIdAndSubjectId(section.getId(), subject.getId())) {
            throw new ConflictException(subject.getName() + " is already assigned to this section.");
        }
        requireTeacherIfPresent(actor.schoolId(), request.teacherId());

        SectionSubject assignment = assignments.saveAndFlush(
                new SectionSubject(section, subject, request.teacherId(), request.weeklyPeriods()));
        audit.record(actor, "SECTION_SUBJECT_ASSIGNED", ENTITY, assignment.getId(), "Assigned " + subject.getCode()
                + " to " + section.getGradeLevel().getName() + " " + section.getName());
        return toResponse(assignment, teachers.displayNames(idSet(assignment.getTeacherId())));
    }

    @Transactional
    public SectionSubjectDtos.Response update(AuthenticatedUser actor, UUID id,
            SectionSubjectDtos.Request request) {
        SectionSubject assignment = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), assignment.getVersion());
        if (assignment.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("Assignments of a closed academic year cannot be changed.");
        }
        requireTeacherIfPresent(actor.schoolId(), request.teacherId());

        boolean active = request.active() == null ? assignment.isActive() : request.active();
        assignment.update(request.teacherId(), request.weeklyPeriods(), active);
        assignments.saveAndFlush(assignment);
        audit.record(actor, "SECTION_SUBJECT_UPDATED", ENTITY, id, "Updated " + assignment.getSubject().getCode()
                + " in " + assignment.getSection().getGradeLevel().getName() + " "
                + assignment.getSection().getName());
        return toResponse(assignment, teachers.displayNames(idSet(assignment.getTeacherId())));
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        SectionSubject assignment = get(actor.schoolId(), id);
        if (assignment.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("Assignments of a closed academic year cannot be removed.");
        }
        if (timetable.existsBySectionIdAndSubjectId(assignment.getSection().getId(),
                assignment.getSubject().getId())) {
            throw new ConflictException("This subject is on the section's timetable. Remove those slots first, "
                    + "or mark the assignment inactive.");
        }
        if (homework.existsBySectionIdAndSubjectId(assignment.getSection().getId(),
                assignment.getSubject().getId())) {
            throw new ConflictException("This subject has assignments set for the class. Archive them and mark "
                    + "the assignment inactive instead of removing it.");
        }
        assignments.delete(assignment);
        audit.record(actor, "SECTION_SUBJECT_REMOVED", ENTITY, id, "Removed " + assignment.getSubject().getCode()
                + " from " + assignment.getSection().getGradeLevel().getName() + " "
                + assignment.getSection().getName());
    }

    private SectionSubject get(UUID schoolId, UUID id) {
        return assignments.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject assignment"));
    }

    private void requireTeacherIfPresent(UUID schoolId, UUID teacherId) {
        if (teacherId != null) {
            teachers.requireActiveTeacher(schoolId, teacherId);
        }
    }

    private static Set<UUID> idSet(UUID id) {
        return id == null ? Set.of() : Set.of(id);
    }

    private static SectionSubjectDtos.Response toResponse(SectionSubject assignment, Map<UUID, String> names) {
        UUID teacherId = assignment.getTeacherId();
        SectionDtos.NamedRef teacher = teacherId == null ? null
                : new SectionDtos.NamedRef(teacherId, names.getOrDefault(teacherId, "Unknown teacher"));
        Section section = assignment.getSection();
        return new SectionSubjectDtos.Response(assignment.getId(), assignment.getAcademicYear().getId(),
                assignment.getAcademicYear().getName(), section.getId(), section.getName(),
                section.getGradeLevel().getName(), SubjectDtos.Ref.from(assignment.getSubject()), teacher,
                assignment.getWeeklyPeriods(), assignment.isActive(), assignment.getVersion());
    }
}
