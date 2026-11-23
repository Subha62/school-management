package com.schoolmanagement.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.repository.UserRepository;
import com.schoolmanagement.user.service.UserProvisioner;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class BootstrapSafetyTest {

    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserProvisioner provisioner = mock(UserProvisioner.class);

    private void verifyNoAccountCreated() {
        verify(provisioner, never()).createActiveUser(any(), any(), any(), any(), any(), any(), any());
    }

    private void verifyCreated(RoleName role) {
        verify(provisioner).createActiveUser(any(), any(), any(), any(), any(), any(), eq(role));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://db.prod.example:5432/school", "jdbc:postgresql://10.0.0.5/school"})
    void demoAccountsAreNeverSeededIntoANonLocalDatabase(String url) {
        new DevDataSeeder(schools, provisioner, url, "Demo@Pass2026!").run(null);

        verifyNoAccountCreated();
    }

    @Test
    void demoAccountsAreNotSeededWithoutAConfiguredPassword() {
        new DevDataSeeder(schools, provisioner, "jdbc:postgresql://localhost:5432/school", "").run(null);

        verifyNoAccountCreated();
    }

    @Test
    void demoAccountsAreSeededIntoALocalDatabase() {
        School school = new School("Demo School", "DEMO", "UTC");
        ReflectionTestUtils.setField(school, "id", UUID.randomUUID());
        when(schools.findBySchoolCode("DEMO")).thenReturn(Optional.empty());
        when(schools.save(any())).thenReturn(school);

        new DevDataSeeder(schools, provisioner, "jdbc:postgresql://localhost:5432/school", "Demo@Pass2026!")
                .run(null);

        verifyCreated(RoleName.ROLE_ADMIN);
        verifyCreated(RoleName.ROLE_TEACHER);
        verifyCreated(RoleName.ROLE_STUDENT);
    }

    @Test
    void demoAccountsAreNotDuplicatedOnRestart() {
        when(schools.findBySchoolCode("DEMO")).thenReturn(Optional.of(new School("Demo School", "DEMO", "UTC")));

        new DevDataSeeder(schools, provisioner, "jdbc:postgresql://localhost:5432/school", "Demo@Pass2026!")
                .run(null);

        verifyNoAccountCreated();
    }

    @Test
    void demoAcademicDataIsNeverSeededIntoANonLocalDatabase() {
        var years = mock(com.schoolmanagement.academic.repository.AcademicYearRepository.class);
        var sectionsRepo = mock(com.schoolmanagement.academic.repository.SectionRepository.class);

        new DevAcademicSeeder(schools, users, years, mock(com.schoolmanagement.academic.repository.GradeLevelRepository.class),
                mock(com.schoolmanagement.academic.repository.SubjectRepository.class), sectionsRepo,
                mock(com.schoolmanagement.academic.repository.SectionSubjectRepository.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class),
                java.time.Clock.systemUTC(), "jdbc:postgresql://db.prod.example/school", "Demo@Pass2026!").run(null);

        verify(schools, never()).findBySchoolCode(any());
        verify(years, never()).save(any());
        verify(sectionsRepo, never()).save(any());
    }

    @Test
    void demoPeopleAreNeverSeededIntoANonLocalDatabase() {
        var students = mock(com.schoolmanagement.student.repository.StudentProfileRepository.class);
        var teachers = mock(com.schoolmanagement.teacher.repository.TeacherProfileRepository.class);

        new DevPeopleSeeder(schools, users, teachers, students,
                mock(com.schoolmanagement.guardian.repository.GuardianRepository.class),
                mock(com.schoolmanagement.student.repository.StudentGuardianRepository.class),
                mock(com.schoolmanagement.academic.repository.SectionRepository.class),
                mock(com.schoolmanagement.student.repository.StudentEnrollmentRepository.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class),
                java.time.Clock.systemUTC(), "jdbc:postgresql://db.prod.example/school", "Demo@Pass2026!").run(null);

        verify(schools, never()).findBySchoolCode(any());
        verify(students, never()).save(any());
        verify(teachers, never()).save(any());
    }

    @Test
    void demoOperationsDataIsNeverSeededIntoANonLocalDatabase() {
        var timetable = mock(com.schoolmanagement.timetable.repository.TimetableEntryRepository.class);
        var sessions = mock(com.schoolmanagement.attendance.repository.AttendanceSessionRepository.class);

        new DevOperationsSeeder(schools, mock(com.schoolmanagement.academic.repository.SectionRepository.class),
                mock(com.schoolmanagement.academic.repository.SectionSubjectRepository.class), timetable, sessions,
                mock(com.schoolmanagement.attendance.repository.AttendanceRecordRepository.class),
                mock(com.schoolmanagement.student.repository.StudentEnrollmentRepository.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class),
                java.time.Clock.systemUTC(), "jdbc:postgresql://db.prod.example/school", "Demo@Pass2026!").run(null);

        verify(schools, never()).findBySchoolCode(any());
        verify(timetable, never()).save(any());
        verify(sessions, never()).save(any());
    }

    @Test
    void demoAssignmentsAreNeverSeededIntoANonLocalDatabase() {
        var assignments = mock(com.schoolmanagement.assignment.repository.AssignmentRepository.class);
        var submissions = mock(com.schoolmanagement.assignment.repository.SubmissionRepository.class);

        new DevAssignmentSeeder(schools, mock(com.schoolmanagement.academic.repository.SectionRepository.class),
                mock(com.schoolmanagement.academic.repository.SectionSubjectRepository.class), assignments,
                submissions, mock(com.schoolmanagement.student.repository.StudentEnrollmentRepository.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class),
                java.time.Clock.systemUTC(), "jdbc:postgresql://db.prod.example/school", "Demo@Pass2026!").run(null);

        verify(schools, never()).findBySchoolCode(any());
        verify(assignments, never()).save(any());
        verify(submissions, never()).save(any());
    }

    @Test
    void demoExamsAreNeverSeededIntoANonLocalDatabase() {
        var exams = mock(com.schoolmanagement.exam.repository.ExamRepository.class);
        var gradeRules = mock(com.schoolmanagement.exam.repository.GradeRuleRepository.class);

        new DevExamSeeder(schools, mock(com.schoolmanagement.academic.repository.SectionRepository.class),
                mock(com.schoolmanagement.academic.repository.SectionSubjectRepository.class),
                mock(com.schoolmanagement.student.repository.StudentEnrollmentRepository.class), gradeRules, exams,
                mock(com.schoolmanagement.exam.repository.StudentMarkRepository.class),
                mock(com.schoolmanagement.exam.repository.ExamCommentRepository.class), users,
                mock(com.schoolmanagement.exam.service.ResultService.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class), java.time.Clock.systemUTC(),
                "jdbc:postgresql://db.prod.example/school", "Demo@Pass2026!").run(null);

        verify(schools, never()).findBySchoolCode(any());
        verify(exams, never()).save(any());
        verify(gradeRules, never()).save(any());
    }

    @Test
    void demoCommunicationAndFeesAreNeverSeededIntoANonLocalDatabase() {
        var announcements = mock(com.schoolmanagement.announcement.repository.AnnouncementRepository.class);
        var categories = mock(com.schoolmanagement.fee.repository.FeeCategoryRepository.class);

        new DevCommunicationSeeder(schools, mock(com.schoolmanagement.academic.repository.SectionRepository.class),
                users, announcements, mock(com.schoolmanagement.announcement.service.AnnouncementService.class),
                categories, mock(com.schoolmanagement.fee.service.FeeConfigService.class),
                mock(com.schoolmanagement.fee.service.StudentFeeService.class),
                mock(com.schoolmanagement.fee.service.PaymentService.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class), java.time.Clock.systemUTC(),
                "jdbc:postgresql://db.prod.example/school", "Demo@Pass2026!").run(null);

        verify(schools, never()).findBySchoolCode(any());
        verify(announcements, never()).save(any());
        verify(categories, never()).save(any());
    }

    @Test
    void initialAdminIsNotCreatedUnlessBothVariablesAreSet() {
        new InitialAdminBootstrap(schools, users, provisioner, "admin@school.example", "", "S", "C").run(null);
        new InitialAdminBootstrap(schools, users, provisioner, "", "Str0ng-Passw0rd!", "S", "C").run(null);

        verifyNoAccountCreated();
    }

    @Test
    void initialAdminIsSkippedWhenAnActiveAdministratorAlreadyExists() {
        when(users.existsActiveWithRole(RoleName.ROLE_ADMIN)).thenReturn(true);

        new InitialAdminBootstrap(schools, users, provisioner, "admin@school.example", "Str0ng-Passw0rd!", "S", "C")
                .run(null);

        verifyNoAccountCreated();
    }

    @Test
    void initialAdminPasswordMustMeetThePolicyAndIsNeverEchoedInTheError() {
        assertThatThrownBy(() -> new InitialAdminBootstrap(schools, users, provisioner, "admin@school.example",
                "weakpw", "S", "C").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("weakpw");
        verifyNoAccountCreated();
    }

    @Test
    void initialAdminIsCreatedWhenConfiguredAndNoAdminExists() {
        School school = new School("S", "C", "UTC");
        ReflectionTestUtils.setField(school, "id", UUID.randomUUID());
        when(schools.findBySchoolCode("C")).thenReturn(Optional.empty());
        when(schools.save(any())).thenReturn(school);

        new InitialAdminBootstrap(schools, users, provisioner, " admin@school.example ", "Str0ng-Passw0rd!", "S", "C")
                .run(null);

        verify(provisioner).createActiveUser(any(), any(), eq("admin@school.example"), eq("Str0ng-Passw0rd!"), any(),
                any(), eq(RoleName.ROLE_ADMIN));
    }
}
