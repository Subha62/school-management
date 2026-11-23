package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.GradeLevelRepository;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
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
 * Development-only demo academic data for the demo school: one active year, one grade, one section with the
 * demo teacher as class teacher, and several subjects. Runs only against a local database and only once.
 */
@Component
@Profile("dev")
@Order(2)
class DevAcademicSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAcademicSeeder.class);

    private final SchoolRepository schools;
    private final UserRepository users;
    private final AcademicYearRepository years;
    private final GradeLevelRepository grades;
    private final SubjectRepository subjects;
    private final SectionRepository sections;
    private final SectionSubjectRepository sectionSubjects;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String datasourceUrl;
    private final String demoPassword;

    DevAcademicSeeder(SchoolRepository schools, UserRepository users, AcademicYearRepository years,
            GradeLevelRepository grades, SubjectRepository subjects, SectionRepository sections,
            SectionSubjectRepository sectionSubjects, TransactionTemplate transaction, Clock clock,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.users = users;
        this.years = years;
        this.grades = grades;
        this.subjects = subjects;
        this.sections = sections;
        this.sectionSubjects = sectionSubjects;
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
        UUID schoolId = school.getId();
        if (years.count() > 0) {
            return;
        }
        UUID teacherId = users.findAllByEmailIgnoreCase("teacher@demo.school").stream().findFirst()
                .map(User::getId).orElse(null);

        LocalDate today = LocalDate.now(clock);
        int startYear = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
        AcademicYear year = new AcademicYear(schoolId, startYear + "-" + (startYear + 1),
                LocalDate.of(startYear, 6, 1), LocalDate.of(startYear + 1, 3, 31));
        year.activate();
        year = years.save(year);

        GradeLevel grade = grades.save(new GradeLevel(schoolId, "Grade 8", 8, true));
        Section section = sections.save(new Section(schoolId, year, grade, "A", 40, teacherId, true));

        String[][] demoSubjects = {{"MATH", "Mathematics"}, {"ENG", "English"}, {"SCI", "Science"},
                {"SOC", "Social Studies"}, {"CS", "Computer Science"}};
        int periods = 6;
        for (String[] entry : demoSubjects) {
            Subject subject = subjects.save(new Subject(schoolId, entry[0], entry[1], null, true));
            sectionSubjects.save(new SectionSubject(section, subject, teacherId, periods));
            periods = Math.max(2, periods - 1);
        }
        log.info("Seeded demo academic year {}, Grade 8-A and {} subjects (development only)", year.getName(),
                demoSubjects.length);
    }
}
