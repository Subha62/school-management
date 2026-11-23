package com.schoolmanagement.dashboard;

import static com.schoolmanagement.support.SchoolWorld.id;
import static com.schoolmanagement.support.SchoolWorld.map;
import static com.schoolmanagement.support.SchoolWorld.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.ApiSession;
import com.schoolmanagement.support.PostgresIntegrationTest;
import com.schoolmanagement.support.SchoolWorld;
import com.schoolmanagement.user.service.UserProvisioner;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The dashboards: numbers come from the real data, are scoped to the caller, and each role gets only its own. */
class DashboardIntegrationTest extends PostgresIntegrationTest {

    private static final String ADMIN = "/api/v1/admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SchoolRepository schools;
    @Autowired
    private UserProvisioner provisioner;

    private SchoolWorld world;

    @BeforeEach
    void setUp() throws Exception {
        SchoolWorld.wipe(jdbc);
        world = new SchoolWorld(mockMvc, objectMapper).build(schools, provisioner, "Test School", "a");
    }

    @Test
    void anEmptyishSchoolStillGetsAnswersEverywhere() throws Exception {
        JsonNode admin = ok(world.admin.get(ADMIN + "/dashboard"), 200);
        assertThat(admin.get("academicYearName").asText()).isEqualTo("2026-2027");
        assertThat(admin.get("activeStudents").asInt()).isEqualTo(1);
        assertThat(admin.get("activeTeachers").asInt()).as("teacher accounts without a profile are not counted")
                .isZero();
        assertThat(admin.get("activeClasses").asInt()).isEqualTo(1);
        assertThat(admin.get("attendanceToday").get("registersOpen").asInt()).isZero();
        assertThat(admin.get("exams").get("openForMarks").asInt()).isZero();
        assertThat(admin.get("fees").get("billed").decimalValue()).isEqualByComparingTo("0");
        assertThat(admin.get("recentActivity")).isNotEmpty();

        JsonNode teacher = ok(world.teacher.get("/api/v1/teacher/dashboard"), 200);
        assertThat(teacher.get("todaySlots")).isEmpty();
        assertThat(teacher.get("classesTaught").asInt()).isEqualTo(1);
        assertThat(teacher.get("awaitingReview").asInt()).isZero();
        assertThat(teacher.get("registersToTake")).hasSize(1);
        assertThat(teacher.get("registersToTake").get(0).asText()).isEqualTo("Grade 8 - A");

        JsonNode student = ok(world.student.get("/api/v1/student/dashboard"), 200);
        assertThat(student.get("className").asText()).isEqualTo("Grade 8 - A");
        assertThat(student.get("todaySlots")).isEmpty();
        assertThat(student.get("attendancePercent").isNull()).isTrue();
        assertThat(student.get("upcomingWork")).isEmpty();
        assertThat(student.get("latestResult").isNull()).isTrue();
        assertThat(student.get("fees").get("outstanding").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void adminNumbersFollowExamsFeesAnnouncementsAndRegisters() throws Exception {
        UUID tuition = id(ok(world.admin.post(ADMIN + "/fee-categories", map("name", "Tuition", "active", true)),
                201));
        UUID structure = id(ok(world.admin.post(ADMIN + "/fee-structures", map("academicYearId", world.year,
                "gradeLevelId", world.grade, "feeCategoryId", tuition, "amount", new BigDecimal("1000"), "dueDate",
                LocalDate.now(ZoneOffset.UTC).minusDays(3).toString(), "active", true)), 201));
        ok(world.admin.post(ADMIN + "/fee-structures/" + structure + "/generate", null), 200);
        UUID exam = id(ok(world.admin.post(ADMIN + "/exams", map("academicYearId", world.year, "gradeLevelId",
                world.grade, "name", "Term 1", "subjects", java.util.List.of(map("subjectId", world.math,
                        "maxMarks", new BigDecimal("100"), "passMarks", new BigDecimal("35"))))), 201));
        ok(world.admin.post(ADMIN + "/exams/" + exam + "/open", null), 200);
        UUID notice = id(ok(world.admin.post(ADMIN + "/announcements", map("title", "Hello", "body", "Welcome",
                "audience", "ALL")), 201));
        ok(world.admin.post(ADMIN + "/announcements/" + notice + "/publish", null), 200);
        ok(world.teacher.post("/api/v1/teacher/attendance/sessions", map("sectionId", world.section, "date",
                LocalDate.now(ZoneOffset.UTC).toString())), 201);

        JsonNode admin = ok(world.admin.get(ADMIN + "/dashboard"), 200);
        assertThat(admin.get("fees").get("billed").decimalValue()).isEqualByComparingTo("1000");
        assertThat(admin.get("fees").get("overdue").decimalValue()).isEqualByComparingTo("1000");
        assertThat(admin.get("fees").get("overdueFees").asInt()).isEqualTo(1);
        assertThat(admin.get("exams").get("openForMarks").asInt()).isEqualTo(1);
        assertThat(admin.get("liveAnnouncements").asInt()).isEqualTo(1);
        assertThat(admin.get("attendanceToday").get("registersOpen").asInt()).isEqualTo(1);
        assertThat(admin.get("recentActivity").get(0).get("actorName").asText()).isNotBlank();

        JsonNode teacher = ok(world.teacher.get("/api/v1/teacher/dashboard"), 200);
        assertThat(teacher.get("registersToTake")).as("today's register is already open").isEmpty();
        assertThat(teacher.get("marksMissing").asInt()).as("one student, one subject").isEqualTo(1);
        assertThat(teacher.get("unreadAnnouncements").asInt()).isEqualTo(1);
    }

    @Test
    void aStudentSeesTheirOwnWorkFeesAndResults() throws Exception {
        UUID assignment = id(ok(world.teacher.post("/api/v1/teacher/assignments", map("sectionId", world.section,
                "subjectId", world.math, "title", "Worksheet 1", "description", "Do it", "dueAt",
                Instant.now().plus(Duration.ofDays(3)).toString(), "maxMarks", new BigDecimal("10"), "allowLate",
                false)), 201));
        ok(world.teacher.post("/api/v1/teacher/assignments/" + assignment + "/publish", null), 200);
        UUID tuition = id(ok(world.admin.post(ADMIN + "/fee-categories", map("name", "Tuition", "active", true)),
                201));
        UUID structure = id(ok(world.admin.post(ADMIN + "/fee-structures", map("academicYearId", world.year,
                "gradeLevelId", world.grade, "feeCategoryId", tuition, "amount", new BigDecimal("500"), "dueDate",
                LocalDate.now(ZoneOffset.UTC).plusDays(10).toString(), "active", true)), 201));
        ok(world.admin.post(ADMIN + "/fee-structures/" + structure + "/generate", null), 200);

        JsonNode student = ok(world.student.get("/api/v1/student/dashboard"), 200);
        assertThat(student.get("upcomingWork")).hasSize(1);
        assertThat(student.get("upcomingWork").get(0).get("title").asText()).isEqualTo("Worksheet 1");
        assertThat(student.get("fees").get("outstanding").decimalValue()).isEqualByComparingTo("500");
        assertThat(student.get("fees").get("overdueFees").asInt()).isZero();
        assertThat(student.get("fees").get("nextDueDate").asText())
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(10).toString());

        ok(world.student.post("/api/v1/announcements/" + publishedNotice() + "/read", null), 204);
        assertThat(ok(world.student.get("/api/v1/student/dashboard"), 200).get("unreadAnnouncements").asInt())
                .isZero();
    }

    private UUID publishedNotice() throws Exception {
        UUID notice = id(ok(world.admin.post(ADMIN + "/announcements", map("title", "Hi", "body", "All", "audience",
                "STUDENTS")), 201));
        ok(world.admin.post(ADMIN + "/announcements/" + notice + "/publish", null), 200);
        return notice;
    }

    @Test
    void eachRoleReachesOnlyItsOwnDashboardAndAnonymousNobody() throws Exception {
        assertThat(world.teacher.get(ADMIN + "/dashboard").status()).isEqualTo(403);
        assertThat(world.student.get(ADMIN + "/dashboard").status()).isEqualTo(403);
        assertThat(world.admin.get("/api/v1/teacher/dashboard").status()).isEqualTo(403);
        assertThat(world.student.get("/api/v1/teacher/dashboard").status()).isEqualTo(403);
        assertThat(world.teacher.get("/api/v1/student/dashboard").status()).isEqualTo(403);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper).get("/api/v1/student/dashboard").status())
                .isEqualTo(401);
    }

    @Test
    void anotherSchoolsDataNeverLeaksIntoTheNumbers() throws Exception {
        SchoolWorld other = new SchoolWorld(mockMvc, objectMapper).build(schools, provisioner, "Other School", "b");
        ok(other.admin.post(ADMIN + "/fee-categories", map("name", "Tuition", "active", true)), 201);

        JsonNode admin = ok(world.admin.get(ADMIN + "/dashboard"), 200);
        assertThat(admin.get("activeStudents").asInt()).isEqualTo(1);
        assertThat(admin.get("recentActivity").toString()).doesNotContain("Tuition");
    }
}
