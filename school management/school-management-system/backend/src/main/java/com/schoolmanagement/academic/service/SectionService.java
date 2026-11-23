package com.schoolmanagement.academic.service;

import com.schoolmanagement.academic.dto.SectionDtos;
import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.GradeLevelRepository;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.announcement.repository.AnnouncementRepository;
import com.schoolmanagement.assignment.repository.AssignmentRepository;
import com.schoolmanagement.attendance.repository.AttendanceSessionRepository;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
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

@Service
public class SectionService {

    private static final String ENTITY = "Section";

    private final SectionRepository sections;
    private final AcademicYearRepository years;
    private final GradeLevelRepository grades;
    private final SectionSubjectRepository sectionSubjects;
    private final StudentEnrollmentRepository enrollments;
    private final TimetableEntryRepository timetable;
    private final AttendanceSessionRepository attendance;
    private final AssignmentRepository homework;
    private final AnnouncementRepository notices;
    private final TeacherDirectory teachers;
    private final AuditEventPublisher audit;

    public SectionService(SectionRepository sections, AcademicYearRepository years, GradeLevelRepository grades,
            SectionSubjectRepository sectionSubjects, StudentEnrollmentRepository enrollments,
            TimetableEntryRepository timetable, AttendanceSessionRepository attendance,
            AssignmentRepository homework, AnnouncementRepository notices, TeacherDirectory teachers,
            AuditEventPublisher audit) {
        this.homework = homework;
        this.notices = notices;
        this.sections = sections;
        this.years = years;
        this.grades = grades;
        this.sectionSubjects = sectionSubjects;
        this.enrollments = enrollments;
        this.timetable = timetable;
        this.attendance = attendance;
        this.teachers = teachers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<SectionDtos.Response> search(UUID schoolId, UUID academicYearId, UUID gradeLevelId,
            Boolean active, String search, Pageable pageable) {
        Specification<Section> spec = Specification.<Section>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("active", active))
                .and(SearchSpecs.containsIgnoreCase("name", search));
        if (academicYearId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), academicYearId));
        }
        if (gradeLevelId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("gradeLevel").get("id"), gradeLevelId));
        }
        Page<Section> page = sections.findAll(spec, pageable);
        Map<UUID, String> teacherNames = teachers.displayNames(page.getContent().stream()
                .map(Section::getClassTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResponse.from(page, section -> toResponse(section, teacherNames));
    }

    @Transactional(readOnly = true)
    public SectionDtos.Response getResponse(UUID schoolId, UUID id) {
        Section section = get(schoolId, id);
        return toResponse(section, teachers.displayNames(
                section.getClassTeacherId() == null ? Set.of() : Set.of(section.getClassTeacherId())));
    }

    @Transactional(readOnly = true)
    public Section get(UUID schoolId, UUID id) {
        return sections.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Section"));
    }

    @Transactional
    public SectionDtos.Response create(AuthenticatedUser actor, SectionDtos.Request request) {
        if (request.academicYearId() == null || request.gradeLevelId() == null) {
            throw new BusinessRuleException("Academic year and grade are required.");
        }
        AcademicYear year = years.findByIdAndSchoolId(request.academicYearId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic year"));
        GradeLevel grade = grades.findByIdAndSchoolId(request.gradeLevelId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Grade"));
        if (year.isClosed()) {
            throw new BusinessRuleException("Sections cannot be added to a closed academic year.");
        }
        if (!grade.isActive()) {
            throw new BusinessRuleException("The selected grade is inactive.");
        }
        String name = request.name().trim();
        if (sections.existsByAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(year.getId(), grade.getId(), name)) {
            throw new ConflictException("Section " + name + " already exists for " + grade.getName() + " in "
                    + year.getName() + ".");
        }
        requireTeacherIfPresent(actor.schoolId(), request.classTeacherId());

        Section section = sections.saveAndFlush(new Section(actor.schoolId(), year, grade, name, request.capacity(),
                request.classTeacherId(), request.active()));
        audit.record(actor, "SECTION_CREATED", ENTITY, section.getId(),
                "Created section " + grade.getName() + " " + name + " in " + year.getName());
        return toResponse(section, teachers.displayNames(idSet(section.getClassTeacherId())));
    }

    @Transactional
    public SectionDtos.Response update(AuthenticatedUser actor, UUID id, SectionDtos.Request request) {
        Section section = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), section.getVersion());
        if (section.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("Sections of a closed academic year cannot be changed.");
        }
        String name = request.name().trim();
        if (sections.existsByAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot(
                section.getAcademicYear().getId(), section.getGradeLevel().getId(), name, id)) {
            throw new ConflictException("Section " + name + " already exists for " + section.getGradeLevel().getName()
                    + " in " + section.getAcademicYear().getName() + ".");
        }
        requireTeacherIfPresent(actor.schoolId(), request.classTeacherId());
        long enrolled = enrollments.countBySectionIdAndStatus(id, EnrollmentStatus.ACTIVE);
        if (request.capacity() < enrolled) {
            throw new BusinessRuleException("The capacity cannot be lower than the " + enrolled
                    + " students currently enrolled in this section.");
        }

        section.update(name, request.capacity(), request.classTeacherId(), request.active());
        sections.saveAndFlush(section);
        audit.record(actor, "SECTION_UPDATED", ENTITY, id, "Updated section " + section.getGradeLevel().getName()
                + " " + name + (request.classTeacherId() == null ? "" : " (class teacher set)"));
        return toResponse(section, teachers.displayNames(idSet(section.getClassTeacherId())));
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        Section section = get(actor.schoolId(), id);
        if (section.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("Sections of a closed academic year cannot be deleted.");
        }
        if (sectionSubjects.existsBySectionId(id)) {
            throw new ConflictException("This section has subject assignments. Remove them first.");
        }
        if (enrollments.existsBySectionId(id)) {
            throw new ConflictException("Students have been enrolled in this section, so it cannot be deleted. "
                    + "Mark it inactive instead.");
        }
        if (timetable.existsBySectionId(id) || attendance.existsBySectionId(id) || homework.existsBySectionId(id)
                || notices.existsBySectionId(id)) {
            throw new ConflictException("This section has a timetable, attendance, assignments or announcements, so "
                    + "it cannot be deleted. Mark it inactive instead.");
        }
        sections.delete(section);
        audit.record(actor, "SECTION_DELETED", ENTITY, id, "Deleted section " + section.getGradeLevel().getName()
                + " " + section.getName());
    }

    private void requireTeacherIfPresent(UUID schoolId, UUID teacherId) {
        if (teacherId != null) {
            teachers.requireActiveTeacher(schoolId, teacherId);
        }
    }

    private static Set<UUID> idSet(UUID id) {
        return id == null ? Set.of() : Set.of(id);
    }

    private static SectionDtos.Response toResponse(Section section, Map<UUID, String> teacherNames) {
        UUID teacherId = section.getClassTeacherId();
        SectionDtos.NamedRef teacher = teacherId == null ? null
                : new SectionDtos.NamedRef(teacherId, teacherNames.getOrDefault(teacherId, "Unknown teacher"));
        return new SectionDtos.Response(section.getId(),
                new SectionDtos.NamedRef(section.getAcademicYear().getId(), section.getAcademicYear().getName()),
                new SectionDtos.NamedRef(section.getGradeLevel().getId(), section.getGradeLevel().getName()),
                section.getName(), section.getCapacity(), teacher, section.isActive(), section.getVersion());
    }
}
