package com.schoolmanagement.timetable.service;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import com.schoolmanagement.timetable.dto.TimetableDtos;
import com.schoolmanagement.timetable.entity.TimetableEntry;
import com.schoolmanagement.timetable.entity.TimetableStatus;
import com.schoolmanagement.timetable.repository.TimetableEntryRepository;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Weekly timetable of sections and teachers, with conflict detection. */
@Service
public class TimetableService {

    private static final String ENTITY = "TimetableEntry";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final UUID NO_ENTRY = new UUID(0L, 0L);

    private final TimetableEntryRepository entries;
    private final SectionRepository sections;
    private final SectionSubjectRepository assignments;
    private final SubjectRepository subjects;
    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final UserRepository users;
    private final TeacherDirectory teachers;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public TimetableService(TimetableEntryRepository entries, SectionRepository sections,
            SectionSubjectRepository assignments, SubjectRepository subjects,
            StudentProfileRepository students, StudentEnrollmentRepository enrollments, UserRepository users,
            TeacherDirectory teachers, AuditEventPublisher audit, Clock clock) {
        this.entries = entries;
        this.sections = sections;
        this.assignments = assignments;
        this.subjects = subjects;
        this.students = students;
        this.enrollments = enrollments;
        this.users = users;
        this.teachers = teachers;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- administration --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<TimetableDtos.Response> search(UUID schoolId, UUID academicYearId, UUID sectionId,
            UUID teacherId, DayOfWeek day, TimetableStatus status, Pageable pageable) {
        Specification<TimetableEntry> spec = Specification
                .<TimetableEntry>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("teacherId", teacherId))
                .and(SearchSpecs.equalTo("dayOfWeek", day))
                .and(SearchSpecs.equalTo("status", status));
        if (academicYearId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), academicYearId));
        }
        if (sectionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("section").get("id"), sectionId));
        }
        var page = entries.findAll(spec, pageable);
        Map<UUID, String> names = teachers.displayNames(
                page.getContent().stream().map(TimetableEntry::getTeacherId).collect(Collectors.toSet()));
        return PageResponse.from(page, entry -> TimetableDtos.Response.from(entry, names.get(entry.getTeacherId()),
                null));
    }

    @Transactional(readOnly = true)
    public TimetableDtos.Response getResponse(UUID schoolId, UUID id) {
        TimetableEntry entry = get(schoolId, id);
        return TimetableDtos.Response.from(entry,
                teachers.displayNames(Set.of(entry.getTeacherId())).get(entry.getTeacherId()), null);
    }

    @Transactional
    public TimetableDtos.Response create(AuthenticatedUser actor, TimetableDtos.Request request) {
        if (request.sectionId() == null) {
            throw new FieldValidationException("sectionId", "Section is required");
        }
        Section section = sections.findByIdAndSchoolId(request.sectionId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        if (!section.isActive()) {
            throw new BusinessRuleException("The selected section is inactive.");
        }
        Candidate candidate = validate(actor, section, request, NO_ENTRY);
        TimetableEntry entry = entries.saveAndFlush(new TimetableEntry(section, candidate.subject(),
                candidate.teacherId(), candidate.room(), request.dayOfWeek(), request.periodNumber(),
                request.startTime(), request.endTime(), candidate.from(), candidate.to(), candidate.status()));
        audit.record(actor, "TIMETABLE_ENTRY_CREATED", ENTITY, entry.getId(), describe(entry));
        return toResponse(entry);
    }

    @Transactional
    public TimetableDtos.Response update(AuthenticatedUser actor, UUID id, TimetableDtos.Request request) {
        TimetableEntry entry = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), entry.getVersion());
        Candidate candidate = validate(actor, entry.getSection(), request, entry.getId());
        entry.update(candidate.subject(), candidate.teacherId(), candidate.room(), request.dayOfWeek(),
                request.periodNumber(), request.startTime(), request.endTime(), candidate.from(), candidate.to(),
                candidate.status());
        entries.saveAndFlush(entry);
        audit.record(actor, "TIMETABLE_ENTRY_UPDATED", ENTITY, id, describe(entry));
        return toResponse(entry);
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        TimetableEntry entry = get(actor.schoolId(), id);
        if (entry.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("The timetable of a closed academic year cannot be changed.");
        }
        entries.delete(entry);
        audit.record(actor, "TIMETABLE_ENTRY_DELETED", ENTITY, id, describe(entry));
    }

    /** Everything an entry must satisfy; fails with the first problem found. */
    private Candidate validate(AuthenticatedUser actor, Section section, TimetableDtos.Request request,
            UUID currentEntryId) {
        UUID schoolId = actor.schoolId();
        AcademicYear year = section.getAcademicYear();
        if (year.isClosed()) {
            throw new BusinessRuleException("The timetable of a closed academic year cannot be changed.");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new FieldValidationException("endTime", "End time must be after the start time");
        }
        LocalDate from = request.effectiveFrom() == null ? year.getStartDate() : request.effectiveFrom();
        LocalDate to = request.effectiveTo() == null ? year.getEndDate() : request.effectiveTo();
        if (to.isBefore(from)) {
            throw new FieldValidationException("effectiveTo", "Effective-to date cannot be before the start date");
        }
        if (from.isBefore(year.getStartDate()) || to.isAfter(year.getEndDate())) {
            throw new FieldValidationException("effectiveFrom", "Dates must fall inside the academic year ("
                    + year.getStartDate() + " to " + year.getEndDate() + ")");
        }

        Subject subject = subjects.findByIdAndSchoolId(request.subjectId(), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject"));
        SectionSubject assignment = assignments.findBySectionIdAndSubjectId(section.getId(), subject.getId())
                .filter(SectionSubject::isActive)
                .orElseThrow(() -> new BusinessRuleException(subject.getName()
                        + " is not assigned to this section. Assign it under Teacher assignments first."));
        UUID teacherId = request.teacherId() != null ? request.teacherId() : assignment.getTeacherId();
        if (teacherId == null) {
            throw new BusinessRuleException("Choose a teacher; none is assigned to " + subject.getName()
                    + " in this section.");
        }
        teachers.requireActiveTeacher(schoolId, teacherId);

        String room = request.room() == null || request.room().isBlank() ? null : request.room().trim();
        TimetableStatus status = Boolean.TRUE.equals(request.active()) ? TimetableStatus.ACTIVE
                : TimetableStatus.INACTIVE;

        if (status == TimetableStatus.ACTIVE) {
            // One writer per school at a time, so the check below cannot be raced by a concurrent save.
            entries.lockWrites("timetable:" + schoolId);
            rejectConflicts(schoolId, section, teacherId, room, request, from, to, currentEntryId);
        }
        return new Candidate(subject, teacherId, room, from, to, status);
    }

    private void rejectConflicts(UUID schoolId, Section section, UUID teacherId, String room,
            TimetableDtos.Request request, LocalDate from, LocalDate to, UUID currentEntryId) {
        List<TimetableEntry> clashes = entries.findConflicts(schoolId, request.dayOfWeek(), request.startTime(),
                request.endTime(), from, to, currentEntryId, section.getId(), teacherId,
                room == null ? "" : room.toLowerCase(Locale.ROOT));
        // Report the clash in a fixed order of importance: the class itself, then the teacher, then the room.
        for (TimetableEntry other : clashes) {
            if (other.getSection().getId().equals(section.getId())) {
                throw new ConflictException(label(section) + " already has " + other.getSubject().getName()
                        + " " + slot(other) + ".");
            }
        }
        for (TimetableEntry other : clashes) {
            if (other.getTeacherId().equals(teacherId)) {
                String name = teachers.displayNames(Set.of(teacherId)).getOrDefault(teacherId, "The teacher");
                throw new ConflictException(name + " is already teaching " + label(other.getSection()) + " ("
                        + other.getSubject().getName() + ") " + slot(other) + ".");
            }
        }
        for (TimetableEntry other : clashes) {
            if (room != null && room.equalsIgnoreCase(other.getRoom())) {
                throw new ConflictException("Room " + other.getRoom() + " is already used by "
                        + label(other.getSection()) + " " + slot(other) + ".");
            }
        }
    }

    // ---- weekly views ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TimetableDtos.Week sectionWeek(UUID schoolId, UUID sectionId, LocalDate anyDay) {
        Section section = sections.findByIdAndSchoolId(sectionId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        LocalDate start = weekStart(anyDay);
        return week(start, entries.findActiveForSectionInWeek(section.getId(), start, start.plusDays(6)));
    }

    @Transactional(readOnly = true)
    public TimetableDtos.Week teacherWeek(UUID schoolId, UUID teacherId, LocalDate anyDay) {
        users.findWithRolesByIdAndSchoolId(teacherId, schoolId)
                .filter(user -> user.hasRole(RoleName.ROLE_TEACHER))
                .orElseThrow(() -> new ResourceNotFoundException("Teacher"));
        return ownWeek(schoolId, teacherId, anyDay);
    }

    /** The week of the signed-in teacher; a teacher can only ask for their own. */
    @Transactional(readOnly = true)
    public TimetableDtos.Week ownWeek(UUID schoolId, UUID teacherId, LocalDate anyDay) {
        LocalDate start = weekStart(anyDay);
        return week(start, entries.findActiveForTeacherInWeek(schoolId, teacherId, start, start.plusDays(6)));
    }

    /** The week of the section a student is currently enrolled in (empty when the student is not enrolled). */
    @Transactional(readOnly = true)
    public TimetableDtos.Week studentWeek(UUID schoolId, UUID userId, LocalDate anyDay) {
        LocalDate start = weekStart(anyDay);
        var profile = students.findByUserId(userId).filter(p -> p.getSchoolId().equals(schoolId));
        List<TimetableEntry> found = profile
                .map(p -> enrollments.findAllActiveByStudentId(p.getId()).stream().findFirst()
                        .map(StudentEnrollment::getSection)
                        .map(section -> entries.findActiveForSectionInWeek(section.getId(), start,
                                start.plusDays(6)))
                        .orElse(List.of()))
                .orElse(List.of());
        return week(start, found);
    }

    public LocalDate weekStart(LocalDate anyDay) {
        LocalDate day = anyDay == null ? LocalDate.now(clock) : anyDay;
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private TimetableDtos.Week week(LocalDate start, List<TimetableEntry> found) {
        Map<UUID, String> names = teachers
                .displayNames(found.stream().map(TimetableEntry::getTeacherId).collect(Collectors.toSet()));
        List<TimetableDtos.Response> rows = found.stream()
                .map(entry -> new Dated(entry, start.plusDays(entry.getDayOfWeek().getValue() - 1L)))
                // An entry that starts or ends inside the week only applies on the dates it covers.
                .filter(dated -> !dated.date().isBefore(dated.entry().getEffectiveFrom())
                        && !dated.date().isAfter(dated.entry().getEffectiveTo()))
                .sorted(Comparator.comparing((Dated d) -> d.entry().getDayOfWeek())
                        .thenComparing(d -> d.entry().getStartTime())
                        .thenComparing(d -> d.entry().getPeriodNumber()))
                .map(d -> TimetableDtos.Response.from(d.entry(), names.get(d.entry().getTeacherId()), d.date()))
                .toList();
        return new TimetableDtos.Week(start, start.plusDays(6), rows);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private TimetableEntry get(UUID schoolId, UUID id) {
        return entries.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry"));
    }

    private TimetableDtos.Response toResponse(TimetableEntry entry) {
        return TimetableDtos.Response.from(entry,
                teachers.displayNames(Set.of(entry.getTeacherId())).get(entry.getTeacherId()), null);
    }

    private static String label(Section section) {
        return section.getGradeLevel().getName() + " - " + section.getName();
    }

    private static String slot(TimetableEntry entry) {
        return "on " + entry.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " from "
                + TIME.format(entry.getStartTime()) + " to " + TIME.format(entry.getEndTime());
    }

    private static String describe(TimetableEntry entry) {
        return label(entry.getSection()) + " " + entry.getSubject().getCode() + " "
                + entry.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                + TIME.format(entry.getStartTime()) + "-" + TIME.format(entry.getEndTime());
    }

    private record Candidate(Subject subject, UUID teacherId, String room, LocalDate from, LocalDate to,
            TimetableStatus status) {
    }

    private record Dated(TimetableEntry entry, LocalDate date) {
    }
}
