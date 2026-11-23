package com.schoolmanagement.attendance.service;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.entity.AttendanceRecord;
import com.schoolmanagement.attendance.entity.SessionType;
import com.schoolmanagement.attendance.repository.AttendanceRecordRepository;
import com.schoolmanagement.attendance.repository.AttendanceReportQueries;
import com.schoolmanagement.attendance.repository.AttendanceReportQueries.StudentTally;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.config.AttendanceProperties;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monthly percentages, the low-attendance report, and the attendance a student sees about themselves. */
@Service
public class AttendanceReportService {

    static final int MAX_ROWS = 500;
    private static final int MAX_DAYS = 366;

    private final AttendanceReportQueries queries;
    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final SectionRepository sections;
    private final SectionSubjectRepository assignments;
    private final AttendanceRecordRepository records;
    private final AttendanceProperties properties;
    private final Clock clock;

    public AttendanceReportService(AttendanceReportQueries queries, StudentProfileRepository students,
            StudentEnrollmentRepository enrollments, SectionRepository sections,
            SectionSubjectRepository assignments, AttendanceRecordRepository records,
            AttendanceProperties properties, Clock clock) {
        this.queries = queries;
        this.students = students;
        this.enrollments = enrollments;
        this.sections = sections;
        this.assignments = assignments;
        this.records = records;
        this.properties = properties;
        this.clock = clock;
    }

    public static YearMonth parseMonth(String value, Clock clock) {
        if (value == null || value.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new FieldValidationException("month", "Use the format YYYY-MM, for example 2026-11");
        }
    }

    /** Every student of a section for one month, including those with nothing counted yet. */
    @Transactional(readOnly = true)
    public AttendanceDtos.Report monthly(UUID schoolId, UUID sectionId, YearMonth month, SessionType type,
            UUID subjectId) {
        Section section = sections.findByIdAndSchoolId(sectionId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        return monthlyOf(schoolId, section, month, type, subjectId);
    }

    /** The same report for a teacher, limited to classes they are responsible for. */
    @Transactional(readOnly = true)
    public AttendanceDtos.Report monthlyForTeacher(AuthenticatedUser teacher, UUID sectionId, YearMonth month,
            SessionType type, UUID subjectId) {
        Section section = sections.findByIdAndSchoolId(sectionId, teacher.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        boolean classTeacher = teacher.id().equals(section.getClassTeacherId());
        boolean teachesSubject = subjectId != null && assignments
                .existsBySectionIdAndSubjectIdAndTeacherIdAndActiveTrue(sectionId, subjectId, teacher.id());
        // A subject teacher sees their own subject only; the class teacher sees the class.
        if (!(classTeacher || (type == SessionType.SUBJECT && teachesSubject))) {
            throw new AccessDeniedException("Not responsible for this class");
        }
        return monthlyOf(teacher.schoolId(), section, month, type, subjectId);
    }

    private AttendanceDtos.Report monthlyOf(UUID schoolId, Section section, YearMonth month, SessionType type,
            UUID subjectId) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        int threshold = properties.defaultThresholdPercent();
        Map<UUID, AttendanceTally> tallies = new HashMap<>();
        queries.tallies(schoolId, from, to, type, section.getId(), type == SessionType.SUBJECT ? subjectId : null)
                .forEach(t -> tallies.put(t.studentId(), t.tally()));

        Map<UUID, StudentProfile> people = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : enrollments.findActiveBySectionId(section.getId())) {
            people.put(enrollment.getStudent().getId(), enrollment.getStudent());
        }
        // Students who left the section during the month still have counted sessions.
        List<UUID> others = tallies.keySet().stream().filter(id -> !people.containsKey(id)).toList();
        if (!others.isEmpty()) {
            students.findAllByIdInAndSchoolId(others, schoolId).forEach(s -> people.put(s.getId(), s));
        }
        String sectionName = section.getGradeLevel().getName() + " - " + section.getName();
        List<AttendanceDtos.ReportRow> rows = people.values().stream()
                .map(student -> row(student, sectionName, tallies.getOrDefault(student.getId(),
                        AttendanceTally.EMPTY), threshold))
                .toList();
        return new AttendanceDtos.Report(from, to, type, threshold, false, rows);
    }

