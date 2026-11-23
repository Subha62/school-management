package com.schoolmanagement.dashboard;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.announcement.entity.Announcement;
import com.schoolmanagement.announcement.entity.AnnouncementStatus;
import com.schoolmanagement.announcement.repository.AnnouncementRepository;
import com.schoolmanagement.announcement.service.AnnouncementService;
import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.AssignmentStatus;
import com.schoolmanagement.assignment.entity.SubmissionStatus;
import com.schoolmanagement.assignment.service.AssignmentService;
import com.schoolmanagement.assignment.service.StudentAssignmentService;
import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.SessionStatus;
import com.schoolmanagement.attendance.entity.SessionType;
import com.schoolmanagement.attendance.repository.AttendanceSessionRepository;
import com.schoolmanagement.attendance.service.AttendanceReportService;
import com.schoolmanagement.audit.AuditLogService;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.entity.ExamStatus;
import com.schoolmanagement.exam.service.ExamService;
import com.schoolmanagement.exam.service.MarksService;
import com.schoolmanagement.exam.service.ResultService;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.service.FeeReportService;
import com.schoolmanagement.fee.service.StudentFeeService;
import com.schoolmanagement.school.service.SchoolTime;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.teacher.entity.EmploymentStatus;
import com.schoolmanagement.teacher.repository.TeacherProfileRepository;
import com.schoolmanagement.timetable.dto.TimetableDtos;
import com.schoolmanagement.timetable.service.TimetableService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers on each role's home page. Everything is derived from the same services and repositories the feature
 * screens use, scoped to the caller (a teacher's numbers are about their own classes, a student's about themselves),
 * and nothing here changes data.
 */
@Service
public class DashboardService {

    private static final int RECENT_ACTIVITY = 8;
    private static final int UPCOMING = 5;

    private final AcademicYearRepository years;
    private final StudentProfileRepository students;
    private final TeacherProfileRepository teachers;
    private final SectionRepository sections;
    private final com.schoolmanagement.academic.repository.SectionSubjectRepository sectionSubjects;
    private final StudentEnrollmentRepository enrollments;
    private final AttendanceSessionRepository sessions;
    private final AnnouncementRepository announcements;
    private final AnnouncementService announcementService;
    private final AuditLogService auditLogs;
    private final FeeReportService feeReports;
    private final StudentFeeService studentFees;
    private final ExamService exams;
    private final MarksService marks;
    private final ResultService results;
    private final TimetableService timetable;
    private final AssignmentService assignments;
    private final StudentAssignmentService studentAssignments;
    private final AttendanceReportService attendanceReports;
    private final SchoolTime schoolTime;
    private final Clock clock;

    public DashboardService(AcademicYearRepository years, StudentProfileRepository students,
            TeacherProfileRepository teachers, SectionRepository sections,
            com.schoolmanagement.academic.repository.SectionSubjectRepository sectionSubjects,
            StudentEnrollmentRepository enrollments,
            AttendanceSessionRepository sessions, AnnouncementRepository announcements,
            AnnouncementService announcementService, AuditLogService auditLogs, FeeReportService feeReports,
            StudentFeeService studentFees, ExamService exams, MarksService marks, ResultService results,
            TimetableService timetable, AssignmentService assignments, StudentAssignmentService studentAssignments,
            AttendanceReportService attendanceReports, SchoolTime schoolTime, Clock clock) {
        this.years = years;
        this.students = students;
        this.teachers = teachers;
        this.sections = sections;
        this.sectionSubjects = sectionSubjects;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.announcements = announcements;
        this.announcementService = announcementService;
        this.auditLogs = auditLogs;
        this.feeReports = feeReports;
        this.studentFees = studentFees;
        this.exams = exams;
        this.marks = marks;
        this.results = results;
        this.timetable = timetable;
        this.assignments = assignments;
        this.studentAssignments = studentAssignments;
        this.attendanceReports = attendanceReports;
        this.schoolTime = schoolTime;
        this.clock = clock;
    }

    // ---- administrators ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardDtos.Admin admin(AuthenticatedUser actor) {
        UUID schoolId = actor.schoolId();
        LocalDate today = schoolTime.today(schoolId);
        Optional<AcademicYear> year = years.findFirstBySchoolIdAndStatus(schoolId, AcademicYearStatus.ACTIVE);

