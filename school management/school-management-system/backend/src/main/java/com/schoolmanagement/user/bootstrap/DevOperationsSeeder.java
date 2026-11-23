package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.attendance.entity.AttendanceRecord;
import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.AttendanceStatus;
import com.schoolmanagement.attendance.repository.AttendanceRecordRepository;
import com.schoolmanagement.attendance.repository.AttendanceSessionRepository;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.timetable.entity.TimetableEntry;
import com.schoolmanagement.timetable.entity.TimetableStatus;
import com.schoolmanagement.timetable.repository.TimetableEntryRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Development-only: a weekly timetable for the demo section and two weeks of finalized daily attendance for its
 * students. Runs only against a local database and only once.
 */
@Component
@Profile("dev")
@Order(4)
class DevOperationsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevOperationsSeeder.class);
    private static final int PERIODS_PER_DAY = 5;
    private static final int PAST_SCHOOL_DAYS = 10;

    private final SchoolRepository schools;
    private final SectionRepository sections;
    private final SectionSubjectRepository assignments;
    private final TimetableEntryRepository timetable;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final StudentEnrollmentRepository enrollments;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String datasourceUrl;
    private final String demoPassword;

    DevOperationsSeeder(SchoolRepository schools, SectionRepository sections,
            SectionSubjectRepository assignments, TimetableEntryRepository timetable,
            AttendanceSessionRepository sessions, AttendanceRecordRepository records,
            StudentEnrollmentRepository enrollments, TransactionTemplate transaction, Clock clock,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.sections = sections;
        this.assignments = assignments;
        this.timetable = timetable;
        this.sessions = sessions;
        this.records = records;
        this.enrollments = enrollments;
        this.transaction = transaction;
        this.clock = clock;
        this.datasourceUrl = datasourceUrl;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword.isBlank() || !LocalDatabase.isLocal(datasourceUrl)) {
            return;
        }
        schools.findBySchoolCode(DevDataSeeder.SCHOOL_CODE).ifPresent(school -> transaction.executeWithoutResult(
                status -> seed(school)));
    }

    private void seed(School school) {
        if (timetable.count() > 0 || sessions.count() > 0) {
            return;
        }
        Section section = sections.findAll().stream().findFirst().orElse(null);
        if (section == null || section.getClassTeacherId() == null) {
            return;
        }
        List<SectionSubject> subjects = assignments.findAll().stream()
                .filter(a -> a.getSection().getId().equals(section.getId()) && a.getTeacherId() != null)
                .sorted(Comparator.comparing(a -> a.getSubject().getCode()))
                .toList();
        seedTimetable(section, subjects);
        seedAttendance(section);
        log.info("Seeded a demo timetable and past attendance for {} (development only)", section.getName());
    }

    private void seedTimetable(Section section, List<SectionSubject> subjects) {
        if (subjects.isEmpty()) {
            return;
        }
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY};
        for (int day = 0; day < days.length; day++) {
            for (int period = 0; period < PERIODS_PER_DAY; period++) {
                // Rotate the subjects so each weekday starts with a different one.
                SectionSubject assignment = subjects.get((day + period) % subjects.size());
                LocalTime start = LocalTime.of(9, 0).plusMinutes(period * 50L);
                timetable.save(new TimetableEntry(section, assignment.getSubject(), assignment.getTeacherId(),
                        "R-" + section.getName() + "01", days[day], period + 1, start, start.plusMinutes(45),
                        section.getAcademicYear().getStartDate(), section.getAcademicYear().getEndDate(),
                        TimetableStatus.ACTIVE));
            }
        }
    }

    private void seedAttendance(Section section) {
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(section.getId());
        if (roster.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        List<LocalDate> schoolDays = new ArrayList<>();
        for (LocalDate day = today.minusDays(1); schoolDays.size() < PAST_SCHOOL_DAYS
                && !day.isBefore(section.getAcademicYear().getStartDate()); day = day.minusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                schoolDays.add(day);
            }
        }
        for (int i = 0; i < schoolDays.size(); i++) {
            AttendanceSession session = sessions.save(
                    new AttendanceSession(section, schoolDays.get(i), null, null, section.getClassTeacherId()));
            for (StudentEnrollment enrollment : roster) {
                // Three absences in ten days put the demo student under the 75% threshold.
                AttendanceStatus status = i % 3 == 2 ? AttendanceStatus.ABSENT
                        : i == 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
                records.save(new AttendanceRecord(session, enrollment.getStudent(), status, null,
                        section.getClassTeacherId()));
            }
            session.finalizeBy(section.getClassTeacherId(), clock.instant());
        }
    }
}
