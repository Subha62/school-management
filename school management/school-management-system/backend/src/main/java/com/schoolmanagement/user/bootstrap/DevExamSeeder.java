package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.exam.entity.Exam;
import com.schoolmanagement.exam.entity.ExamComment;
import com.schoolmanagement.exam.entity.ExamSubject;
import com.schoolmanagement.exam.entity.GradeRule;
import com.schoolmanagement.exam.entity.StudentMark;
import com.schoolmanagement.exam.repository.ExamCommentRepository;
import com.schoolmanagement.exam.repository.ExamRepository;
import com.schoolmanagement.exam.repository.GradeRuleRepository;
import com.schoolmanagement.exam.repository.StudentMarkRepository;
import com.schoolmanagement.exam.service.ResultService;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Development-only: a grade scale, one published exam with results for the demo class, and one exam that is open for
 * marks with half of the marks still missing. Runs only against a local database and only once.
 */
@Component
@Profile("dev")
@Order(6)
class DevExamSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevExamSeeder.class);

    private final SchoolRepository schools;
    private final SectionRepository sections;
    private final SectionSubjectRepository sectionSubjects;
    private final StudentEnrollmentRepository enrollments;
    private final GradeRuleRepository gradeRules;
    private final ExamRepository exams;
    private final StudentMarkRepository marks;
    private final ExamCommentRepository comments;
    private final UserRepository users;
    private final ResultService results;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String datasourceUrl;
    private final String demoPassword;

    DevExamSeeder(SchoolRepository schools, SectionRepository sections, SectionSubjectRepository sectionSubjects,
            StudentEnrollmentRepository enrollments, GradeRuleRepository gradeRules, ExamRepository exams,
            StudentMarkRepository marks, ExamCommentRepository comments, UserRepository users,
            ResultService results, TransactionTemplate transaction, Clock clock,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.sections = sections;
        this.sectionSubjects = sectionSubjects;
        this.enrollments = enrollments;
        this.gradeRules = gradeRules;
        this.exams = exams;
        this.marks = marks;
        this.comments = comments;
        this.users = users;
        this.results = results;
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
        schools.findBySchoolCode(DevDataSeeder.SCHOOL_CODE)
                .ifPresent(school -> transaction.executeWithoutResult(status -> seed(school)));
    }

    private void seed(School school) {
        if (exams.count() > 0) {
            return;
        }
        Section section = sections.findAll().stream().findFirst().orElse(null);
        User admin = users.findByLoginIdentifier("admin@demo.school").stream().findFirst().orElse(null);
        List<SectionSubject> taught = section == null ? List.of() : sectionSubjects.findAll().stream()
                .filter(a -> a.getSection().getId().equals(section.getId()) && a.getTeacherId() != null)
                .sorted(Comparator.comparing(a -> a.getSubject().getCode())).toList();
        if (admin == null || taught.isEmpty()) {
            return;
        }
        scale(school);
        Instant now = clock.instant();

        Exam published = exam(school, section, taught, "Term 1", now.minus(Duration.ofDays(20)));
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(section.getId());
        int[] bases = {88, 72, 55, 41, 93, 64};
        for (int i = 0; i < roster.size(); i++) {
            StudentEnrollment enrollment = roster.get(i);
            for (int s = 0; s < published.getSubjects().size(); s++) {
                ExamSubject subject = published.getSubjects().get(s);
                int percent = Math.max(20, Math.min(98, bases[i % bases.length] - s * 6));
                marks.save(entered(subject, enrollment, section, taught, percent));
            }
            comments.save(new ExamComment(published, enrollment.getStudent(), section,
                    "Steady progress this term. Keep reading widely.", taught.get(0).getTeacherId()));
        }
        marks.flush();
        published.open();
        published.lock(now.minus(Duration.ofDays(5)), admin.getId());
        results.freeze(published);
        published.publish(now.minus(Duration.ofDays(4)), admin.getId());
        exams.save(published);

        Exam open = exam(school, section, taught, "Mid-term", now.plus(Duration.ofDays(20)));
        open.open();
        if (!roster.isEmpty()) {
            ExamSubject first = open.getSubjects().get(0);
            marks.save(entered(first, roster.get(0), section, taught, 76));
        }
        exams.save(open);
        log.info("Seeded demo exams (development only)");
    }

    private void scale(School school) {
        String[][] bands = {{"F", "Needs support", "0", "39.99"}, {"D", "Satisfactory", "40", "49.99"},
                {"C", "Good", "50", "59.99"}, {"B", "Very good", "60", "74.99"}, {"A", "Excellent", "75", "89.99"},
                {"A+", "Outstanding", "90", "100"}};
        for (String[] band : bands) {
            gradeRules.save(new GradeRule(school.getId(), band[0], band[1], new BigDecimal(band[2]),
                    new BigDecimal(band[3])));
        }
    }

    private Exam exam(School school, Section section, List<SectionSubject> taught, String name, Instant when) {
        Exam exam = new Exam(school.getId(), section.getAcademicYear(), section.getGradeLevel(), name,
                java.time.LocalDate.ofInstant(when, java.time.ZoneOffset.UTC), null);
        for (SectionSubject a : taught) {
            exam.getSubjects().add(new ExamSubject(exam, a.getSubject(), new BigDecimal("100"),
                    new BigDecimal("35"), null));
        }
        return exams.save(exam);
    }

    private StudentMark entered(ExamSubject subject, StudentEnrollment enrollment, Section section,
            List<SectionSubject> taught, int percent) {
        java.util.UUID teacher = taught.stream().filter(a -> a.getSubject().getId().equals(subject.getSubject().getId()))
                .map(SectionSubject::getTeacherId).findFirst().orElse(taught.get(0).getTeacherId());
        StudentMark mark = new StudentMark(subject, enrollment.getStudent(), section);
        mark.record(new BigDecimal(percent).setScale(2), false, null, teacher);
        return mark;
    }
}