        long activeStudents = students.count(Specification.<StudentProfile>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("status", StudentStatus.ACTIVE)));
        long activeTeachers = teachers.count(Specification
                .<com.schoolmanagement.teacher.entity.TeacherProfile>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("employmentStatus", EmploymentStatus.ACTIVE)));
        long activeClasses = year.map(y -> sections.count(Specification.<Section>where(
                SearchSpecs.equalTo("schoolId", schoolId)).and(SearchSpecs.equalTo("active", true))
                .and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), y.getId())))).orElse(0L);

        DashboardDtos.AttendanceToday attendance = new DashboardDtos.AttendanceToday(
                registers(schoolId, today, SessionStatus.FINALIZED), registers(schoolId, today, SessionStatus.OPEN));

        DashboardDtos.FeeSnapshot fees = year.map(y -> {
            FeeDtos.Summary summary = feeReports.summary(schoolId, y.getId(), null);
            return new DashboardDtos.FeeSnapshot(summary.billed(), summary.collected(), summary.outstanding(),
                    summary.overdue(), summary.countByStatus().getOrDefault(FeeStatus.OVERDUE, 0L));
        }).orElse(null);

        DashboardDtos.ExamSnapshot examSnapshot = new DashboardDtos.ExamSnapshot(
                examCount(schoolId, ExamStatus.OPEN_FOR_MARKS), examCount(schoolId, ExamStatus.LOCKED),
                examCount(schoolId, ExamStatus.PUBLISHED));

        Instant now = clock.instant();
        long live = announcements.count(Specification.<Announcement>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("status", AnnouncementStatus.PUBLISHED))
                .and((root, query, cb) -> cb.or(cb.isNull(root.get("publishAt")),
                        cb.lessThanOrEqualTo(root.<Instant>get("publishAt"), now)))
                .and((root, query, cb) -> cb.or(cb.isNull(root.get("expiresAt")),
                        cb.greaterThan(root.<Instant>get("expiresAt"), now))));

        return new DashboardDtos.Admin(year.map(AcademicYear::getName).orElse(null), activeStudents, activeTeachers,
                activeClasses, attendance, fees, examSnapshot, live, auditLogs.recent(schoolId, RECENT_ACTIVITY));
    }

    private long registers(UUID schoolId, LocalDate day, SessionStatus status) {
        return sessions.count(Specification.<AttendanceSession>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("attendanceDate", day)).and(SearchSpecs.equalTo("status", status)));
    }

    private long examCount(UUID schoolId, ExamStatus status) {
        return exams.list(schoolId, null, null, status, null, PageRequest.of(0, 1)).totalElements();
    }

    // ---- teachers ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardDtos.Teacher teacher(AuthenticatedUser actor) {
        UUID schoolId = actor.schoolId();
        LocalDate today = schoolTime.today(schoolId);

        List<DashboardDtos.Slot> slots = slotsOf(timetable.ownWeek(schoolId, actor.id(), today), today);
        List<Section> classTeacherOf = sections.findAllByClassTeacherIdAndSchoolId(actor.id(), schoolId);
        java.util.Set<UUID> taught = new java.util.HashSet<>();
        sectionSubjects.findAllByTeacherIdAndActiveTrueAndSchoolId(actor.id(), schoolId).stream()
                .filter(a -> !a.getAcademicYear().isClosed()).forEach(a -> taught.add(a.getSection().getId()));
        classTeacherOf.stream().filter(s -> !s.getAcademicYear().isClosed()).forEach(s -> taught.add(s.getId()));

        long awaiting = assignments.reviewQueue(actor, null, null, true, PageRequest.of(0, 1)).totalElements();
        long missing = 0;
        for (ExamDtos.TeacherExam exam : marks.myExams(actor, PageRequest.of(0, 100)).content()) {
            if (exam.status() == ExamStatus.OPEN_FOR_MARKS) {
                missing += exam.tasks().stream().mapToLong(t -> Math.max(0, t.expected() - t.entered())).sum();
            }
        }

        List<String> toTake = new ArrayList<>();
        for (Section section : classTeacherOf) {
            boolean open = section.isActive() && !section.getAcademicYear().isClosed();
            if (open && sessions.findBySectionIdAndAttendanceDateAndType(section.getId(), today, SessionType.DAILY)
                    .isEmpty()) {
                toTake.add(section.getGradeLevel().getName() + " - " + section.getName());
            }
        }
        toTake.sort(Comparator.naturalOrder());
        return new DashboardDtos.Teacher(slots, taught.size(), awaiting, missing, toTake,
                announcementService.unreadCount(actor).count());
    }

    // ---- students ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardDtos.Student student(AuthenticatedUser actor) {
        UUID schoolId = actor.schoolId();
        LocalDate today = schoolTime.today(schoolId);
        Optional<StudentProfile> profile = students.findByUserId(actor.id())
                .filter(p -> p.getSchoolId().equals(schoolId));
        String className = profile.flatMap(p -> enrollments.findAllActiveByStudentId(p.getId()).stream().findFirst())
                .map(DashboardService::className).orElse(null);

        List<DashboardDtos.Slot> slots = slotsOf(timetable.studentWeek(schoolId, actor.id(), today), today);

        BigDecimal attendance = profile.isPresent() && className != null
                ? attendanceReports.studentMonth(actor, YearMonth.from(today)).daily().percentage() : null;

        Instant now = clock.instant();
        List<DashboardDtos.Upcoming> upcoming = studentAssignments
                .list(actor, AssignmentStatus.PUBLISHED, null, PageRequest.of(0, 50, Sort.by("dueAt"))).content()
                .stream().filter(a -> a.canSubmit() && needsWork(a, now)).limit(UPCOMING)
                .map(a -> new DashboardDtos.Upcoming(a.id(), a.title(), a.subjectName(), a.dueAt(),
                        a.submission() != null && a.submission().status() == SubmissionStatus.RESUBMISSION_REQUESTED))
                .toList();

        DashboardDtos.LatestResult latest = profile.isEmpty() ? null : results
                .studentResults(actor, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))).content()
                .stream().findFirst().map(r -> new DashboardDtos.LatestResult(r.examId(), r.examName(),
                        r.percentage(), r.grade(), r.result())).orElse(null);

        DashboardDtos.StudentFees fees = profile.isEmpty() ? new DashboardDtos.StudentFees(BigDecimal.ZERO, 0, null)
                : feesOf(actor);
        return new DashboardDtos.Student(className, slots, attendance, upcoming, latest, fees,
                announcementService.unreadCount(actor).count());
    }

    private static boolean needsWork(AssignmentDtos.StudentView a, Instant now) {
        return a.submission() == null || a.submission().status() == SubmissionStatus.RESUBMISSION_REQUESTED;
    }

    private DashboardDtos.StudentFees feesOf(AuthenticatedUser actor) {
        BigDecimal outstanding = BigDecimal.ZERO;
        long overdue = 0;
        LocalDate next = null;
        for (FeeDtos.StudentFeeResponse fee : studentFees.mine(actor, null, PageRequest.of(0, 100, Sort.by("dueDate")))
                .content()) {
            if (fee.status() == FeeStatus.WAIVED || fee.balance().signum() <= 0) {
                continue;
            }
            outstanding = outstanding.add(fee.balance());
            if (fee.status() == FeeStatus.OVERDUE) {
                overdue++;
            } else if (next == null || fee.dueDate().isBefore(next)) {
                next = fee.dueDate();
            }
        }
        return new DashboardDtos.StudentFees(outstanding, overdue, next);
    }

    // ---- shared -----------------------------------------------------------------------------------------

    private static List<DashboardDtos.Slot> slotsOf(TimetableDtos.Week week, LocalDate today) {
        return week.entries().stream().filter(e -> today.equals(e.date()))
                .sorted(Comparator.comparing(TimetableDtos.Response::startTime))
                .map(e -> new DashboardDtos.Slot(e.periodNumber(), e.startTime(), e.endTime(), e.subjectName(),
                        e.sectionName(), e.room()))
                .toList();
    }

    private static String className(StudentEnrollment enrollment) {
        return enrollment.getGradeLevel().getName() + " - " + enrollment.getSection().getName();
    }
}
