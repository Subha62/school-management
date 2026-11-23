package com.schoolmanagement.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.ApiSession;
import com.schoolmanagement.support.ApiSession.Reply;
import com.schoolmanagement.support.PostgresIntegrationTest;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.service.UserProvisioner;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The academic module end to end: real security chain, JPA mappings and the Flyway-created schema. */
class AcademicFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String YEARS = "/api/v1/admin/academic-years";
    private static final String GRADES = "/api/v1/admin/grade-levels";
    private static final String SUBJECTS = "/api/v1/admin/subjects";
    private static final String SECTIONS = "/api/v1/admin/sections";
    private static final String ASSIGNMENTS = "/api/v1/admin/section-subjects";

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

    private UUID schoolId;
    private UUID teacherId;
    private UUID studentId;
    private ApiSession admin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table section_subjects, sections, subjects, grade_levels, academic_years, "
                + "refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
        schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        teacherId = provisioner.createActiveUser(schoolId, "T-1", "teacher@test.school", PASSWORD, "Tara",
                "Teacher", RoleName.ROLE_TEACHER).getId();
        studentId = provisioner.createActiveUser(schoolId, "S-1", "student@test.school", PASSWORD, "Sam",
                "Student", RoleName.ROLE_STUDENT).getId();
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);
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

    private UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    private JsonNode createYear(String name, String start, String end) throws Exception {
        return ok(admin.post(YEARS, map("name", name, "startDate", start, "endDate", end)), 201);
    }

    private UUID createActiveYear() throws Exception {
        UUID yearId = id(createYear("2026-2027", "2026-06-01", "2027-03-31"));
        ok(admin.post(YEARS + "/" + yearId + "/activate", null), 200);
        return yearId;
    }

    private UUID createGrade(String name, int order) throws Exception {
        return id(ok(admin.post(GRADES, map("name", name, "displayOrder", order, "active", true)), 201));
    }

    private UUID createSubject(String code, String name) throws Exception {
        return id(ok(admin.post(SUBJECTS, map("code", code, "name", name, "description", "", "active", true)), 201));
    }

    private JsonNode createSection(UUID yearId, UUID gradeId, String name, Object classTeacher) throws Exception {
        return ok(admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", gradeId, "name", name,
                "capacity", 40, "classTeacherId", classTeacher, "active", true)), 201);
    }

    // ---- academic years ----------------------------------------------------------------------------------

    @Test
    void academicYearLifecycleCreateActivateCloseAndRules() throws Exception {
        JsonNode year = createYear("2026-2027", "2026-06-01", "2027-03-31");
        assertThat(year.get("status").asText()).isEqualTo("PLANNED");
        UUID yearId = id(year);

        assertThat(ok(admin.post(YEARS + "/" + yearId + "/activate", null), 200).get("status").asText())
                .isEqualTo("ACTIVE");
        assertThat(ok(admin.post(YEARS + "/" + yearId + "/close", null), 200).get("status").asText())
                .isEqualTo("CLOSED");

        Reply update = admin.put(YEARS + "/" + yearId, map("name", "Renamed", "startDate", "2026-06-01",
                "endDate", "2027-03-31", "version", 2));
        assertThat(update.status()).isEqualTo(422);
        assertThat(update.detail()).contains("closed");
        assertThat(admin.delete(YEARS + "/" + yearId).status()).isEqualTo(422);
        assertThat(admin.post(YEARS + "/" + yearId + "/close", null).status()).isEqualTo(422);
    }

    @Test
    void onlyOneAcademicYearCanBeActiveAndActivationNeverClosesAnotherSilently() throws Exception {
        UUID first = createActiveYear();
        UUID second = id(createYear("2027-2028", "2027-06-01", "2028-03-31"));

        Reply blocked = admin.post(YEARS + "/" + second + "/activate", null);

        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.detail()).contains("already active");
        assertThat(ok(admin.get(YEARS + "/" + first), 200).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void databaseItselfRejectsASecondActiveYear() throws Exception {
        createActiveYear();
        UUID other = id(createYear("2027-2028", "2027-06-01", "2028-03-31"));

        assertThatThrownBy(() -> jdbc.update("update academic_years set status = 'ACTIVE' where id = ?", other))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void academicYearInputIsValidatedAndDuplicatesAndOverlapsAreRejected() throws Exception {
        createYear("2026-2027", "2026-06-01", "2027-03-31");

        Reply missing = admin.post(YEARS, map("name", " ", "startDate", "2028-06-01"));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("errors").findValuesAsText("field")).contains("name", "endDate");

        assertThat(admin.post(YEARS, map("name", "2028-2029", "startDate", "2029-03-31", "endDate", "2028-06-01"))
                .status()).isEqualTo(422);
        assertThat(admin.post(YEARS, map("name", "2026-2027", "startDate", "2030-06-01", "endDate", "2031-03-31"))
                .status()).as("same name, different case handled below").isEqualTo(409);
        assertThat(admin.post(YEARS, map("name", "  2026-2027 ", "startDate", "2030-06-01", "endDate", "2031-03-31"))
                .status()).isEqualTo(409);
        Reply upper = admin.post(YEARS, map("name", "2026-2027".toUpperCase(), "startDate", "2030-06-01",
                "endDate", "2031-03-31"));
        assertThat(upper.status()).isEqualTo(409);
        Reply overlap = admin.post(YEARS, map("name", "Overlapping", "startDate", "2027-01-01",
                "endDate", "2027-12-31"));
        assertThat(overlap.status()).isEqualTo(409);
        assertThat(overlap.detail()).contains("overlap");
    }

    @Test
    void plannedYearWithSectionsCannotBeDeletedButAnEmptyOneCan() throws Exception {
        UUID withSection = id(createYear("2026-2027", "2026-06-01", "2027-03-31"));
        UUID empty = id(createYear("2027-2028", "2027-06-01", "2028-03-31"));
        UUID grade = createGrade("Grade 1", 1);
        createSection(withSection, grade, "A", null);

        assertThat(admin.delete(YEARS + "/" + withSection).status()).isEqualTo(409);
        assertThat(admin.delete(YEARS + "/" + empty).status()).isEqualTo(204);
        assertThat(admin.get(YEARS + "/" + empty).status()).isEqualTo(404);
    }

    @Test
    void staleVersionIsRejectedAsAConflict() throws Exception {
        JsonNode year = createYear("2026-2027", "2026-06-01", "2027-03-31");
        UUID yearId = id(year);
        long version = year.get("version").asLong();

        Reply first = admin.put(YEARS + "/" + yearId, map("name", "2026-27", "startDate", "2026-06-01",
                "endDate", "2027-03-31", "version", version));
        Reply stale = admin.put(YEARS + "/" + yearId, map("name", "Another", "startDate", "2026-06-01",
                "endDate", "2027-03-31", "version", version));
        Reply noVersion = admin.put(YEARS + "/" + yearId, map("name", "Another", "startDate", "2026-06-01",
                "endDate", "2027-03-31"));

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("version").asLong()).isGreaterThan(version);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.detail()).contains("changed by someone else");
        assertThat(noVersion.status()).isEqualTo(400);
    }

    @Test
    void listsArePaginatedFilterableSortableAndBounded() throws Exception {
        createYear("2024-2025", "2024-06-01", "2025-03-31");
        createYear("2025-2026", "2025-06-01", "2026-03-31");
        createActiveYear();

        JsonNode page = ok(admin.get(YEARS + "?size=2&page=0"), 200);
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("first").asBoolean()).isTrue();
        assertThat(page.get("content").get(0).get("name").asText()).isEqualTo("2026-2027");

        assertThat(ok(admin.get(YEARS + "?status=ACTIVE"), 200).get("content")).hasSize(1);
        assertThat(ok(admin.get(YEARS + "?search=2025"), 200).get("content")).hasSize(2);
        assertThat(ok(admin.get(YEARS + "?search=%25"), 200).get("content")).as("wildcards are literal").isEmpty();
        assertThat(ok(admin.get(YEARS + "?sort=name&direction=asc"), 200).get("content").get(0).get("name")
                .asText()).isEqualTo("2024-2025");
        assertThat(admin.get(YEARS + "?size=101").status()).isEqualTo(400);
        assertThat(admin.get(YEARS + "?sort=schoolId").status()).isEqualTo(400);
        assertThat(admin.get(YEARS + "?status=NOPE").status()).isEqualTo(400);
    }

    // ---- grades and subjects --------------------------------------------------------------------------------

    @Test
    void gradesAreOrderedUniqueCaseInsensitivelyAndProtectedWhenUsed() throws Exception {
        UUID grade9 = createGrade("Grade 9", 9);
        createGrade("Grade 8", 8);

        JsonNode list = ok(admin.get(GRADES), 200).get("content");
        assertThat(list.findValuesAsText("name")).containsExactly("Grade 8", "Grade 9");
        assertThat(admin.post(GRADES, map("name", "GRADE 8", "displayOrder", 1, "active", true)).status())
                .isEqualTo(409);
        assertThat(admin.post(GRADES, map("name", "X", "displayOrder", -1, "active", true)).status())
                .isEqualTo(400);

        UUID yearId = createActiveYear();
        createSection(yearId, grade9, "A", null);
        Reply used = admin.delete(GRADES + "/" + grade9);
        assertThat(used.status()).isEqualTo(409);
        assertThat(used.detail()).contains("inactive");
    }

    @Test
    void subjectCodesAreNormalisedUniqueCaseInsensitivelyAndSearchable() throws Exception {
        UUID math = createSubject("math101", "Mathematics");
        createSubject("ENG1", "English");

        assertThat(ok(admin.get(SUBJECTS + "/" + math), 200).get("code").asText()).isEqualTo("MATH101");
        assertThat(admin.post(SUBJECTS, map("code", "Math101", "name", "Other", "active", true)).status())
                .isEqualTo(409);
        assertThat(admin.post(SUBJECTS, map("code", "bad code!", "name", "Other", "active", true)).status())
                .isEqualTo(400);
        assertThat(ok(admin.get(SUBJECTS + "?search=engl"), 200).get("content")).hasSize(1);
        assertThat(ok(admin.get(SUBJECTS + "?search=math"), 200).get("content")).hasSize(1);
        assertThat(ok(admin.get(SUBJECTS + "?active=false"), 200).get("content")).isEmpty();
    }

    @Test
    void renamingToAnExistingSubjectCodeIsRejectedButKeepingYourOwnIsFine() throws Exception {
        JsonNode math = ok(admin.post(SUBJECTS, map("code", "MATH", "name", "Maths", "active", true)), 201);
        createSubject("ENG", "English");
        long version = math.get("version").asLong();

        assertThat(admin.put(SUBJECTS + "/" + id(math), map("code", "eng", "name", "Maths", "active", true,
                "version", version)).status()).isEqualTo(409);
        assertThat(admin.put(SUBJECTS + "/" + id(math), map("code", "math", "name", "Mathematics", "active", true,
                "version", version)).status()).isEqualTo(200);
    }

    // ---- sections ------------------------------------------------------------------------------------------

    @Test
    void sectionNamesAreUniquePerGradeAndYearOnly() throws Exception {
        UUID yearId = createActiveYear();
        UUID grade1 = createGrade("Grade 1", 1);
        UUID grade2 = createGrade("Grade 2", 2);
        createSection(yearId, grade1, "A", null);

        Reply duplicate = admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", grade1, "name", "a",
                "capacity", 30, "active", true));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.detail()).contains("Section a already exists").contains("Grade 1");
        assertThat(createSection(yearId, grade2, "A", null).get("gradeLevel").get("name").asText())
                .isEqualTo("Grade 2");
    }

    @Test
    void sectionRulesClosedYearInactiveGradeAndCapacity() throws Exception {
        UUID yearId = createActiveYear();
        UUID grade = createGrade("Grade 1", 1);
        UUID inactive = id(ok(admin.post(GRADES, map("name", "Old", "displayOrder", 0, "active", false)), 201));

        assertThat(admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", inactive, "name", "A",
                "capacity", 30, "active", true)).status()).isEqualTo(422);
        assertThat(admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", grade, "name", "A",
                "capacity", 0, "active", true)).status()).isEqualTo(400);
        assertThat(admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", grade, "name", "A",
                "capacity", 501, "active", true)).status()).isEqualTo(400);
        assertThat(admin.post(SECTIONS, map("academicYearId", UUID.randomUUID(), "gradeLevelId", grade,
                "name", "A", "capacity", 30, "active", true)).status()).isEqualTo(404);

        createSection(yearId, grade, "A", null);
        ok(admin.post(YEARS + "/" + yearId + "/close", null), 200);
        Reply closed = admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", grade, "name", "B",
                "capacity", 30, "active", true));
        assertThat(closed.status()).isEqualTo(422);
        assertThat(closed.detail()).contains("closed");
    }

    @Test
    void classTeacherMustBeAnActiveTeacherOfTheSchool() throws Exception {
        UUID yearId = createActiveYear();
        UUID grade = createGrade("Grade 1", 1);

        Reply student = admin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", grade, "name", "A",
                "capacity", 30, "classTeacherId", studentId, "active", true));
        assertThat(student.status()).isEqualTo(422);
        assertThat(student.detail()).contains("teacher is not available");

        JsonNode section = createSection(yearId, grade, "B", teacherId);
        assertThat(section.get("classTeacher").get("name").asText()).isEqualTo("Tara Teacher");

        jdbc.update("update users set account_status = 'SUSPENDED' where id = ?", teacherId);
        Reply suspended = admin.put(SECTIONS + "/" + id(section), map("name", "B", "capacity", 35,
                "classTeacherId", teacherId, "active", true, "version", section.get("version").asLong()));
        assertThat(suspended.status()).isEqualTo(422);
    }

    @Test
    void sectionListFiltersByYearAndGradeAndIncludesNamesWithoutExtraCalls() throws Exception {
        UUID year = createActiveYear();
        UUID grade1 = createGrade("Grade 1", 1);
        UUID grade2 = createGrade("Grade 2", 2);
        createSection(year, grade2, "B", teacherId);
        createSection(year, grade1, "A", null);
        createSection(year, grade1, "B", null);

        JsonNode all = ok(admin.get(SECTIONS + "?academicYearId=" + year), 200).get("content");
        assertThat(all).hasSize(3);
        assertThat(all.get(0).get("gradeLevel").get("name").asText()).isEqualTo("Grade 1");
        assertThat(all.get(2).get("classTeacher").get("name").asText()).isEqualTo("Tara Teacher");
        assertThat(ok(admin.get(SECTIONS + "?gradeLevelId=" + grade1), 200).get("content")).hasSize(2);
        assertThat(ok(admin.get(SECTIONS + "?search=b"), 200).get("content")).hasSize(2);
        assertThat(ok(admin.get(SECTIONS + "?size=1&page=2"), 200).get("last").asBoolean()).isTrue();
    }

    @Test
    void sectionCanBeUpdatedAndDeletedUntilSubjectsAreAssigned() throws Exception {
        UUID year = createActiveYear();
        UUID grade = createGrade("Grade 1", 1);
        UUID subject = createSubject("MATH", "Mathematics");
        JsonNode section = createSection(year, grade, "A", null);
        UUID sectionId = id(section);

        JsonNode updated = ok(admin.put(SECTIONS + "/" + sectionId, map("name", "A", "capacity", 45,
                "classTeacherId", teacherId, "active", true, "version", section.get("version").asLong())), 200);
        assertThat(updated.get("capacity").asInt()).isEqualTo(45);

        ok(admin.post(ASSIGNMENTS, map("sectionId", sectionId, "subjectId", subject, "weeklyPeriods", 5)), 201);
        assertThat(admin.delete(SECTIONS + "/" + sectionId).status()).isEqualTo(409);
    }

    // ---- teacher assignments ----------------------------------------------------------------------------------

    @Test
    void subjectAssignmentWorkflow() throws Exception {
        UUID year = createActiveYear();
        UUID grade = createGrade("Grade 8", 8);
        UUID section = id(createSection(year, grade, "A", null));
        UUID math = createSubject("MATH", "Mathematics");

        JsonNode assignment = ok(admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", math,
                "teacherId", teacherId, "weeklyPeriods", 6)), 201);
        assertThat(assignment.get("teacher").get("name").asText()).isEqualTo("Tara Teacher");
        assertThat(assignment.get("subject").get("code").asText()).isEqualTo("MATH");
        assertThat(assignment.get("gradeLevelName").asText()).isEqualTo("Grade 8");

        Reply duplicate = admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", math, "weeklyPeriods", 2));
        assertThat(duplicate.status()).isEqualTo(409);

        JsonNode changed = ok(admin.put(ASSIGNMENTS + "/" + id(assignment), map("teacherId", null,
                "weeklyPeriods", 4, "active", true, "version", assignment.get("version").asLong())), 200);
        assertThat(changed.get("teacher").isNull()).isTrue();
        assertThat(changed.get("weeklyPeriods").asInt()).isEqualTo(4);

        assertThat(ok(admin.get(ASSIGNMENTS + "?sectionId=" + section), 200).get("content")).hasSize(1);
        assertThat(ok(admin.get(ASSIGNMENTS + "?teacherId=" + teacherId), 200).get("content")).isEmpty();
        assertThat(admin.delete(ASSIGNMENTS + "/" + id(assignment)).status()).isEqualTo(204);
    }

    @Test
    void assignmentRulesAreEnforced() throws Exception {
        UUID year = createActiveYear();
        UUID grade = createGrade("Grade 8", 8);
        UUID section = id(createSection(year, grade, "A", null));
        UUID math = createSubject("MATH", "Mathematics");
        UUID retired = id(ok(admin.post(SUBJECTS, map("code", "OLD", "name", "Retired", "active", false)), 201));

        assertThat(admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", retired, "weeklyPeriods", 2))
                .status()).isEqualTo(422);
        assertThat(admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", math, "teacherId", studentId,
                "weeklyPeriods", 2)).status()).isEqualTo(422);
        assertThat(admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", math, "weeklyPeriods", 0))
                .status()).isEqualTo(400);
        assertThat(admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", math, "weeklyPeriods", 41))
                .status()).isEqualTo(400);
        assertThat(admin.post(ASSIGNMENTS, map("sectionId", UUID.randomUUID(), "subjectId", math,
                "weeklyPeriods", 2)).status()).isEqualTo(404);

        JsonNode assignment = ok(admin.post(ASSIGNMENTS, map("sectionId", section, "subjectId", math,
                "weeklyPeriods", 3)), 201);
        assertThat(admin.delete(SUBJECTS + "/" + math).status()).as("subject in use").isEqualTo(409);

        ok(admin.post(YEARS + "/" + year + "/close", null), 200);
        assertThat(admin.put(ASSIGNMENTS + "/" + id(assignment), map("weeklyPeriods", 2, "version",
                assignment.get("version").asLong())).status()).isEqualTo(422);
        assertThat(admin.delete(ASSIGNMENTS + "/" + id(assignment)).status()).isEqualTo(422);
    }

    @Test
    void databaseRejectsAnAssignmentWhoseYearDiffersFromTheSectionsYear() throws Exception {
        UUID year = createActiveYear();
        UUID otherYear = id(createYear("2030-2031", "2030-06-01", "2031-03-31"));
        UUID grade = createGrade("Grade 8", 8);
        UUID section = id(createSection(year, grade, "A", null));
        UUID math = createSubject("MATH", "Mathematics");

        assertThatThrownBy(() -> jdbc.update("insert into section_subjects (school_id, academic_year_id, "
                + "section_id, subject_id, weekly_periods) values (?, ?, ?, ?, 3)", schoolId, otherYear, section,
                math)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void teacherOptionsListOnlyActiveTeachersAndSupportSearch() throws Exception {
        provisioner.createActiveUser(schoolId, "T-2", "kim@test.school", PASSWORD, "Kim", "Mentor",
                RoleName.ROLE_TEACHER);
        UUID retired = provisioner.createActiveUser(schoolId, "T-3", "old@test.school", PASSWORD, "Old", "Teacher",
                RoleName.ROLE_TEACHER).getId();
        jdbc.update("update users set account_status = 'ARCHIVED' where id = ?", retired);

        JsonNode all = ok(admin.get("/api/v1/admin/teacher-options"), 200).get("content");
        assertThat(all.findValuesAsText("email")).containsExactly("kim@test.school", "teacher@test.school");
        assertThat(all.toString()).doesNotContain("student@test.school").doesNotContain("passwordHash");
        assertThat(ok(admin.get("/api/v1/admin/teacher-options?search=KIM"), 200).get("content")).hasSize(1);
    }

    // ---- authorization and isolation -----------------------------------------------------------------------------

    @Test
    void onlyAdministratorsCanUseTheAcademicEndpoints() throws Exception {
        ApiSession teacher = ApiSession.login(mockMvc, objectMapper, "teacher@test.school", PASSWORD);
        ApiSession student = ApiSession.login(mockMvc, objectMapper, "student@test.school", PASSWORD);
        ApiSession anonymous = ApiSession.anonymous(mockMvc, objectMapper);
        String[] paths = {YEARS, GRADES, SUBJECTS, SECTIONS, ASSIGNMENTS, "/api/v1/admin/teacher-options"};

        for (String path : paths) {
            assertThat(teacher.get(path).status()).as("teacher GET " + path).isEqualTo(403);
            assertThat(student.get(path).status()).as("student GET " + path).isEqualTo(403);
            assertThat(anonymous.get(path).status()).as("anonymous GET " + path).isEqualTo(401);
        }
        assertThat(teacher.post(YEARS, map("name", "X", "startDate", "2026-01-01", "endDate", "2026-12-31"))
                .status()).isEqualTo(403);
        assertThat(student.post(GRADES, map("name", "X", "displayOrder", 1, "active", true)).status())
                .isEqualTo(403);
        assertThat(teacher.delete(SUBJECTS + "/" + UUID.randomUUID()).status()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from academic_years", Integer.class)).isZero();
    }

    @Test
    void dataOfAnotherSchoolIsInvisibleAndUntouchable() throws Exception {
        UUID yearId = createActiveYear();
        UUID gradeId = createGrade("Grade 1", 1);
        UUID otherSchool = schools.save(new School("Other School", "OTHER", "UTC")).getId();
        provisioner.createActiveUser(otherSchool, "admin", "admin@other.school", PASSWORD, "Olga", "Other",
                RoleName.ROLE_ADMIN);
        ApiSession otherAdmin = ApiSession.login(mockMvc, objectMapper, "admin@other.school", PASSWORD);

        assertThat(ok(otherAdmin.get(YEARS), 200).get("content")).isEmpty();
        assertThat(otherAdmin.get(YEARS + "/" + yearId).status()).isEqualTo(404);
        assertThat(otherAdmin.post(YEARS + "/" + yearId + "/close", null).status()).isEqualTo(404);
        assertThat(otherAdmin.delete(GRADES + "/" + gradeId).status()).isEqualTo(404);
        assertThat(otherAdmin.post(SECTIONS, map("academicYearId", yearId, "gradeLevelId", gradeId, "name", "A",
                "capacity", 30, "active", true)).status()).isEqualTo(404);
        assertThat(ok(admin.get(YEARS + "/" + yearId), 200).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void serverIgnoresUnknownFieldsSoSchoolOrStatusCannotBeInjected() throws Exception {
        Reply reply = admin.post(YEARS, map("name", "2026-2027", "startDate", "2026-06-01", "endDate",
                "2027-03-31", "status", "ACTIVE", "schoolId", UUID.randomUUID()));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from academic_years", Integer.class)).isZero();
    }
}
