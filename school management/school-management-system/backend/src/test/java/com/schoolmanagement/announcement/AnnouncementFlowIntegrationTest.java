package com.schoolmanagement.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.ApiSession;
import com.schoolmanagement.support.ApiSession.Reply;
import com.schoolmanagement.support.PostgresIntegrationTest;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.service.UserProvisioner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Announcements end to end: who may write, who sees what, dates, archiving and read tracking. */
class AnnouncementFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String ADMIN = "/api/v1/admin";
    private static final String FEED = "/api/v1/announcements";
    private static final String MANAGE = "/api/v1/admin/announcements";
    private static final String TEACHER_MANAGE = "/api/v1/teacher/announcements";

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

    private ApiSession admin;
    private ApiSession classTeacher;
    private ApiSession subjectTeacher;
    private ApiSession idleTeacher;
    private ApiSession ann;
    private ApiSession cat;
    private UUID sectionA;
    private UUID sectionB;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table announcement_reads, announcements, exam_results, exam_comments, student_marks, "
                + "exam_subjects, exams, grade_rules, submission_attachments, assignment_submissions, "
                + "assignment_attachments, assignments, attendance_corrections, attendance_records, "
                + "attendance_sessions, timetable_entries, student_enrollments, promotion_batches, "
                + "student_guardians, guardians, student_profiles, teacher_profiles, section_subjects, sections, "
                + "subjects, grade_levels, academic_years, refresh_tokens, password_reset_tokens, user_roles, users, "
                + "schools cascade");
        UUID schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        UUID classTeacherId = provisioner.createActiveUser(schoolId, "T-1", "t1@test.school", PASSWORD, "Milo",
                "Math", RoleName.ROLE_TEACHER).getId();
        UUID subjectTeacherId = provisioner.createActiveUser(schoolId, "T-2", "t2@test.school", PASSWORD, "Edna",
                "English", RoleName.ROLE_TEACHER).getId();
        provisioner.createActiveUser(schoolId, "T-3", "t3@test.school", PASSWORD, "Ivy", "Idle",
                RoleName.ROLE_TEACHER);
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID year = id(ok(admin.post(ADMIN + "/academic-years", map("name", "2026-2027", "startDate",
                today.minusDays(60).toString(), "endDate", today.plusDays(120).toString())), 201));
        ok(admin.post(ADMIN + "/academic-years/" + year + "/activate", null), 200);
        UUID grade = id(ok(admin.post(ADMIN + "/grade-levels", map("name", "Grade 8", "displayOrder", 8, "active",
                true)), 201));
        UUID english = id(ok(admin.post(ADMIN + "/subjects", map("code", "ENG", "name", "English", "description", "",
                "active", true)), 201));
        sectionA = id(ok(admin.post(ADMIN + "/sections", map("academicYearId", year, "gradeLevelId", grade, "name",
                "A", "capacity", 40, "classTeacherId", classTeacherId, "active", true)), 201));
        sectionB = id(ok(admin.post(ADMIN + "/sections", map("academicYearId", year, "gradeLevelId", grade, "name",
                "B", "capacity", 40, "classTeacherId", null, "active", true)), 201));
        ok(admin.post(ADMIN + "/section-subjects", map("sectionId", sectionA, "subjectId", english, "teacherId",
                subjectTeacherId, "weeklyPeriods", 5, "active", true)), 201);
        enrolled("S-1", "Ann", sectionA);
        enrolled("S-3", "Cat", sectionB);
        classTeacher = ApiSession.login(mockMvc, objectMapper, "t1@test.school", PASSWORD);
        subjectTeacher = ApiSession.login(mockMvc, objectMapper, "t2@test.school", PASSWORD);
        idleTeacher = ApiSession.login(mockMvc, objectMapper, "t3@test.school", PASSWORD);
        ann = ApiSession.login(mockMvc, objectMapper, "S-1", PASSWORD);
        cat = ApiSession.login(mockMvc, objectMapper, "S-3", PASSWORD);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private JsonNode ok(Reply reply, int expectedStatus) {
        assertThat(reply.status()).as(reply.raw()).isEqualTo(expectedStatus);
        return reply.body();
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    private void enrolled(String admission, String first, UUID section) throws Exception {
        UUID student = id(ok(admin.post(ADMIN + "/students", map("admissionNumber", admission, "password", PASSWORD,
                "firstName", first, "lastName", "Pupil", "email", admission.toLowerCase() + "@test.school",
                "dateOfBirth", "2012-04-10", "gender", "MALE", "admissionDate", "2026-06-01", "addressLine1",
                "1 Main Road", "city", "Pune", "emergencyContactName", "Parent", "emergencyContactPhone",
                "+91 98765 00000")), 201));
        ok(admin.post(ADMIN + "/enrollments", map("studentId", student, "sectionId", section)), 201);
    }

    private Map<String, Object> body(String title, String audience, UUID section) {
        return map("title", title, "body", "Please read this carefully.", "audience", audience, "sectionId", section,
                "publishAt", null, "expiresAt", null);
    }

    private UUID published(ApiSession author, String path, String title, String audience, UUID section)
            throws Exception {
        UUID id = id(ok(author.post(path, body(title, audience, section)), 201));
        ok(author.post(path + "/" + id + "/publish", null), 200);
        return id;
    }

    private List<String> titles(ApiSession session) throws Exception {
        List<String> titles = new ArrayList<>();
        ok(session.get(FEED + "?size=100"), 200).get("content").forEach(a -> titles.add(a.get("title").asText()));
        return titles;
    }

    // ---- audiences -------------------------------------------------------------------------------------

    @Test
    void everyoneSeesOnlyTheAnnouncementsMeantForThem() throws Exception {
        published(admin, MANAGE, "For all", "ALL", null);
        published(admin, MANAGE, "For admins", "ADMINS", null);
        published(admin, MANAGE, "For teachers", "TEACHERS", null);
        published(admin, MANAGE, "For students", "STUDENTS", null);
        published(admin, MANAGE, "For class A", "SECTION", sectionA);
        published(admin, MANAGE, "For class B", "SECTION", sectionB);

        assertThat(titles(admin)).containsExactlyInAnyOrder("For all", "For admins");
        assertThat(titles(classTeacher)).containsExactlyInAnyOrder("For all", "For teachers", "For class A");
        assertThat(titles(subjectTeacher)).as("a subject teacher of A").containsExactlyInAnyOrder("For all",
                "For teachers", "For class A");
        assertThat(titles(idleTeacher)).containsExactlyInAnyOrder("For all", "For teachers");
        assertThat(titles(ann)).containsExactlyInAnyOrder("For all", "For students", "For class A");
        assertThat(titles(cat)).containsExactlyInAnyOrder("For all", "For students", "For class B");

        JsonNode adminsOnly = ok(admin.get(MANAGE + "?audience=ADMINS"), 200).get("content").get(0);
        String id = adminsOnly.get("id").asText();
        assertThat(ann.get(FEED + "/" + id).status()).as("not for students").isEqualTo(404);
        assertThat(classTeacher.get(FEED + "/" + id).status()).isEqualTo(404);
        assertThat(admin.get(FEED + "/" + id).status()).isEqualTo(200);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper).get(FEED).status()).isEqualTo(401);
    }

    @Test
    void draftsScheduledExpiredAndArchivedAnnouncementsAreHidden() throws Exception {
        UUID draft = id(ok(admin.post(MANAGE, body("Draft", "ALL", null)), 201));
        Map<String, Object> scheduled = body("Scheduled", "ALL", null);
        scheduled.put("publishAt", Instant.now().plus(Duration.ofDays(2)).toString());
        UUID later = id(ok(admin.post(MANAGE, scheduled), 201));
        ok(admin.post(MANAGE + "/" + later + "/publish", null), 200);
        UUID expiring = published(admin, MANAGE, "Expiring", "ALL", null);
        UUID archived = published(admin, MANAGE, "Archived", "ALL", null);
        UUID visible = published(admin, MANAGE, "Visible", "ALL", null);

        assertThat(titles(ann)).containsExactlyInAnyOrder("Expiring", "Archived", "Visible");
        ok(admin.post(MANAGE + "/" + archived + "/archive", null), 200);
        jdbc.update("update announcements set expires_at = now() - interval '1 minute' where id = ?", expiring);
        assertThat(titles(ann)).containsExactly("Visible");
        assertThat(ann.get(FEED + "/" + draft).status()).isEqualTo(404);
        assertThat(ann.get(FEED + "/" + later).status()).isEqualTo(404);
        assertThat(ann.get(FEED + "/" + expiring).status()).isEqualTo(404);
        assertThat(ann.get(FEED + "/" + visible).status()).isEqualTo(200);

        JsonNode managed = ok(admin.get(MANAGE + "/" + later), 200);
        assertThat(managed.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(managed.get("live").asBoolean()).as("published but not started yet").isFalse();

        jdbc.update("update announcements set publish_at = now() - interval '1 minute' where id = ?", later);
        assertThat(titles(ann)).containsExactlyInAnyOrder("Scheduled", "Visible");
    }

    @Test
    void windowsAreValidatedAndAnExpiredDraftCannotBePublished() throws Exception {
        Map<String, Object> backwards = body("Backwards", "ALL", null);
        backwards.put("publishAt", Instant.now().plus(Duration.ofDays(3)).toString());
        backwards.put("expiresAt", Instant.now().plus(Duration.ofDays(2)).toString());
        assertThat(admin.post(MANAGE, backwards).status()).isEqualTo(400);

        Map<String, Object> past = body("Past", "ALL", null);
        past.put("expiresAt", Instant.now().minus(Duration.ofDays(1)).toString());
        UUID id = id(ok(admin.post(MANAGE, past), 201));
        Reply publish = admin.post(MANAGE + "/" + id + "/publish", null);
        assertThat(publish.status()).isEqualTo(422);
        assertThat(publish.detail()).contains("expired");

        Map<String, Object> blank = body("Blank", "ALL", null);
        blank.put("title", " ");
        assertThat(admin.post(MANAGE, blank).status()).isEqualTo(400);
        assertThat(admin.post(MANAGE, body("Needs class", "SECTION", null)).status()).isEqualTo(400);
        assertThat(admin.post(MANAGE, body("Class on ALL", "ALL", sectionA)).status()).isEqualTo(400);
        assertThat(admin.post(MANAGE, map("title", "x", "body", "y", "audience", "EVERYONE")).status())
                .isEqualTo(400);
    }

    // ---- reading and read tracking --------------------------------------------------------------------

    @Test
    void readingIsTrackedPerPersonAndMarkingItTwiceIsHarmless() throws Exception {
        UUID first = published(admin, MANAGE, "First", "STUDENTS", null);
        UUID second = published(admin, MANAGE, "Second", "STUDENTS", null);
        UUID teachersOnly = published(admin, MANAGE, "Teachers", "TEACHERS", null);

        assertThat(ok(ann.get(FEED + "/unread-count"), 200).get("count").asInt()).isEqualTo(2);
        assertThat(ok(ann.get(FEED + "/" + first), 200).get("read").asBoolean()).isFalse();
        assertThat(ann.post(FEED + "/" + first + "/read", null).status()).isEqualTo(204);
        assertThat(ann.post(FEED + "/" + first + "/read", null).status()).isEqualTo(204);

        assertThat(ok(ann.get(FEED + "/unread-count"), 200).get("count").asInt()).isEqualTo(1);
        assertThat(ok(cat.get(FEED + "/unread-count"), 200).get("count").asInt()).as("reading is per person")
                .isEqualTo(2);
        JsonNode unread = ok(ann.get(FEED + "?unread=true"), 200).get("content");
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).get("id").asText()).isEqualTo(second.toString());
        assertThat(ok(ann.get(FEED), 200).get("content").findValuesAsText("read")).contains("true", "false");
        assertThat(jdbc.queryForObject("select count(*) from announcement_reads", Integer.class)).isEqualTo(1);

        ok(cat.post(FEED + "/" + first + "/read", null), 204);
        assertThat(ok(admin.get(MANAGE + "/" + first), 200).get("readCount").asInt()).isEqualTo(2);
        assertThat(ok(ann.get(FEED), 200).get("content").get(0).get("readCount").isNull()).as("readers see no counts")
                .isTrue();
        assertThat(ann.post(FEED + "/" + teachersOnly + "/read", null).status()).as("cannot read what is not theirs")
                .isEqualTo(404);
    }

    // ---- authoring -------------------------------------------------------------------------------------

    @Test
    void teachersWriteOnlyToTheirOwnClassesAndSeeOnlyTheirOwnAnnouncements() throws Exception {
        UUID byClassTeacher = published(classTeacher, TEACHER_MANAGE, "Trip tomorrow", "SECTION", sectionA);
        UUID bySubjectTeacher = published(subjectTeacher, TEACHER_MANAGE, "Reading homework", "SECTION", sectionA);

        assertThat(titles(ann)).containsExactlyInAnyOrder("Trip tomorrow", "Reading homework");
        assertThat(titles(cat)).isEmpty();
        assertThat(subjectTeacher.post(TEACHER_MANAGE, body("Not mine", "SECTION", sectionB)).status())
                .as("a class the teacher does not teach").isEqualTo(403);
        assertThat(idleTeacher.post(TEACHER_MANAGE, body("Not mine", "SECTION", sectionA)).status()).isEqualTo(403);
        assertThat(classTeacher.post(TEACHER_MANAGE, body("Everyone", "ALL", null)).status())
                .as("teachers cannot address the whole school").isEqualTo(403);
        assertThat(classTeacher.post(TEACHER_MANAGE, body("Staff", "TEACHERS", null)).status()).isEqualTo(403);

        JsonNode mine = ok(classTeacher.get(TEACHER_MANAGE), 200).get("content");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("authorName").asText()).isEqualTo("Milo Math");
        assertThat(classTeacher.get(TEACHER_MANAGE + "/" + bySubjectTeacher).status()).as("another teacher's")
                .isEqualTo(404);
        assertThat(classTeacher.post(TEACHER_MANAGE + "/" + bySubjectTeacher + "/archive", null).status())
                .isEqualTo(404);
        assertThat(ok(admin.get(MANAGE), 200).get("totalElements").asInt()).as("administrators see all")
                .isEqualTo(2);
        assertThat(ann.get(MANAGE).status()).isEqualTo(403);
        assertThat(classTeacher.get(MANAGE).status()).as("the admin path is for administrators").isEqualTo(403);
        assertThat(admin.get(TEACHER_MANAGE).status()).isEqualTo(403);
        assertThat(byClassTeacher).isNotNull();
    }

    @Test
    void lifecycleRulesProtectWhatReadersHaveAlreadySeen() throws Exception {
        JsonNode draft = ok(admin.post(MANAGE, body("Original", "STUDENTS", null)), 201);
        UUID id = id(draft);
        Map<String, Object> edit = body("Edited", "SECTION", sectionA);
        edit.put("version", draft.get("version").asLong());
        JsonNode retargeted = ok(admin.put(MANAGE + "/" + id, edit), 200);
        assertThat(retargeted.get("audience").asText()).as("a draft may change audience").isEqualTo("SECTION");
        assertThat(retargeted.get("sectionName").asText()).isEqualTo("Grade 8 - A");
        edit.put("version", draft.get("version").asLong());
        assertThat(admin.put(MANAGE + "/" + id, edit).status()).as("stale version").isEqualTo(409);

        JsonNode live = ok(admin.post(MANAGE + "/" + id + "/publish", null), 200);
        assertThat(admin.post(MANAGE + "/" + id + "/publish", null).status()).isEqualTo(422);
        assertThat(admin.delete(MANAGE + "/" + id).status()).as("published ones are archived, not deleted")
                .isEqualTo(422);
        Map<String, Object> widen = body("Edited", "ALL", null);
        widen.put("version", live.get("version").asLong());
        assertThat(admin.put(MANAGE + "/" + id, widen).status()).as("audience is fixed once published")
                .isEqualTo(422);
        Map<String, Object> reword = body("Reworded", "SECTION", sectionA);
        reword.put("version", live.get("version").asLong());
        assertThat(ok(admin.put(MANAGE + "/" + id, reword), 200).get("title").asText()).isEqualTo("Reworded");

        JsonNode archived = ok(admin.post(MANAGE + "/" + id + "/archive", null), 200);
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(admin.post(MANAGE + "/" + id + "/archive", null).status()).isEqualTo(422);
        reword.put("version", archived.get("version").asLong());
        assertThat(admin.put(MANAGE + "/" + id, reword).status()).isEqualTo(422);

        UUID other = id(ok(admin.post(MANAGE, body("To delete", "ALL", null)), 201));
        assertThat(admin.delete(MANAGE + "/" + other).status()).isEqualTo(204);
        assertThat(admin.get(MANAGE + "/" + other).status()).isEqualTo(404);
        assertThat(admin.delete(ADMIN + "/sections/" + sectionA).status()).as("class has announcements")
                .isEqualTo(409);
    }
}
