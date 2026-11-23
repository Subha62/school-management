package com.schoolmanagement.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.file.UploadService;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.ApiSession;
import com.schoolmanagement.support.ApiSession.Binary;
import com.schoolmanagement.support.ApiSession.Reply;
import com.schoolmanagement.support.PostgresIntegrationTest;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.service.UserProvisioner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Assignments end to end: authoring, files, submissions and review through the real security chain and schema. */
class AssignmentFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String ADMIN = "/api/v1/admin";
    private static final String T = "/api/v1/teacher/assignments";
    private static final String S = "/api/v1/student/assignments";
    private static final String KEY_PATTERN = "^[0-9a-f]{2}/[0-9a-f-]{36}\\.[a-z]+$";

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
    @Autowired
    private UploadService uploadService;
    @Autowired
    private TransactionTemplate transaction;
    @Value("${app.uploads.local-path}")
    private String uploadRoot;

    private ApiSession admin;
    private ApiSession mathTeacher;
    private ApiSession englishTeacher;
    private ApiSession ann;
    private ApiSession bob;
    private ApiSession cat;
    private UUID sectionA;
    private UUID sectionB;
    private UUID mathId;
    private UUID englishId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table submission_attachments, assignment_submissions, assignment_attachments, "
                + "assignments, attendance_corrections, attendance_records, attendance_sessions, "
                + "timetable_entries, student_enrollments, promotion_batches, student_guardians, guardians, "
                + "student_profiles, teacher_profiles, section_subjects, sections, subjects, grade_levels, "
                + "academic_years, refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
        UUID schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        UUID mathTeacherId = provisioner.createActiveUser(schoolId, "T-1", "t1@test.school", PASSWORD, "Milo",
                "Math", RoleName.ROLE_TEACHER).getId();
        UUID englishTeacherId = provisioner.createActiveUser(schoolId, "T-2", "t2@test.school", PASSWORD, "Edna",
                "English", RoleName.ROLE_TEACHER).getId();
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID year = id(ok(admin.post(ADMIN + "/academic-years", map("name", "2026-2027", "startDate",
                today.minusDays(60).toString(), "endDate", today.plusDays(120).toString())), 201));
        ok(admin.post(ADMIN + "/academic-years/" + year + "/activate", null), 200);
        UUID grade = id(ok(admin.post(ADMIN + "/grade-levels", map("name", "Grade 8", "displayOrder", 8, "active",
                true)), 201));
        mathId = id(ok(admin.post(ADMIN + "/subjects", map("code", "MATH", "name", "Mathematics", "description",
                "", "active", true)), 201));
        englishId = id(ok(admin.post(ADMIN + "/subjects", map("code", "ENG", "name", "English", "description",
                "", "active", true)), 201));
        sectionA = section(year, grade, "A");
        sectionB = section(year, grade, "B");
        assign(sectionA, mathId, mathTeacherId);
        assign(sectionA, englishId, englishTeacherId);
        assign(sectionB, mathId, mathTeacherId);

        enrolled("S-1", "Ann", sectionA);
        enrolled("S-2", "Bob", sectionA);
        enrolled("S-3", "Cat", sectionB);
        mathTeacher = ApiSession.login(mockMvc, objectMapper, "t1@test.school", PASSWORD);
        englishTeacher = ApiSession.login(mockMvc, objectMapper, "t2@test.school", PASSWORD);
        ann = ApiSession.login(mockMvc, objectMapper, "S-1", PASSWORD);
        bob = ApiSession.login(mockMvc, objectMapper, "S-2", PASSWORD);
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

    private UUID section(UUID year, UUID grade, String name) throws Exception {
        return id(ok(admin.post(ADMIN + "/sections", map("academicYearId", year, "gradeLevelId", grade, "name",
                name, "capacity", 40, "classTeacherId", null, "active", true)), 201));
    }

    private void assign(UUID section, UUID subject, UUID teacher) throws Exception {
        ok(admin.post(ADMIN + "/section-subjects", map("sectionId", section, "subjectId", subject, "teacherId",
                teacher, "weeklyPeriods", 5, "active", true)), 201);
    }

    private void enrolled(String admission, String first, UUID section) throws Exception {
        UUID student = id(ok(admin.post(ADMIN + "/students", map("admissionNumber", admission, "password", PASSWORD,
                "firstName", first, "lastName", "Pupil", "email", admission.toLowerCase() + "@test.school",
                "dateOfBirth", "2012-04-10", "gender", "MALE", "admissionDate", "2026-06-01", "addressLine1",
                "1 Main Road", "city", "Pune", "emergencyContactName", "Parent", "emergencyContactPhone",
                "+91 98765 00000")), 201));
        ok(admin.post(ADMIN + "/enrollments", map("studentId", student, "sectionId", section)), 201);
    }

    private Map<String, Object> body(UUID section, UUID subject, String title, long dueHours, String max,
            boolean allowLate) {
        return map("sectionId", section, "subjectId", subject, "title", title, "description", "Do it well",
                "dueAt", Instant.now().plus(Duration.ofHours(dueHours)).toString(), "maxMarks", new java.math.BigDecimal(max),
                "allowLate", allowLate);
    }

    private JsonNode draft(String title, long dueHours, String max, boolean allowLate) throws Exception {
        return ok(mathTeacher.post(T, body(sectionA, mathId, title, dueHours, max, allowLate)), 201);
    }

    private UUID published(String title, long dueHours, String max, boolean allowLate) throws Exception {
        UUID assignment = id(draft(title, dueHours, max, allowLate));
        ok(mathTeacher.post(T + "/" + assignment + "/publish", null), 200);
        return assignment;
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("files", name, contentType, content);
    }

    private static MockMultipartFile pdf(String name) {
        return file(name, "application/pdf", "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII));
    }

    private Reply submit(ApiSession student, UUID assignment, String text, MockMultipartFile... files)
            throws Exception {
        Map<String, String> fields = text == null ? Map.of() : Map.of("textAnswer", text);
        return student.multipart(S + "/" + assignment + "/submission", fields, files);
    }

    private void setDue(UUID assignment, Duration fromNow) {
        jdbc.update("update assignments set due_at = ? where id = ?::uuid",
                java.sql.Timestamp.from(Instant.now().plus(fromNow)), assignment.toString());
    }

    private Path stored(String key) {
        return Path.of(uploadRoot).toAbsolutePath().normalize().resolve(key);
    }

    private long filesOnDisk() {
        Path root = Path.of(uploadRoot).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return 0;
        }
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private Map<String, Object> review(String outcome, Object marks, String feedback, long version) {
        return map("outcome", outcome, "marks", marks, "feedback", feedback, "version", version);
    }

    // ---- authoring -------------------------------------------------------------------------------------

    @Test
    void anAssignmentMovesThroughDraftPublishedClosedAndArchived() throws Exception {
        JsonNode created = draft("Fractions", 48, "20", false);
        UUID id = id(created);
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("counts").get("enrolled").asInt()).isEqualTo(2);
        assertThat(created.get("teacherName").asText()).isEqualTo("Milo Math");

        // A draft is invisible to students and other teachers.
        assertThat(ok(ann.get(S), 200).get("totalElements").asInt()).isZero();
        assertThat(ann.get(S + "/" + id).status()).isEqualTo(404);
        assertThat(englishTeacher.get(T + "/" + id).status()).isEqualTo(404);
        assertThat(ok(englishTeacher.get(T), 200).get("totalElements").asInt()).isZero();

        // Optimistic locking on edits.
        Map<String, Object> change = body(sectionA, mathId, "Fractions, revised", 72, "25", true);
        change.put("version", created.get("version").asLong() + 5);
        assertThat(mathTeacher.put(T + "/" + id, change).status()).isEqualTo(409);
        change.put("version", created.get("version").asLong());
        JsonNode updated = ok(mathTeacher.put(T + "/" + id, change), 200);
        assertThat(updated.get("title").asText()).isEqualTo("Fractions, revised");

        JsonNode live = ok(mathTeacher.post(T + "/" + id + "/publish", null), 200);
        assertThat(live.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(mathTeacher.post(T + "/" + id + "/publish", null).status()).isEqualTo(422);
        assertThat(ok(ann.get(S), 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(ok(ann.get(S + "/" + id), 200).get("canSubmit").asBoolean()).isTrue();
        // Another class never sees it.
        assertThat(ok(cat.get(S), 200).get("totalElements").asInt()).isZero();
        assertThat(cat.get(S + "/" + id).status()).isEqualTo(404);

        assertThat(mathTeacher.delete(T + "/" + id).status()).isEqualTo(422);
        assertThat(mathTeacher.post(T + "/" + id + "/archive", null).status()).isEqualTo(422);
        assertThat(ok(mathTeacher.post(T + "/" + id + "/close", null), 200).get("status").asText())
                .isEqualTo("CLOSED");
        assertThat(ok(ann.get(S + "/" + id), 200).get("canSubmit").asBoolean()).isFalse();
        assertThat(ok(mathTeacher.post(T + "/" + id + "/reopen", null), 200).get("status").asText())
                .isEqualTo("PUBLISHED");
        ok(mathTeacher.post(T + "/" + id + "/close", null), 200);
        assertThat(ok(mathTeacher.post(T + "/" + id + "/archive", null), 200).get("status").asText())
                .isEqualTo("ARCHIVED");

        // Archived work leaves the default list and students' view, and can no longer be edited.
        assertThat(ok(mathTeacher.get(T), 200).get("totalElements").asInt()).isZero();
        assertThat(ok(mathTeacher.get(T + "?status=ARCHIVED"), 200).get("totalElements").asInt()).isEqualTo(1);
        assertThat(ok(ann.get(S), 200).get("totalElements").asInt()).isZero();
        change.put("version", ok(mathTeacher.get(T + "/" + id), 200).get("version").asLong());
        assertThat(mathTeacher.put(T + "/" + id, change).status()).isEqualTo(422);
    }

    @Test
    void creationRulesAreEnforced() throws Exception {
        Map<String, Object> past = body(sectionA, mathId, "Late already", -1, "10", false);
        assertThat(mathTeacher.post(T, past).status()).isEqualTo(400);
        assertThat(mathTeacher.post(T, body(sectionA, mathId, "Zero", 24, "0", false)).status()).isEqualTo(400);
        assertThat(mathTeacher.post(T, body(sectionA, mathId, "Huge", 24, "1000.01", false)).status())
                .isEqualTo(400);
        assertThat(mathTeacher.post(T, body(sectionA, mathId, "Fine marks", 24, "10.25", false)).status())
                .isEqualTo(201);
        assertThat(mathTeacher.post(T, body(sectionA, mathId, "Too precise", 24, "10.255", false)).status())
                .isEqualTo(400);
        assertThat(mathTeacher.post(T, body(sectionA, mathId, " ", 24, "10", false)).status()).isEqualTo(400);

        // A teacher can only set work for a class and subject they teach.
        assertThat(englishTeacher.post(T, body(sectionA, mathId, "Not mine", 24, "10", false)).status())
                .isEqualTo(403);
        assertThat(mathTeacher.post(T, body(sectionA, englishId, "Not mine", 24, "10", false)).status())
                .isEqualTo(403);
        assertThat(mathTeacher.post(T, body(UUID.randomUUID(), mathId, "Nowhere", 24, "10", false)).status())
                .isEqualTo(404);
    }

    @Test
    void aDraftCanBeDeletedButOnlyBeforePublishing() throws Exception {
        UUID id = id(draft("Scratch", 24, "10", false));
        ok(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(), pdf("a.pdf")), 200);
        long before = filesOnDisk();
        assertThat(before).isPositive();

        assertThat(mathTeacher.delete(T + "/" + id).status()).isEqualTo(204);

        assertThat(filesOnDisk()).isEqualTo(before - 1);
        assertThat(jdbc.queryForObject("select count(*) from assignment_attachments", Integer.class)).isZero();
        assertThat(mathTeacher.get(T + "/" + id).status()).isEqualTo(404);
    }

    // ---- submissions and review ------------------------------------------------------------------------

    @Test
    void aStudentSubmitsAndTheTeacherReviewsIt() throws Exception {
        UUID id = published("Worksheet", 48, "20", false);

        assertThat(submit(ann, id, null).status()).isEqualTo(400);
        assertThat(submit(ann, id, "   ").status()).isEqualTo(400);
        JsonNode first = ok(submit(ann, id, "Version one"), 200);
        assertThat(first.get("submission").get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(first.get("submission").get("marks").isNull()).isTrue();

        // Editing before review replaces the answer: still exactly one submission.
        JsonNode edited = ok(submit(ann, id, "Version two"), 200);
        assertThat(edited.get("submission").get("textAnswer").asText()).isEqualTo("Version two");
        assertThat(jdbc.queryForObject("select count(*) from assignment_submissions", Integer.class)).isEqualTo(1);

        JsonNode roster = ok(mathTeacher.get(T + "/" + id + "/submissions"), 200);
        assertThat(roster).hasSize(2);
        assertThat(roster.get(0).get("admissionNumber").asText()).isEqualTo("S-1");
        assertThat(roster.get(0).get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(roster.get(1).get("status").isNull()).isTrue();
        assertThat(ok(mathTeacher.get(T + "/" + id), 200).get("counts").get("awaitingReview").asInt()).isEqualTo(1);
        JsonNode queue = ok(mathTeacher.get("/api/v1/teacher/submissions"), 200);
        assertThat(queue.get("totalElements").asInt()).isEqualTo(1);
        assertThat(queue.get("content").get(0).get("assignmentTitle").asText()).isEqualTo("Worksheet");

        UUID submissionId = UUID.fromString(roster.get(0).get("submissionId").asText());
        String path = T + "/" + id + "/submissions/" + submissionId;
        long version = ok(mathTeacher.get(path), 200).get("version").asLong();

        assertThat(mathTeacher.put(path + "/review", review("REVIEWED", null, "ok", version)).status()).isEqualTo(400);
        Reply tooMany = mathTeacher.put(path + "/review", review("REVIEWED", 20.5, "ok", version));
        assertThat(tooMany.status()).isEqualTo(400);
        assertThat(mathTeacher.put(path + "/review", review("REVIEWED", -1, "ok", version)).status()).isEqualTo(400);
        assertThat(mathTeacher.put(path + "/review", review("REVIEWED", 15, "ok", version + 3)).status())
                .isEqualTo(409);
        assertThat(englishTeacher.put(path + "/review", review("REVIEWED", 15, "ok", version)).status())
                .isEqualTo(404);
        assertThat(englishTeacher.get(path).status()).isEqualTo(404);

        JsonNode reviewed = ok(mathTeacher.put(path + "/review", review("REVIEWED", 18.5, "Well done", version)), 200);
        assertThat(reviewed.get("status").asText()).isEqualTo("REVIEWED");
        assertThat(reviewed.get("reviewedBy").asText()).isEqualTo("Milo Math");

        JsonNode seen = ok(ann.get(S + "/" + id), 200).get("submission");
        assertThat(seen.get("marks").decimalValue()).isEqualByComparingTo("18.5");
        assertThat(seen.get("feedback").asText()).isEqualTo("Well done");
        assertThat(ok(ann.get(S + "/" + id), 200).get("canSubmit").asBoolean()).isFalse();
        assertThat(submit(ann, id, "Too late to change").status()).isEqualTo(422);

        // Marks are fixed to the maximum once work exists, and the queue no longer lists reviewed work.
        Map<String, Object> change = body(sectionA, mathId, "Worksheet", 48, "30", false);
        change.put("version", ok(mathTeacher.get(T + "/" + id), 200).get("version").asLong());
        assertThat(mathTeacher.put(T + "/" + id, change).status()).isEqualTo(422);
        assertThat(ok(mathTeacher.get("/api/v1/teacher/submissions"), 200).get("totalElements").asInt()).isZero();
        assertThat(ok(mathTeacher.get("/api/v1/teacher/submissions?status=REVIEWED"), 200).get("totalElements")
                .asInt()).isEqualTo(1);
        JsonNode mine = ok(ann.get("/api/v1/student/submissions"), 200);
        assertThat(mine.get("content").get(0).get("marks").decimalValue()).isEqualByComparingTo("18.5");

        // The class next door cannot submit, and an unenrolled view is simply missing.
        assertThat(submit(cat, id, "Sneaky").status()).isEqualTo(404);
    }

    @Test
    void aStudentSeesNothingOfTheirWorkBeingMarkedUntilItIsReviewed() throws Exception {
        UUID id = published("Quiet marking", 48, "10", false);
        ok(submit(bob, id, "My answer"), 200);
        UUID submissionId = UUID.fromString(ok(mathTeacher.get(T + "/" + id + "/submissions"), 200).get(1)
                .get("submissionId").asText());

        JsonNode mine = ok(bob.get("/api/v1/student/submissions"), 200);
        assertThat(mine.get("content").get(0).get("marks").isNull()).isTrue();
        assertThat(mine.get("content").get(0).get("status").asText()).isEqualTo("SUBMITTED");
        // Bob's id is not a way to someone else's work.
        assertThat(ok(ann.get("/api/v1/student/submissions"), 200).get("totalElements").asInt()).isZero();
        assertThat(ann.get(T + "/" + id + "/submissions/" + submissionId).status()).isEqualTo(403);
    }

    @Test
    void aResubmissionCanBeRequestedAndAnsweredEvenAfterTheDueDate() throws Exception {
        UUID id = published("Redo", 48, "10", false);
        ok(submit(bob, id, "First try"), 200);
        JsonNode row = ok(mathTeacher.get(T + "/" + id + "/submissions"), 200).get(1);
        String path = T + "/" + id + "/submissions/" + row.get("submissionId").asText();
        long version = ok(mathTeacher.get(path), 200).get("version").asLong();

        assertThat(mathTeacher.put(path + "/review", review("RESUBMISSION_REQUESTED", null, " ", version)).status())
                .isEqualTo(400);
        JsonNode requested = ok(mathTeacher.put(path + "/review",
                review("RESUBMISSION_REQUESTED", null, "Show your working", version)), 200);
        assertThat(requested.get("status").asText()).isEqualTo("RESUBMISSION_REQUESTED");

        // The student sees the feedback but no marks, and may answer again although the due date has passed.
        setDue(id, Duration.ofHours(-5));
        JsonNode view = ok(bob.get(S + "/" + id), 200);
        assertThat(view.get("submission").get("feedback").asText()).isEqualTo("Show your working");
        assertThat(view.get("submission").get("marks").isNull()).isTrue();
        assertThat(view.get("canSubmit").asBoolean()).isTrue();
        JsonNode again = ok(submit(bob, id, "Second try with working"), 200);
        assertThat(again.get("submission").get("status").asText()).isEqualTo("SUBMITTED");

        long next = ok(mathTeacher.get(path), 200).get("version").asLong();
        assertThat(ok(mathTeacher.put(path + "/review", review("REVIEWED", 9, "Better", next)), 200)
                .get("marks").decimalValue()).isEqualByComparingTo("9");
    }

    @Test
    void lateWorkFollowsTheAssignmentSetting() throws Exception {
        UUID strict = published("Strict", 48, "10", false);
        UUID lenient = published("Lenient", 48, "10", true);
        ok(submit(ann, strict, "On time"), 200);
        setDue(strict, Duration.ofHours(-1));
        setDue(lenient, Duration.ofHours(-1));

        Reply refused = submit(bob, strict, "Too late");
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.detail()).contains("due date has passed");
        assertThat(ok(bob.get(S + "/" + strict), 200).get("canSubmit").asBoolean()).isFalse();
        // Work handed in on time cannot be changed once the due date passes and late work is not accepted.
        assertThat(submit(ann, strict, "Changed my mind").status()).isEqualTo(422);

        JsonNode late = ok(submit(bob, lenient, "Late but accepted"), 200);
        assertThat(late.get("submission").get("status").asText()).isEqualTo("LATE");
        assertThat(ok(mathTeacher.get("/api/v1/teacher/submissions?status=LATE"), 200).get("totalElements").asInt())
                .isEqualTo(1);
        assertThat(ok(mathTeacher.get(T + "/" + lenient), 200).get("counts").get("awaitingReview").asInt())
                .isEqualTo(1);

        // Reopening needs a due date in the future or late work allowed.
        ok(mathTeacher.post(T + "/" + strict + "/close", null), 200);
        Reply reopen = mathTeacher.post(T + "/" + strict + "/reopen", null);
        assertThat(reopen.status()).isEqualTo(422);
        assertThat(reopen.detail()).contains("Extend the due date");
    }

    @Test
    void aClosedAssignmentTakesNoNewWorkButCanStillBeReviewed() throws Exception {
        UUID id = published("Closing", 48, "10", false);
        ok(submit(ann, id, "In time"), 200);
        ok(mathTeacher.post(T + "/" + id + "/close", null), 200);

        Reply refused = submit(bob, id, "After closing");
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.detail()).contains("closed");
        assertThat(ok(ann.get(S + "?status=CLOSED"), 200).get("totalElements").asInt()).isEqualTo(1);

        JsonNode row = ok(mathTeacher.get(T + "/" + id + "/submissions"), 200).get(0);
        String path = T + "/" + id + "/submissions/" + row.get("submissionId").asText();
        long version = ok(mathTeacher.get(path), 200).get("version").asLong();
        ok(mathTeacher.put(path + "/review", review("REVIEWED", 7, "Fine", version)), 200);
    }

    // ---- files -----------------------------------------------------------------------------------------

    @Test
    void uploadsAreValidatedStoredUnderGeneratedNamesAndServedAsAttachments() throws Exception {
        UUID id = id(draft("With files", 48, "10", false));
        long start = filesOnDisk();

        JsonNode withFiles = ok(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(), pdf("../../evil.pdf"),
                file("notes.txt", "text/plain", "plain notes".getBytes(StandardCharsets.UTF_8))), 200);
        assertThat(withFiles.get("attachments")).hasSize(2);
        assertThat(withFiles.get("attachments").get(0).get("name").asText()).isEqualTo("evil.pdf");
        assertThat(withFiles.get("attachments").get(0).get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(filesOnDisk()).isEqualTo(start + 2);
        List<String> keys = jdbc.queryForList("select storage_key from assignment_attachments", String.class);
        assertThat(keys).hasSize(2).allMatch(key -> key.matches(KEY_PATTERN));
        keys.forEach(key -> assertThat(stored(key)).exists());

        // Refused uploads leave nothing behind, even when only one file in the request is bad.
        long before = filesOnDisk();
        assertThat(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(),
                file("virus.exe", "application/x-msdownload", "MZ".getBytes())).status()).isEqualTo(400);
        assertThat(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(),
                file("fake.pdf", "application/pdf", "<html>not a pdf</html>".getBytes())).status()).isEqualTo(400);
        assertThat(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(), pdf("good.pdf"),
                file("bad.exe", "application/octet-stream", "MZ".getBytes())).status()).isEqualTo(400);
        assertThat(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of()).status()).isEqualTo(400);
        assertThat(filesOnDisk()).isEqualTo(before);
        byte[] huge = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(), 0, huge, 0, 5);
        assertThat(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(),
                file("huge.pdf", "application/pdf", huge)).status()).isEqualTo(400);

        // Students download only files of assignments they can see, as attachments.
        UUID attachment = UUID.fromString(withFiles.get("attachments").get(0).get("id").asText());
        String download = "/attachments/" + attachment + "/download";
        Binary own = mathTeacher.download(T + "/" + id + download);
        assertThat(own.status()).isEqualTo(200);
        assertThat(own.body()).isEqualTo("%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII));
        assertThat(own.response().getContentType()).isEqualTo("application/pdf");
        assertThat(own.response().getHeader("Content-Disposition")).startsWith("attachment").contains("evil.pdf");
        assertThat(own.response().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(ann.download(S + "/" + id + download).status()).isEqualTo(404);
        assertThat(englishTeacher.download(T + "/" + id + download).status()).isEqualTo(404);

        ok(mathTeacher.post(T + "/" + id + "/publish", null), 200);
        assertThat(ann.download(S + "/" + id + download).status()).isEqualTo(200);
        assertThat(cat.download(S + "/" + id + download).status()).isEqualTo(404);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper).download(S + "/" + id + download).status())
                .isEqualTo(401);

        // Removing a file removes it from storage.
        ok(mathTeacher.delete(T + "/" + id + "/attachments/" + attachment), 200);
        assertThat(filesOnDisk()).isEqualTo(start + 1);
        assertThat(ann.download(S + "/" + id + download).status()).isEqualTo(404);
    }

    @Test
    void anAssignmentHasAtMostTheConfiguredNumberOfFiles() throws Exception {
        UUID id = id(draft("Many files", 48, "10", false));
        for (int i = 0; i < 10; i++) {
            ok(mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(), pdf("f" + i + ".pdf")), 200);
        }
        Reply eleventh = mathTeacher.multipart(T + "/" + id + "/attachments", Map.of(), pdf("one-more.pdf"));
        assertThat(eleventh.status()).isEqualTo(422);
        assertThat(eleventh.detail()).contains("at most 10 files");
    }

    @Test
    void submissionFilesBelongToTheirStudentAndTheirTeacher() throws Exception {
        UUID id = published("Photos", 48, "10", false);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        JsonNode sent = ok(submit(ann, id, null, file("work.png", "image/png", png)), 200);
        UUID attachment = UUID.fromString(sent.get("submission").get("attachments").get(0).get("id").asText());
        UUID submissionId = UUID.fromString(sent.get("submission").get("id").asText());

        Binary teacherCopy = mathTeacher.download(T + "/" + id + "/submissions/" + submissionId + "/attachments/"
                + attachment + "/download");
        assertThat(teacherCopy.status()).isEqualTo(200);
        assertThat(teacherCopy.body()).isEqualTo(png);
        assertThat(teacherCopy.response().getContentType()).isEqualTo("image/png");
        assertThat(ann.download(S + "/" + id + "/submission/attachments/" + attachment + "/download").status())
                .isEqualTo(200);
        // Another student, another teacher and another class cannot reach it.
        assertThat(bob.download(S + "/" + id + "/submission/attachments/" + attachment + "/download").status())
                .isEqualTo(404);
        assertThat(englishTeacher.download(T + "/" + id + "/submissions/" + submissionId + "/attachments/"
                + attachment + "/download").status()).isEqualTo(404);
        assertThat(cat.download(S + "/" + id + "/submission/attachments/" + attachment + "/download").status())
                .isEqualTo(404);

        // A file-only submission must keep at least one file; text makes the file optional.
        assertThat(ann.delete(S + "/" + id + "/submission/attachments/" + attachment).status()).isEqualTo(422);
        ok(submit(ann, id, "Some words"), 200);
        long onDisk = filesOnDisk();
        ok(ann.delete(S + "/" + id + "/submission/attachments/" + attachment), 200);
        assertThat(filesOnDisk()).isEqualTo(onDisk - 1);

        // At most five files per submission.
        for (int i = 0; i < 5; i++) {
            ok(submit(ann, id, null, pdf("p" + i + ".pdf")), 200);
        }
        Reply sixth = submit(ann, id, null, pdf("p5.pdf"));
        assertThat(sixth.status()).isEqualTo(422);
        assertThat(sixth.detail()).contains("at most 5 files");
    }

    @Test
    void filesWrittenForARolledBackTransactionAreRemoved() throws Exception {
        long before = filesOnDisk();
        List<String> keys = new java.util.ArrayList<>();
        try {
            transaction.executeWithoutResult(status -> {
                uploadService.storeAll(List.of(pdf("a.pdf"))).forEach(s -> keys.add(s.key()));
                assertThat(filesOnDisk()).isEqualTo(before + 1);
                throw new IllegalStateException("database failure");
            });
        } catch (IllegalStateException expected) {
            // The point of the test.
        }
        assertThat(filesOnDisk()).isEqualTo(before);

        transaction.executeWithoutResult(status -> uploadService.storeAll(List.of(pdf("b.pdf"))).forEach(s -> keys.add(s.key())));
        assertThat(filesOnDisk()).isEqualTo(before + 1);

        // A deletion is applied only when the transaction that asked for it commits.
        String kept = keys.get(1);
        try {
            transaction.executeWithoutResult(status -> {
                uploadService.deleteAfterCommit(kept);
                throw new IllegalStateException("rolled back");
            });
        } catch (IllegalStateException expected) {
            // The point of the test.
        }
        assertThat(stored(kept)).exists();
        transaction.executeWithoutResult(status -> uploadService.deleteAfterCommit(kept));
        assertThat(stored(kept)).doesNotExist();
    }

    // ---- boundaries ------------------------------------------------------------------------------------

    @Test
    void sectionsAndSubjectsInUseCannotBeRemoved() throws Exception {
        draft("Pins the class", 48, "10", false);
        assertThat(admin.delete(ADMIN + "/sections/" + sectionA).status()).isEqualTo(409);
        JsonNode links = ok(admin.get(ADMIN + "/section-subjects?sectionId=" + sectionA + "&subjectId=" + mathId),
                200);
        assertThat(admin.delete(ADMIN + "/section-subjects/" + id(links.get("content").get(0))).status())
                .isEqualTo(409);
    }

    @Test
    void endpointsAreLimitedByRole() throws Exception {
        UUID id = published("Visible", 48, "10", false);
        ApiSession anonymous = ApiSession.anonymous(mockMvc, objectMapper);
        for (String path : List.of(T, S, "/api/v1/teacher/submissions", "/api/v1/student/submissions")) {
            assertThat(anonymous.get(path).status()).as(path).isEqualTo(401);
        }
        assertThat(ann.get(T).status()).isEqualTo(403);
        assertThat(ann.get("/api/v1/teacher/submissions").status()).isEqualTo(403);
        assertThat(mathTeacher.get(S).status()).isEqualTo(403);
        assertThat(mathTeacher.get("/api/v1/student/submissions").status()).isEqualTo(403);
        assertThat(admin.get(T).status()).isEqualTo(403);
        assertThat(admin.get(S + "/" + id).status()).isEqualTo(403);

        UUID otherSchool = schools.save(new School("Other", "OTHER", "UTC")).getId();
        provisioner.createActiveUser(otherSchool, "T-9", "t9@other.school", PASSWORD, "Otto", "Other",
                RoleName.ROLE_TEACHER);
        ApiSession outsider = ApiSession.login(mockMvc, objectMapper, "t9@other.school", PASSWORD);
        assertThat(outsider.get(T + "/" + id).status()).isEqualTo(404);
        assertThat(outsider.post(T, body(sectionA, mathId, "Cross school", 24, "10", false)).status()).isEqualTo(404);
    }
}