    /** Students whose attendance over a period is under the threshold, lowest first. */
    @Transactional(readOnly = true)
    public AttendanceDtos.Report belowThreshold(UUID schoolId, LocalDate from, LocalDate to, UUID sectionId,
            SessionType type, UUID subjectId, Integer thresholdPercent) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (end.isBefore(start)) {
            throw new FieldValidationException("to", "The end date cannot be before the start date");
        }
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new FieldValidationException("to", "Choose a period of at most one year");
        }
        int threshold = thresholdPercent == null ? properties.defaultThresholdPercent() : thresholdPercent;
        if (threshold < 1 || threshold > 100) {
            throw new FieldValidationException("threshold", "The threshold must be between 1 and 100");
        }
        if (sectionId != null) {
            sections.findByIdAndSchoolId(sectionId, schoolId).orElseThrow(() -> new ResourceNotFoundException("Section"));
        }
        List<StudentTally> low = queries.tallies(schoolId, start, end, type, sectionId,
                type == SessionType.SUBJECT ? subjectId : null).stream()
                .filter(t -> t.tally().isBelow(threshold))
                .sorted(Comparator.comparing((StudentTally t) -> t.tally().percentage())
                        .thenComparing(StudentTally::studentId))
                .toList();
        boolean truncated = low.size() > MAX_ROWS;
        List<StudentTally> shown = truncated ? low.subList(0, MAX_ROWS) : low;

        List<UUID> ids = shown.stream().map(StudentTally::studentId).toList();
        Map<UUID, StudentProfile> people = ids.isEmpty() ? Map.of()
                : students.findAllByIdInAndSchoolId(ids, schoolId).stream()
                        .collect(Collectors.toMap(StudentProfile::getId, s -> s));
        Map<UUID, String> classes = new HashMap<>();
        if (!ids.isEmpty()) {
            for (StudentEnrollment e : enrollments.findActiveByStudentIds(ids)) {
                classes.putIfAbsent(e.getStudent().getId(), e.getGradeLevel().getName() + " - " + e.getSection().getName());
            }
        }
        List<AttendanceDtos.ReportRow> rows = new ArrayList<>();
        for (StudentTally t : shown) {
            StudentProfile student = people.get(t.studentId());
            if (student != null) {
                rows.add(row(student, classes.getOrDefault(student.getId(), "-"), t.tally(), threshold));
            }
        }
        return new AttendanceDtos.Report(start, end, type, threshold, truncated, rows);
    }

    /** The signed-in student's own finalized attendance for a month. */
    @Transactional(readOnly = true)
    public AttendanceDtos.StudentMonth studentMonth(AuthenticatedUser student, YearMonth month) {
        StudentProfile profile = students.findByUserId(student.id())
                .filter(p -> p.getSchoolId().equals(student.schoolId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student"));
        List<AttendanceRecord> found = records.findFinalizedForStudent(profile.getId(), month.atDay(1),
                month.atEndOfMonth());
        AttendanceTally daily = AttendanceTally.EMPTY;
        AttendanceTally subjectWise = AttendanceTally.EMPTY;
        List<AttendanceDtos.StudentDay> days = new ArrayList<>();
        for (AttendanceRecord record : found) {
            var session = record.getSession();
            if (session.getType() == SessionType.DAILY) {
                daily = daily.plus(record.getStatus());
            } else {
                subjectWise = subjectWise.plus(record.getStatus());
            }
            days.add(new AttendanceDtos.StudentDay(session.getAttendanceDate(), session.getType(),
                    session.getSubject() == null ? null : session.getSubject().getName(),
                    session.getPeriodNumber(), record.getStatus(), record.getRemarks()));
        }
        return new AttendanceDtos.StudentMonth(month.toString(), properties.defaultThresholdPercent(), rate(daily),
                rate(subjectWise), days);
    }

    private static AttendanceDtos.Rate rate(AttendanceTally t) {
        return new AttendanceDtos.Rate(t.present(), t.late(), t.halfDay(), t.absent(), t.excused(), t.percentage());
    }

    private static AttendanceDtos.ReportRow row(StudentProfile student, String sectionName, AttendanceTally t,
            int threshold) {
        BigDecimal percentage = t.percentage();
        return new AttendanceDtos.ReportRow(student.getId(), student.getAdmissionNumber(),
                (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim(), sectionName,
                t.present(), t.late(), t.halfDay(), t.absent(), t.excused(), t.counted(), percentage,
                t.isBelow(threshold));
    }
}
