package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.assignment.entity.Assignment;
import com.schoolmanagement.assignment.entity.AssignmentSubmission;
import com.schoolmanagement.assignment.repository.AssignmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionRepository;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Development-only: one open assignment and one closed, reviewed one for the demo class. Runs only against a local
 * database and only once. Seeded assignments carry no files, because file content is not part of the seed data.
 */
@Component
@Profile("dev")
@Order(5)
class DevAssignmentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAssignmentSeeder.class);

    private final SchoolRepository schools;
    private final SectionRepository sections;
    private final SectionSubjectRepository sectionSubjects;
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final StudentEnrollmentRepository enrollments;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String datasourceUrl;
    private final String demoPassword;

    DevAssignmentSeeder(SchoolRepository schools, SectionRepository sections,
            SectionSubjectRepository sectionSubjects, AssignmentRepository assignments,
            SubmissionRepository submissions, StudentEnrollmentRepository enrollments,
            TransactionTemplate transaction, Clock clock, @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.sections = sections;
        this.sectionSubjects = sectionSubjects;
        this.assignments = assignments;
        this.submissions = submissions;
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
        if (assignments.count() > 0) {
            return;
        }
        Section section = sections.findAll().stream().findFirst().orElse(null);
        if (section == null) {
            return;
        }
        List<SectionSubject> taught = sectionSubjects.findAll().stream()
                .filter(a -> a.getSection().getId().equals(section.getId()) && a.getTeacherId() != null)
                .sorted((a, b) -> a.getSubject().getCode().compareTo(b.getSubject().getCode()))
                .toList();
        if (taught.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        SectionSubject first = taught.get(0);
        SectionSubject second = taught.get(Math.min(1, taught.size() - 1));

        Assignment open = new Assignment(section, first.getSubject(), first.getTeacherId(), "Worksheet 1",
                "Complete the questions and show your working. Attach a photo of your notebook or type your answers.",
                now.plus(Duration.ofDays(7)), new BigDecimal("20"), false);
        open.publish(now.minus(Duration.ofDays(1)));
        assignments.save(open);

        Assignment closed = new Assignment(section, second.getSubject(), second.getTeacherId(), "Reading log",
                "Write a short summary of the chapter you read this week.", now.minus(Duration.ofDays(3)),
                new BigDecimal("10"), true);
        closed.publish(now.minus(Duration.ofDays(8)));
        closed.close(now.minus(Duration.ofDays(2)));
        assignments.save(closed);

        for (StudentEnrollment enrollment : enrollments.findActiveBySectionId(section.getId())) {
            AssignmentSubmission submission = new AssignmentSubmission(closed, enrollment.getStudent(),
                    "I read chapter 3 and learned how plants make food.", now.minus(Duration.ofDays(4)), false);
            submission.review(new BigDecimal("8.5"), "Good summary. Add one example next time.",
                    second.getTeacherId(), now.minus(Duration.ofDays(2)));
            submissions.save(submission);
        }
        log.info("Seeded demo assignments (development only)");
    }
}
