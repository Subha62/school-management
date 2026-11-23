package com.schoolmanagement.attendance.service;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.entity.AttendanceRecord;
import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.SessionStatus;
import com.schoolmanagement.attendance.repository.AttendanceRecordRepository;
import com.schoolmanagement.attendance.repository.AttendanceSessionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.config.AttendanceProperties;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attendance as taken by teachers. A teacher may work only on classes they are responsible for: the class teacher
 * takes the daily attendance, and a subject teacher takes the attendance of their own subject.
 */
@Service
public class AttendanceService {

    private static final String ENTITY = "AttendanceSession";

    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final SectionRepository sections;
    private final SectionSubjectRepository assignments;
    private final SubjectRepository subjects;
    private final StudentEnrollmentRepository enrollments;
    private final AttendanceViewBuilder views;
    private final AuditEventPublisher audit;
    private final AttendanceProperties properties;
    private final Clock clock;

    public AttendanceService(AttendanceSessionRepository sessions, AttendanceRecordRepository records,
            SectionRepository sections, SectionSubjectRepository assignments, SubjectRepository subjects,
            StudentEnrollmentRepository enrollments, AttendanceViewBuilder views, AuditEventPublisher audit,
            AttendanceProperties properties, Clock clock) {
        this.sessions = sessions;
        this.records = records;
        this.sections = sections;
        this.assignments = assignments;
        this.subjects = subjects;
        this.enrollments = enrollments;
        this.views = views;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** The result of opening a session: {@code created} is false when it already existed. */
    public record Opened(AttendanceDtos.Detail detail, boolean created) {
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.ClassOption> classes(AuthenticatedUser teacher) {
        Map<UUID, Section> bySection = new LinkedHashMap<>();
        Set<UUID> daily = new HashSet<>();
        Map<UUID, List<AttendanceDtos.SubjectOption>> subjectsBySection = new HashMap<>();
        for (Section section : sections.findAllByClassTeacherIdAndSchoolId(teacher.id(), teacher.schoolId())) {
            if (isOpenForAttendance(section)) {
                bySection.put(section.getId(), section);
                daily.add(section.getId());
            }
        }
        assignments.findAllByTeacherIdAndActiveTrueAndSchoolId(teacher.id(), teacher.schoolId()).stream()
                .filter(assignment -> isOpenForAttendance(assignment.getSection()))
                .forEach(assignment -> {
                    bySection.putIfAbsent(assignment.getSection().getId(), assignment.getSection());
                    subjectsBySection.computeIfAbsent(assignment.getSection().getId(), key -> new ArrayList<>())
                            .add(new AttendanceDtos.SubjectOption(assignment.getSubject().getId(),
                                    assignment.getSubject().getName()));
                });
        return bySection.values().stream()
                .sorted((a, b) -> label(a).compareToIgnoreCase(label(b)))
                .map(section -> new AttendanceDtos.ClassOption(section.getId(), label(section),
                        section.getAcademicYear().getName(), daily.contains(section.getId()),
                        subjectsBySection.getOrDefault(section.getId(), List.of()).stream()
                                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.Summary> listMine(AuthenticatedUser teacher, UUID sectionId, LocalDate from,
            LocalDate to, SessionStatus status, Pageable pageable) {
        Specification<AttendanceSession> spec = Specification
                .<AttendanceSession>where(SearchSpecs.equalTo("schoolId", teacher.schoolId()))
                .and(SearchSpecs.equalTo("createdBy", teacher.id()))
                .and(SearchSpecs.equalTo("status", status));
        if (sectionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("section").get("id"), sectionId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("attendanceDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("attendanceDate"), to));
        }
        var page = sessions.findAll(spec, pageable);
        List<AttendanceDtos.Summary> summaries = views.summaries(page.getContent());
        return new PageResponse<>(summaries, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional
    public Opened open(AuthenticatedUser teacher, AttendanceDtos.OpenRequest request) {
        Section section = sections.findByIdAndSchoolId(request.sectionId(), teacher.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        boolean subjectSession = request.subjectId() != null;
        if (!subjectSession && request.periodNumber() != null) {
            throw new FieldValidationException("periodNumber", "A period applies to subject attendance only");
        }
        requireAuthorized(teacher, section, request.subjectId());

        var existing = subjectSession
                ? sessions.findSubjectSession(section.getId(), request.date(), request.subjectId(),
                        request.periodNumber() == null ? 0 : request.periodNumber())
                : sessions.findBySectionIdAndAttendanceDateAndType(section.getId(), request.date(),
                        com.schoolmanagement.attendance.entity.SessionType.DAILY);
        if (existing.isPresent()) {
            return new Opened(views.detail(existing.get()), false);
        }

        if (!isOpenForAttendance(section)) {
            throw new BusinessRuleException(
                    "Attendance can only be taken for active sections of the active academic year.");
        }
        requireDateAllowed(request.date(), section.getAcademicYear(), true);
        Subject subject = null;
        if (subjectSession) {
            subject = subjects.findByIdAndSchoolId(request.subjectId(), teacher.schoolId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subject"));
        }
        AttendanceSession session = sessions.saveAndFlush(
                new AttendanceSession(section, request.date(), subject, request.periodNumber(), teacher.id()));
        audit.record(teacher, "ATTENDANCE_SESSION_OPENED", ENTITY, session.getId(), describe(session));
        return new Opened(views.detail(session), true);
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.Detail get(AuthenticatedUser teacher, UUID id) {
        AttendanceSession session = sessions.findByIdAndSchoolId(id, teacher.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session"));
        requireAuthorized(teacher, session);
        return views.detail(session);
    }

    @Transactional
    public AttendanceDtos.Detail mark(AuthenticatedUser teacher, UUID id, AttendanceDtos.MarkRequest request) {
        AttendanceSession session = lockedEditable(teacher, id);
        Set<UUID> seen = new HashSet<>();
        for (AttendanceDtos.MarkItem item : request.records()) {
            if (!seen.add(item.studentId())) {
                throw new BusinessRuleException("A student can only appear once in a request.");
            }
        }
        Map<UUID, StudentProfile> roster = new HashMap<>();
        for (StudentEnrollment enrollment : enrollments.findActiveBySectionId(session.getSection().getId())) {
            roster.put(enrollment.getStudent().getId(), enrollment.getStudent());
        }
        long notEnrolled = request.records().stream().filter(item -> !roster.containsKey(item.studentId())).count();
        if (notEnrolled > 0) {
            throw new BusinessRuleException(notEnrolled + (notEnrolled == 1 ? " student is" : " students are")
                    + " not enrolled in this section, so attendance cannot be marked for them.");
        }

        Map<UUID, AttendanceRecord> existing = new HashMap<>();
        records.findAllBySessionId(session.getId()).forEach(r -> existing.put(r.getStudent().getId(), r));
        List<AttendanceRecord> changed = new ArrayList<>();
        for (AttendanceDtos.MarkItem item : request.records()) {
            String remarks = item.remarks() == null || item.remarks().isBlank() ? null : item.remarks().trim();
            AttendanceRecord record = existing.get(item.studentId());
            if (record == null) {
                record = new AttendanceRecord(session, roster.get(item.studentId()), item.status(), remarks,
                        teacher.id());
            } else {
                record.change(item.status(), remarks, teacher.id());
            }
            changed.add(record);
        }
        records.saveAllAndFlush(changed);
        audit.record(teacher, "ATTENDANCE_MARKED", ENTITY, id, "Marked " + changed.size() + " students in "
                + describe(session));
        return views.detail(session);
    }

    @Transactional
    public AttendanceDtos.Detail finalizeSession(AuthenticatedUser teacher, UUID id) {
        AttendanceSession session = lockedEditable(teacher, id);
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(session.getSection().getId());
        if (roster.isEmpty()) {
            throw new BusinessRuleException("There are no enrolled students in this section.");
        }
        Set<UUID> marked = new HashSet<>();
        records.findAllBySessionId(id).forEach(record -> marked.add(record.getStudent().getId()));
        long unmarked = roster.stream().filter(e -> !marked.contains(e.getStudent().getId())).count();
        if (unmarked > 0) {
            throw new BusinessRuleException(unmarked + (unmarked == 1 ? " student has" : " students have")
                    + " not been marked yet. Mark everyone before finalizing.");
        }
        session.finalizeBy(teacher.id(), clock.instant());
        sessions.saveAndFlush(session);
        audit.record(teacher, "ATTENDANCE_FINALIZED", ENTITY, id, "Finalized " + describe(session));
        return views.detail(session);
    }

    /** Removes a session opened by mistake; only possible before it is finalized. */
    @Transactional
    public void deleteOpen(AuthenticatedUser teacher, UUID id) {
        AttendanceSession session = lockedEditable(teacher, id);
        sessions.delete(session);
        audit.record(teacher, "ATTENDANCE_SESSION_DELETED", ENTITY, id, "Deleted " + describe(session));
    }

    // ---- rules -----------------------------------------------------------------------------------------

    /** Locks the session and checks everything that must hold before a teacher may change it. */
    private AttendanceSession lockedEditable(AuthenticatedUser teacher, UUID id) {
        AttendanceSession locked = sessions.lockByIdAndSchoolId(id, teacher.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session"));
        requireAuthorized(teacher, locked);
        if (locked.isFinalized()) {
            throw new BusinessRuleException(
                    "Finalized attendance can no longer be changed by teachers. Ask an administrator for a correction.");
        }
        requireDateAllowed(locked.getAttendanceDate(), locked.getAcademicYear(), false);
        return locked;
    }

    private void requireAuthorized(AuthenticatedUser teacher, AttendanceSession session) {
        requireAuthorized(teacher, session.getSection(),
                session.getSubject() == null ? null : session.getSubject().getId());
    }

    private void requireAuthorized(AuthenticatedUser teacher, Section section, UUID subjectId) {
        boolean allowed = subjectId == null
                ? teacher.id().equals(section.getClassTeacherId())
                : assignments.existsBySectionIdAndSubjectIdAndTeacherIdAndActiveTrue(section.getId(), subjectId,
                        teacher.id());
        if (!allowed) {
            throw new AccessDeniedException("Not responsible for this class");
        }
    }

    private void requireDateAllowed(LocalDate date, AcademicYear year, boolean checkYearBounds) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new FieldValidationException("date", "Attendance cannot be taken for a future date");
        }
        if (checkYearBounds && (date.isBefore(year.getStartDate()) || date.isAfter(year.getEndDate()))) {
            throw new FieldValidationException("date", "The date is outside the academic year ("
                    + year.getStartDate() + " to " + year.getEndDate() + ")");
        }
        if (date.isBefore(today.minusDays(properties.teacherBackdateDays()))) {
            throw new BusinessRuleException("Teachers can take or change attendance for the last "
                    + properties.teacherBackdateDays() + " days only. Ask an administrator for older dates.");
        }
    }

    private static boolean isOpenForAttendance(Section section) {
        return section.isActive() && section.getAcademicYear().getStatus() == AcademicYearStatus.ACTIVE;
    }

    private static String label(Section section) {
        return section.getGradeLevel().getName() + " - " + section.getName();
    }

    static String describe(AttendanceSession session) {
        return label(session.getSection()) + " " + session.getAttendanceDate()
                + (session.getSubject() == null ? " (daily)" : " (" + session.getSubject().getCode() + ")");
    }
}
