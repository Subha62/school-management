package com.schoolmanagement.exam;

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
import java.math.BigDecimal;
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

/** Examinations end to end: grade scale, set-up, marks entry, locking, publication and what each role may see. */
class ExamFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String ADMIN = "/api/v1/admin";
    private static final String TEACHER = "/api/v1/teacher/exams";
    private static final String STUDENT = "/api/v1/student/results";

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
    private ApiSession mathTeacher;
    private ApiSession englishTeacher;
    private ApiSession ann;
    private ApiSession cat;
    private UUID year;
    private UUID grade;
    private UUID sectionA;
    private UUID sectionB;
    private UUID mathId;
    private UUID englishId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table exam_results, exam_comments, student_marks, exam_subjects, exams, grade_rules, "
                + "submission_attachments, assignment_submissions, assignment_attachments, assignments, "
                + "attendance_corrections, attendance_records, attendance_sessions, timetable_entries, "
                + "student_enrollments, promotion_batches, student_guardians, guardians, student_profiles, "
                + "teacher_profiles, section_subjects, sections, subjects, grade_levels, academic_years, "
                + "refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
        UUID schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        UUID mathTeacherId = provisioner.createActiveUser(schoolId, "T-1", "t1@test.school", PASSWORD, "Milo",
                "Math", RoleName.ROLE_TEACHER).getId();
        UUID englishTeacherId = provisioner.createActiveUser(schoolId, "T-2", "t2@test.school", PASSWORD, "Edna",
                "English", RoleName.ROLE_TEACHER).getId();
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        year = id(ok(admin.post(ADMIN + "/academic-years", map("name", "2026-2027", "startDate",
                today.minusDays(60).toString(), "endDate", today.plusDays(120).toString())), 201));
        ok(admin.post(ADMIN + "/academic-years/" + year + "/activate", null), 200);
        grade = id(ok(admin.post(ADMIN + "/grade-levels", map("name", "Grade 8", "displayOrder", 8, "active", true)),
                201));
        mathId = id(ok(admin.post(ADMIN + "/subjects", map("code", "MATH", "name", "Mathematics", "description", "",
                "active", true)), 201));
        englishId = id(ok(admin.post(ADMIN + "/subjects", map("code", "ENG", "name", "English", "description", "",
                "active", true)), 201));
        sectionA = id(ok(admin.post(ADMIN + "/sections", map("academicYearId", year, "gradeLevelId", grade, "name",
                "A", "capacity", 40, "classTeacherId", mathTeacherId, "active", true)), 201));
        sectionB = id(ok(admin.post(ADMIN + "/sections", map("academicYearId", year, "gradeLevelId", grade, "name",
                "B", "capacity", 40, "classTeacherId", null, "active", true)), 201));
        assign(sectionA, mathId, mathTeacherId);
        assign(sectionA, englishId, englishTeacherId);
        assign(sectionB, mathId, mathTeacherId);
        enrolled("S-1", "Ann", sectionA);
        enrolled("S-2", "Bob", sectionA);
        enrolled("S-3", "Cat", sectionB);
        mathTeacher = ApiSession.login(mockMvc, objectMapper, "t1@test.school", PASSWORD);
        englishTeacher = ApiSession.login(mockMvc, objectMapper, "t2@test.school", PASSWORD);
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

    private UUID student(String admission) {
        return jdbc.queryForObject("select id from student_profiles where admission_number = ?", UUID.class,
                admission);
    }

    private void band(String grade, String min, String max) throws Exception {
        ok(admin.post(ADMIN + "/grade-rules", map("grade", grade, "description", "", "minPercent",
                new BigDecimal(min), "maxPercent", new BigDecimal(max))), 201);
    }

    private void fullScale() throws Exception {
        band("F", "0", "39.99");
        band("C", "40", "59.99");
        band("B", "60", "79.99");
        band("A", "80", "100");
    }

    private Map<String, Object> examBody(String name) {
        return map("academicYearId", year, "gradeLevelId", grade, "name", name, "startDate", null, "endDate", null,
                "subjects", List.of(
                        map("subjectId", mathId, "maxMarks", new BigDecimal("100"), "passMarks", new BigDecimal("35"),
                                "examDate", null),
                        map("subjectId", englishId, "maxMarks", new BigDecimal("50"), "passMarks", new BigDecimal("20"),
                                "examDate", null)));
    }

    private JsonNode exam(String name) throws Exception {
        return ok(admin.post(ADMIN + "/exams", examBody(name)), 201);
    }

    private UUID openExam() throws Exception {
        UUID exam = id(exam("Term 1"));
        ok(admin.post(ADMIN + "/exams/" + exam + "/open", null), 200);
        return exam;
    }

    private UUID examSubject(JsonNode exam, UUID subject) {
        for (JsonNode s : exam.get("subjects")) {
            if (s.get("subjectId").asText().equals(subject.toString())) {
                return UUID.fromString(s.get("id").asText());
            }
        }
        throw new IllegalStateException("subject not in exam");
    }

    private String marksUrl(UUID exam, UUID examSubject, UUID section) {
        return TEACHER + "/" + exam + "/marks?examSubjectId=" + examSubject + "&sectionId=" + section;
    }

    private Map<String, Object> entry(UUID student, String marks, boolean absent) {
        return map("studentId", student, "marks", marks == null ? null : new BigDecimal(marks), "absent", absent,
                "remarks", null);
    }

    private void enter(ApiSession teacher, UUID exam, UUID examSubject, UUID section,
            Map<String, Object>... entries) throws Exception {
        ok(teacher.put(marksUrl(exam, examSubject, section), map("entries", List.of(entries))), 200);
    }

    /** Ann 90 + 40, Bob absent in maths + 10, Cat 40 in maths. */
    private void enterEverything(UUID exam) throws Exception {
        JsonNode view = ok(admin.get(ADMIN + "/exams/" + exam), 200);
        UUID math = examSubject(view, mathId);
        UUID english = examSubject(view, englishId);
        enter(mathTeacher, exam, math, sectionA, entry(student("S-1"), "90", false), entry(student("S-2"), null, true));
        enter(mathTeacher, exam, math, sectionB, entry(student("S-3"), "40", false));
        enter(englishTeacher, exam, english, sectionA, entry(student("S-1"), "40", false),
                entry(student("S-2"), "10", false));
    }

    // ---- grade scale -----------------------------------------------------------------------------------

    @Test
    void gradeBandsMayNotOverlapOrRepeatAGradeAndMustBeAValidRange() throws Exception {
        band("A", "80", "100");

        assertThat(admin.post(ADMIN + "/grade-rules", map("grade", "B", "minPercent", new BigDecimal("79"),
                "maxPercent", new BigDecimal("90"))).status()).isEqualTo(409);
        assertThat(admin.post(ADMIN + "/grade-rules", map("grade", "B", "minPercent", new BigDecimal("100"),
                "maxPercent", new BigDecimal("100"))).status()).isEqualTo(409);
        assertThat(admin.post(ADMIN + "/grade-rules", map("grade", "a", "minPercent", new BigDecimal("0"),
                "maxPercent", new BigDecimal("10"))).status()).as("grade names are unique ignoring case")
                .isEqualTo(409);
        assertThat(admin.post(ADMIN + "/grade-rules", map("grade", "B", "minPercent", new BigDecimal("70"),
                "maxPercent", new BigDecimal("60"))).status()).isEqualTo(400);
        assertThat(admin.post(ADMIN + "/grade-rules", map("grade", "B", "minPercent", new BigDecimal("60.123"),
                "maxPercent", new BigDecimal("70"))).status()).isEqualTo(400);
        assertThat(admin.post(ADMIN + "/grade-rules", map("grade", "B", "minPercent", new BigDecimal("50"),
                "maxPercent", new BigDecimal("101"))).status()).isEqualTo(400);

        band("B", "60", "79.99");
        JsonNode list = ok(admin.get(ADMIN + "/grade-rules"), 200);
        assertThat(list.get("content")).hasSize(2);
        assertThat(list.get("content").get(0).get("grade").asText()).isEqualTo("A");

        JsonNode b = list.get("content").get(1);
        assertThat(admin.put(ADMIN + "/grade-rules/" + b.get("id").asText(), map("grade", "B", "description", "",
                "minPercent", new BigDecimal("60"), "maxPercent", new BigDecimal("85"), "version",
                b.get("version").asLong())).status()).as("widening into A's band overlaps").isEqualTo(409);
        assertThat(admin.put(ADMIN + "/grade-rules/" + b.get("id").asText(), map("grade", "B", "description", "Good",
                "minPercent", new BigDecimal("55"), "maxPercent", new BigDecimal("79.99"), "version",
                b.get("version").asLong())).status()).isEqualTo(200);
        assertThat(admin.delete(ADMIN + "/grade-rules/" + b.get("id").asText()).status()).isEqualTo(204);
    }

    @Test
    void onlyAdministratorsManageTheGradeScaleAndExams() throws Exception {
        assertThat(mathTeacher.get(ADMIN + "/grade-rules").status()).isEqualTo(403);
        assertThat(ann.get(ADMIN + "/exams").status()).isEqualTo(403);
        assertThat(mathTeacher.post(ADMIN + "/exams", examBody("X")).status()).isEqualTo(403);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper).get(STUDENT).status()).isEqualTo(401);
    }

    // ---- set-up ----------------------------------------------------------------------------------------

    @Test
    void examSetUpIsValidatedAndOnlyDraftsCanBeEditedOrDeleted() throws Exception {
        JsonNode draft = exam("Term 1");
        UUID id = id(draft);
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("subjects")).hasSize(2);

        assertThat(admin.post(ADMIN + "/exams", examBody("term 1")).status()).as("name unique ignoring case")
                .isEqualTo(409);
        Map<String, Object> badPass = examBody("Bad");
        badPass.put("subjects", List.of(map("subjectId", mathId, "maxMarks", new BigDecimal("50"), "passMarks",
                new BigDecimal("60"), "examDate", null)));
        assertThat(admin.post(ADMIN + "/exams", badPass).status()).isEqualTo(400);
        Map<String, Object> twice = examBody("Twice");
        twice.put("subjects", List.of(
                map("subjectId", mathId, "maxMarks", new BigDecimal("50"), "passMarks", new BigDecimal("20")),
                map("subjectId", mathId, "maxMarks", new BigDecimal("50"), "passMarks", new BigDecimal("20"))));
        assertThat(admin.post(ADMIN + "/exams", twice).status()).isEqualTo(400);
        Map<String, Object> none = examBody("None");
        none.put("subjects", List.of());
        assertThat(admin.post(ADMIN + "/exams", none).status()).isEqualTo(400);

        Map<String, Object> edit = examBody("Term 1 revised");
        edit.put("subjects", List.of(map("subjectId", mathId, "maxMarks", new BigDecimal("80"), "passMarks",
                new BigDecimal("30"), "examDate", null)));
        edit.put("version", draft.get("version").asLong());
        JsonNode edited = ok(admin.put(ADMIN + "/exams/" + id, edit), 200);
        assertThat(edited.get("name").asText()).isEqualTo("Term 1 revised");
        assertThat(edited.get("subjects")).hasSize(1);
        assertThat(edited.get("subjects").get(0).get("maxMarks").decimalValue()).isEqualByComparingTo("80");
        edit.put("version", draft.get("version").asLong());
        assertThat(admin.put(ADMIN + "/exams/" + id, edit).status()).as("stale version").isEqualTo(409);

        ok(admin.post(ADMIN + "/exams/" + id + "/open", null), 200);
        edit.put("version", 99);
        assertThat(admin.put(ADMIN + "/exams/" + id, edit).status()).isIn(409, 422);
        assertThat(admin.delete(ADMIN + "/exams/" + id).status()).isEqualTo(422);
        assertThat(admin.delete(ADMIN + "/exams/" + id(exam("Draft to delete"))).status()).isEqualTo(204);
    }

    @Test
    void anExamNeedsAClassThatIsTaughtItsSubjectsBeforeItOpens() throws Exception {
        UUID science = id(ok(admin.post(ADMIN + "/subjects", map("code", "SCI", "name", "Science", "description", "",
                "active", true)), 201));
        Map<String, Object> body = examBody("Science only");
        body.put("subjects", List.of(map("subjectId", science, "maxMarks", new BigDecimal("50"), "passMarks",
                new BigDecimal("20"))));
        UUID id = id(ok(admin.post(ADMIN + "/exams", body), 201));
        Reply open = admin.post(ADMIN + "/exams/" + id + "/open", null);
        assertThat(open.status()).isEqualTo(422);
        assertThat(open.detail()).contains("taught");
    }

    // ---- marks entry -----------------------------------------------------------------------------------

    @Test
    void teachersSeeAndEnterMarksOnlyForTheirOwnSubjectsAndClasses() throws Exception {
        UUID id = id(exam("Term 1"));
        JsonNode view = ok(admin.get(ADMIN + "/exams/" + id), 200);
        UUID math = examSubject(view, mathId);
        UUID english = examSubject(view, englishId);

        assertThat(mathTeacher.get(marksUrl(id, math, sectionA)).status()).as("drafts are hidden").isEqualTo(404);
        assertThat(ok(mathTeacher.get(TEACHER), 200).get("content")).isEmpty();
        ok(admin.post(ADMIN + "/exams/" + id + "/open", null), 200);

        JsonNode mine = ok(mathTeacher.get(TEACHER), 200).get("content").get(0);
        assertThat(mine.get("tasks")).hasSize(2);
        assertThat(mine.get("commentSections")).hasSize(1);
        JsonNode theirs = ok(englishTeacher.get(TEACHER), 200).get("content").get(0);
        assertThat(theirs.get("tasks")).hasSize(1);
        assertThat(theirs.get("tasks").get(0).get("subjectName").asText()).isEqualTo("English");
        assertThat(theirs.get("commentSections")).isEmpty();

        assertThat(englishTeacher.get(marksUrl(id, math, sectionA)).status()).as("not their subject").isEqualTo(403);
        assertThat(mathTeacher.get(marksUrl(id, english, sectionA)).status()).isEqualTo(403);
        assertThat(englishTeacher.get(marksUrl(id, english, sectionB)).status()).as("not their class").isEqualTo(403);
        assertThat(ann.get(marksUrl(id, math, sectionA)).status()).isEqualTo(403);
        assertThat(englishTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "50", false)))).status()).isEqualTo(403);
        JsonNode sheet = ok(mathTeacher.get(marksUrl(id, math, sectionA)), 200);
        assertThat(sheet.get("editable").asBoolean()).isTrue();
        assertThat(sheet.get("rows")).hasSize(2);
        assertThat(sheet.get("rows").get(0).get("entered").asBoolean()).isFalse();
    }

    @Test
    void marksAreValidatedAndAnEntryWithNothingClearsTheMark() throws Exception {
        UUID id = openExam();
        JsonNode view = ok(admin.get(ADMIN + "/exams/" + id), 200);
        UUID math = examSubject(view, mathId);

        Reply tooHigh = mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "100.5", false))));
        assertThat(tooHigh.status()).isEqualTo(400);
        assertThat(tooHigh.body().toString()).contains("entries[0].marks").contains("more than 100");
        Reply absentWithMarks = mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "10", true))));
        assertThat(absentWithMarks.status()).isEqualTo(400);
        assertThat(absentWithMarks.body().toString()).contains("absent");
        assertThat(mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "-1", false)))).status()).isEqualTo(400);
        assertThat(mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "10.123", false)))).status()).isEqualTo(400);
        assertThat(mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-3"), "10", false)))).status()).as("a student of another class")
                .isEqualTo(400);
        assertThat(mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "10", false), entry(student("S-1"), "20", false)))).status())
                .as("the same student twice").isEqualTo(400);
        assertThat(ok(mathTeacher.get(marksUrl(id, math, sectionA)), 200).get("rows").get(0).get("entered")
                .asBoolean()).as("nothing was saved by the rejected requests").isFalse();

        JsonNode saved = ok(mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), "100", false), entry(student("S-2"), null, true)))), 200);
        assertThat(saved.get("rows").get(0).get("marks").decimalValue()).isEqualByComparingTo("100");
        assertThat(saved.get("rows").get(1).get("absent").asBoolean()).isTrue();
        assertThat(saved.get("rows").get(1).get("entered").asBoolean()).isTrue();
        assertThat(ok(admin.get(ADMIN + "/exams/" + id + "/progress"), 200).get("missing").asInt()).isEqualTo(3);

        JsonNode cleared = ok(mathTeacher.put(marksUrl(id, math, sectionA), map("entries",
                List.of(entry(student("S-1"), null, false)))), 200);
        assertThat(cleared.get("rows").get(0).get("entered").asBoolean()).isFalse();
        assertThat(cleared.get("rows").get(1).get("absent").asBoolean()).as("others are untouched").isTrue();
        assertThat(jdbc.queryForObject("select count(*) from student_marks", Integer.class)).isEqualTo(1);
    }

    // ---- lifecycle -------------------------------------------------------------------------------------

    @Test
    void anExamCannotBeLockedWithMissingMarksOrAGradeScaleWithGaps() throws Exception {
        UUID id = openExam();
        band("A", "80", "100");
        band("F", "0", "39.99");

        Reply noMarks = admin.post(ADMIN + "/exams/" + id + "/lock", null);
        assertThat(noMarks.status()).isEqualTo(422);
        assertThat(noMarks.detail()).contains("missing");

        enterEverything(id);
        ok(admin.get(ADMIN + "/exams/" + id + "/progress"), 200);
        assertThat(ok(admin.get(ADMIN + "/exams/" + id + "/progress"), 200).get("complete").asBoolean()).isTrue();

        Reply gap = admin.post(ADMIN + "/exams/" + id + "/lock", null);
        assertThat(gap.status()).isEqualTo(422);
        assertThat(gap.detail()).contains("gap").contains("40").contains("79.99");

        band("C", "40", "79.99");
        assertThat(mathTeacher.post(ADMIN + "/exams/" + id + "/lock", null).status()).as("teachers cannot lock")
                .isEqualTo(403);
        assertThat(ok(admin.post(ADMIN + "/exams/" + id + "/lock", null), 200).get("status").asText())
                .isEqualTo("LOCKED");
    }

    @Test
    void lockedMarksCannotChangeUntilTheExamIsReopenedAndPublishedResultsAreFinal() throws Exception {
        fullScale();
        UUID id = openExam();
        enterEverything(id);
        JsonNode view = ok(admin.get(ADMIN + "/exams/" + id), 200);
        UUID math = examSubject(view, mathId);

        assertThat(admin.post(ADMIN + "/exams/" + id + "/publish", null).status()).as("lock first").isEqualTo(422);
        ok(admin.post(ADMIN + "/exams/" + id + "/lock", null), 200);
        Reply late = mathTeacher.put(marksUrl(id, math, sectionB), map("entries",
                List.of(entry(student("S-3"), "99", false))));
        assertThat(late.status()).isEqualTo(422);
        assertThat(ok(mathTeacher.get(marksUrl(id, math, sectionB)), 200).get("editable").asBoolean()).isFalse();
        assertThat(admin.post(ADMIN + "/exams/" + id + "/open", null).status()).isEqualTo(422);

        ok(admin.post(ADMIN + "/exams/" + id + "/reopen", null), 200);
        enter(mathTeacher, id, math, sectionB, entry(student("S-3"), "55", false));
        ok(admin.post(ADMIN + "/exams/" + id + "/lock", null), 200);
        ok(admin.post(ADMIN + "/exams/" + id + "/publish", null), 200);

        assertThat(admin.post(ADMIN + "/exams/" + id + "/reopen", null).status()).as("publication is final")
                .isEqualTo(422);
        assertThat(admin.post(ADMIN + "/exams/" + id + "/publish", null).status()).isEqualTo(422);
        assertThat(admin.delete(ADMIN + "/exams/" + id).status()).isEqualTo(422);
        assertThat(mathTeacher.put(marksUrl(id, math, sectionB), map("entries",
                List.of(entry(student("S-3"), "10", false)))).status()).isEqualTo(422);
    }

    @Test
    void resultsAreCalculatedFromMarksAndPreviewedBeforePublicationWithoutReachingStudents() throws Exception {
        fullScale();
        UUID id = openExam();
        enterEverything(id);
        assertThat(admin.get(ADMIN + "/exams/" + id + "/results?sectionId=" + sectionA).status())
                .as("not before the exam is locked").isEqualTo(422);
        ok(admin.post(ADMIN + "/exams/" + id + "/lock", null), 200);

        JsonNode preview = ok(admin.get(ADMIN + "/exams/" + id + "/results?sectionId=" + sectionA), 200);
        assertThat(preview.get("preview").asBoolean()).isTrue();
        JsonNode annResult = preview.get("results").get(0);
        assertThat(annResult.get("studentName").asText()).isEqualTo("Ann Pupil");
        assertThat(annResult.get("totalMarks").decimalValue()).isEqualByComparingTo("130");
        assertThat(annResult.get("totalMax").decimalValue()).isEqualByComparingTo("150");
        assertThat(annResult.get("percentage").decimalValue()).isEqualByComparingTo("86.67");
        assertThat(annResult.get("grade").asText()).isEqualTo("A");
        assertThat(annResult.get("result").asText()).isEqualTo("PASS");
        JsonNode bobResult = preview.get("results").get(1);
        assertThat(bobResult.get("subjects").get(0).get("subjectName").asText()).as("subjects are listed A-Z")
                .isEqualTo("English");
        assertThat(bobResult.get("subjects").get(0).get("status").asText()).isEqualTo("FAIL");
        assertThat(bobResult.get("subjects").get(1).get("status").asText()).isEqualTo("ABSENT");
        assertThat(bobResult.get("percentage").decimalValue()).isEqualByComparingTo("6.67");
        assertThat(bobResult.get("grade").asText()).isEqualTo("F");
        assertThat(bobResult.get("result").asText()).isEqualTo("FAIL");

        JsonNode catResult = ok(admin.get(ADMIN + "/exams/" + id + "/results?sectionId=" + sectionB), 200)
                .get("results").get(0);
        assertThat(catResult.get("subjects")).as("class B takes no English").hasSize(1);
        assertThat(catResult.get("percentage").decimalValue()).isEqualByComparingTo("40");
        assertThat(catResult.get("grade").asText()).isEqualTo("C");
        assertThat(catResult.get("result").asText()).isEqualTo("PASS");

        assertThat(ok(ann.get(STUDENT), 200).get("content")).as("students see nothing until publication").isEmpty();
        assertThat(ann.get(STUDENT + "/" + id).status()).isEqualTo(404);
        assertThat(mathTeacher.get(ADMIN + "/exams/" + id + "/results?sectionId=" + sectionA).status())
                .isEqualTo(403);
        assertThat(admin.get(ADMIN + "/exams/" + id + "/results?sectionId=" + UUID.randomUUID()).status())
                .isEqualTo(404);
    }

    @Test
    void publishedResultsAreFrozenAndStudentsSeeOnlyTheirOwn() throws Exception {
        fullScale();
        UUID id = openExam();
        enterEverything(id);
        JsonNode comment = ok(mathTeacher.put(TEACHER + "/" + id + "/comments/" + student("S-1"),
                map("comment", "Excellent term")), 200);
        assertThat(comment.get("comment").asText()).isEqualTo("Excellent term");
        ok(admin.post(ADMIN + "/exams/" + id + "/lock", null), 200);
        ok(admin.post(ADMIN + "/exams/" + id + "/publish", null), 200);

        JsonNode mine = ok(ann.get(STUDENT), 200).get("content");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("examName").asText()).isEqualTo("Term 1");
        assertThat(mine.get(0).get("percentage").decimalValue()).isEqualByComparingTo("86.67");
        assertThat(mine.get(0).get("grade").asText()).isEqualTo("A");
        JsonNode card = ok(ann.get(STUDENT + "/" + id), 200);
        assertThat(card.get("schoolName").asText()).isEqualTo("Test School");
        assertThat(card.get("gradeLevelName").asText()).isEqualTo("Grade 8");
        assertThat(card.get("result").get("studentName").asText()).isEqualTo("Ann Pupil");
        assertThat(card.get("result").get("comment").asText()).isEqualTo("Excellent term");
        assertThat(card.get("result").get("subjects")).hasSize(2);
        JsonNode catCard = ok(cat.get(STUDENT + "/" + id), 200);
        assertThat(catCard.get("result").get("studentName").asText()).as("each student gets their own card")
                .isEqualTo("Cat Pupil");
        assertThat(ann.get(STUDENT + "/" + UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(mathTeacher.get(STUDENT).status()).isEqualTo(403);

        // Changing the scale or a subject name afterwards must not change what was published.
        List<String> ids = new ArrayList<>();
        ok(admin.get(ADMIN + "/grade-rules"), 200).get("content").forEach(r -> ids.add(r.get("id").asText()));
        for (String rule : ids) {
            assertThat(admin.delete(ADMIN + "/grade-rules/" + rule).status()).isEqualTo(204);
        }
        band("Z", "0", "100");
        JsonNode frozen = ok(ann.get(STUDENT + "/" + id), 200).get("result");
        assertThat(frozen.get("grade").asText()).isEqualTo("A");
        JsonNode adminView = ok(admin.get(ADMIN + "/exams/" + id + "/results/" + student("S-1")), 200);
        assertThat(adminView.get("result").get("grade").asText()).isEqualTo("A");
        assertThat(ok(admin.get(ADMIN + "/exams/" + id + "/results?sectionId=" + sectionA), 200)
                .get("preview").asBoolean()).isFalse();
    }

    // ---- comments --------------------------------------------------------------------------------------

    @Test
    void onlyTheClassTeacherCommentsAndOnlyWhileTheExamIsOpen() throws Exception {
        fullScale();
        UUID id = openExam();
        String url = TEACHER + "/" + id + "/comments/" + student("S-1");

        assertThat(englishTeacher.put(url, map("comment", "Nice")).status()).isEqualTo(403);
        assertThat(englishTeacher.get(TEACHER + "/" + id + "/comments?sectionId=" + sectionA).status())
                .isEqualTo(403);
        assertThat(mathTeacher.put(url, map("comment", "x".repeat(501))).status()).isEqualTo(400);
        assertThat(mathTeacher.put(TEACHER + "/" + id + "/comments/" + student("S-3"),
                map("comment", "Nice")).status()).as("Cat's class has another class teacher").isEqualTo(403);
        ok(mathTeacher.put(url, map("comment", "  Good effort  ")), 200);
        JsonNode sheet = ok(mathTeacher.get(TEACHER + "/" + id + "/comments?sectionId=" + sectionA), 200);
        assertThat(sheet.get("editable").asBoolean()).isTrue();
        assertThat(sheet.get("rows").get(0).get("comment").asText()).isEqualTo("Good effort");
        assertThat(sheet.get("rows").get(1).get("comment").isNull()).isTrue();

        ok(mathTeacher.put(url, map("comment", "  ")), 200);
        assertThat(ok(mathTeacher.get(TEACHER + "/" + id + "/comments?sectionId=" + sectionA), 200).get("rows")
                .get(0).get("comment").isNull()).as("a blank comment removes it").isTrue();

        enterEverything(id);
        ok(admin.post(ADMIN + "/exams/" + id + "/lock", null), 200);
        assertThat(mathTeacher.put(url, map("comment", "Too late")).status()).isEqualTo(422);
    }

    // ---- deletion guards -------------------------------------------------------------------------------

    @Test
    void gradesAndSubjectsUsedByAnExamCannotBeDeleted() throws Exception {
        UUID science = id(ok(admin.post(ADMIN + "/subjects", map("code", "SCI", "name", "Science", "description", "",
                "active", true)), 201));
        UUID emptyGrade = id(ok(admin.post(ADMIN + "/grade-levels", map("name", "Grade 9", "displayOrder", 9,
                "active", true)), 201));
        Map<String, Object> body = examBody("Grade 9 science");
        body.put("gradeLevelId", emptyGrade);
        body.put("subjects", List.of(map("subjectId", science, "maxMarks", new BigDecimal("50"), "passMarks",
                new BigDecimal("20"))));
        ok(admin.post(ADMIN + "/exams", body), 201);

        assertThat(admin.delete(ADMIN + "/subjects/" + science).status()).isEqualTo(409);
        assertThat(admin.delete(ADMIN + "/grade-levels/" + emptyGrade).status()).isEqualTo(409);
    }
}
