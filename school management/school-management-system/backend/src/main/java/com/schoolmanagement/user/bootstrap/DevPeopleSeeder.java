package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.guardian.entity.Guardian;
import com.schoolmanagement.guardian.entity.GuardianRelationship;
import com.schoolmanagement.guardian.repository.GuardianRepository;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.student.entity.Gender;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentGuardian;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentGuardianRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.teacher.entity.EmploymentStatus;
import com.schoolmanagement.teacher.entity.TeacherProfile;
import com.schoolmanagement.teacher.repository.TeacherProfileRepository;
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
 * Development-only: gives the demo teacher and student their profiles, adds a guardian and enrolls the student in
 * the demo section. Runs only against a local database and only once.
 */
@Component
@Profile("dev")
@Order(3)
class DevPeopleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevPeopleSeeder.class);

    private final SchoolRepository schools;
    private final UserRepository users;
    private final TeacherProfileRepository teachers;
    private final StudentProfileRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final SectionRepository sections;
    private final StudentEnrollmentRepository enrollments;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String datasourceUrl;
    private final String demoPassword;

    DevPeopleSeeder(SchoolRepository schools, UserRepository users, TeacherProfileRepository teachers,
            StudentProfileRepository students, GuardianRepository guardians, StudentGuardianRepository links,
            SectionRepository sections, StudentEnrollmentRepository enrollments, TransactionTemplate transaction,
            Clock clock, @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.users = users;
        this.teachers = teachers;
        this.students = students;
        this.guardians = guardians;
        this.links = links;
        this.sections = sections;
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
        UUID schoolId = school.getId();
        if (teachers.count() > 0 || students.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        users.findAllByEmailIgnoreCase("teacher@demo.school").stream().findFirst().ifPresent(user -> {
            TeacherProfile teacher = new TeacherProfile(schoolId, user, user.getUsername());
            teacher.update(today.minusYears(3), "M.Sc. Mathematics", "Mathematics", EmploymentStatus.ACTIVE);
            teachers.save(teacher);
        });
        User studentUser = users.findAllByEmailIgnoreCase("student@demo.school").stream().findFirst().orElse(null);
        if (studentUser == null) {
            return;
        }
        StudentProfile student = new StudentProfile(schoolId, studentUser, studentUser.getUsername());
        student.update(today.minusYears(13), Gender.OTHER, today.minusMonths(6), "O+", "12 Demo Street", null,
                "Demo City", "Demo State", "560001", "Demo Guardian", "+91 98765 43210", StudentStatus.ACTIVE);
        student = students.save(student);

        Guardian guardian = guardians.save(new Guardian(schoolId, "Demo", "Guardian", GuardianRelationship.MOTHER,
                "guardian@demo.school", "+91 98765 43210", "Engineer", "12 Demo Street, Demo City", true));
        links.save(new StudentGuardian(student, guardian, true, true, true));

        Section section = sections.findAll().stream().findFirst().orElse(null);
        if (section != null) {
            enrollments.save(new StudentEnrollment(student, section, 1, today, null, null, null));
        }
        log.info("Seeded demo teacher and student profiles, a guardian and an enrollment (development only)");
    }
}
