package com.schoolmanagement.people;

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

/** Students, teachers, guardians, enrollments, promotion and user management against the real schema. */
class PeopleFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String BASE = "/api/v1/admin";

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
    private ApiSession admin;
    private UUID year1;
    private UUID year2;
    private UUID grade8;
    private UUID grade9;
    private UUID section8A;
    private UUID section8B;
    private UUID section9A;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table student_enrollments, promotion_batches, student_guardians, guardians, "
                + "student_profiles, teacher_profiles, section_subjects, sections, subjects, grade_levels, "
                + "academic_years, refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
        schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);

        year1 = id(ok(admin.post(BASE + "/academic-years",
                map("name", "2026-2027", "startDate", "2026-06-01", "endDate", "2027-03-31")), 201));
        ok(admin.post(BASE + "/academic-years/" + year1 + "/activate", null), 200);
        year2 = id(ok(admin.post(BASE + "/academic-years",
                map("name", "2027-2028", "startDate", "2027-06-01", "endDate", "2028-03-31")), 201));
        grade8 = id(ok(admin.post(BASE + "/grade-levels", map("name", "Grade 8", "displayOrder", 8, "active", true)), 201));
        grade9 = id(ok(admin.post(BASE + "/grade-levels", map("name", "Grade 9", "displayOrder", 9, "active", true)), 201));
        section8A = section(year1, grade8, "A", 2);
        section8B = section(year1, grade8, "B", 5);
        section9A = section(year2, grade9, "A", 5);
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

    private UUID section(UUID yearId, UUID gradeId, String name, int capacity) throws Exception {
        return id(ok(admin.post(BASE + "/sections", map("academicYearId", yearId, "gradeLevelId", gradeId,
                "name", name, "capacity", capacity, "classTeacherId", null, "active", true)), 201));
    }

    private Map<String, Object> studentBody(String admission, String email) {
        return map("admissionNumber", admission, "password", PASSWORD, "firstName", "Sam", "lastName", "Student",
                "email", email, "phoneNumber", "+91 98765 43210", "dateOfBirth", "2012-04-10", "gender", "MALE",
                "admissionDate", "2026-06-01", "bloodGroup", "O+", "addressLine1", "1 Main Road", "city", "Pune",
                "emergencyContactName", "Parent", "emergencyContactPhone", "+91 98765 00000");
    }

    private JsonNode createStudent(String admission) throws Exception {
        return ok(admin.post(BASE + "/students", studentBody(admission, admission.toLowerCase() + "@test.school")), 201);
    }

    private Map<String, Object> teacherBody(String employeeId, String email) {
        return map("employeeId", employeeId, "password", PASSWORD, "firstName", "Tara", "lastName", employeeId,
                "email", email, "joiningDate", "2020-06-01", "qualification", "M.Sc.", "specialization", "Maths",
                "employmentStatus", "ACTIVE");
    }

    private JsonNode enroll(UUID studentId, UUID sectionId, Integer roll) throws Exception {
        return ok(admin.post(BASE + "/enrollments", map("studentId", studentId, "sectionId", sectionId,
                "rollNumber", roll)), 201);
    }

    private JsonNode guardian(String first) throws Exception {
        return ok(admin.post(BASE + "/guardians", map("firstName", first, "lastName", "Parent",
                "relationship", "MOTHER", "email", first.toLowerCase() + "@mail.test", "phoneNumber", "+91 90000 11111",
                "emergencyContact", true)), 201);
    }

    private Map<String, Object> link(boolean primary, UUID guardianId) {
        return map("guardianId", guardianId, "primaryGuardian", primary, "receivesNotifications", true,
                "authorizedPickup", false);
    }

    // ---- students ----------------------------------------------------------------------------------------

    @Test
    void studentCreationBuildsAccountAndEnforcesUniqueness() throws Exception {
        JsonNode student = createStudent("S-100");
        assertThat(student.get("admissionNumber").asText()).isEqualTo("S-100");
        assertThat(student.get("currentEnrollment").isNull()).isTrue();
        assertThat(student.has("password")).isFalse();

        // The admission number is the sign-in ID.
        assertThat(ApiSession.login(mockMvc, objectMapper, "S-100", PASSWORD)).isNotNull();

        assertThat(admin.post(BASE + "/students", studentBody("s-100", "other@test.school")).status()).isEqualTo(409);
        assertThat(admin.post(BASE + "/students", studentBody("S-101", "s-100@test.school")).status()).isEqualTo(409);

        Map<String, Object> weak = studentBody("S-102", "s-102@test.school");
        weak.put("password", "short");
        Reply weakReply = admin.post(BASE + "/students", weak);
        assertThat(weakReply.status()).isEqualTo(400);
        assertThat(weakReply.raw()).contains("password");

        Map<String, Object> noNumber = studentBody("", "s-103@test.school");
        Reply missing = admin.post(BASE + "/students", noNumber);
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.raw()).contains("admissionNumber");
    }

    @Test
    void studentUpdateChecksVersionAndWithdrawalEndsEnrollment() throws Exception {
        JsonNode student = createStudent("S-200");
        UUID studentId = id(student);
        JsonNode enrollment = enroll(studentId, section8B, null);
        assertThat(enrollment.get("rollNumber").asInt()).isEqualTo(1);

        Map<String, Object> update = studentBody("S-200", "s-200@test.school");
        update.put("firstName", "Renamed");
        update.put("status", "ACTIVE");
        update.put("version", student.get("version").asLong());
        JsonNode updated = ok(admin.put(BASE + "/students/" + studentId, update), 200);
        assertThat(updated.get("firstName").asText()).isEqualTo("Renamed");
        assertThat(updated.get("currentEnrollment").get("section").asText()).isEqualTo("B");

        // The same (now stale) version is refused.
        assertThat(admin.put(BASE + "/students/" + studentId, update).status()).isEqualTo(409);

        update.put("status", "WITHDRAWN");
        update.put("version", updated.get("version").asLong());
        JsonNode withdrawn = ok(admin.put(BASE + "/students/" + studentId, update), 200);
        assertThat(withdrawn.get("currentEnrollment").isNull()).isTrue();
        JsonNode history = ok(admin.get(BASE + "/students/" + studentId + "/enrollments"), 200);
        assertThat(history.get(0).get("status").asText()).isEqualTo("WITHDRAWN");

        Reply again = admin.post(BASE + "/enrollments", map("studentId", studentId, "sectionId", section8A));
        assertThat(again.status()).isEqualTo(422);
    }

    @Test
    void studentSearchMatchesNameAndNumber() throws Exception {
        createStudent("S-300");
        createStudent("S-301");
        JsonNode byNumber = ok(admin.get(BASE + "/students?search=s-301"), 200);
        assertThat(byNumber.get("totalElements").asInt()).isEqualTo(1);
        JsonNode byName = ok(admin.get(BASE + "/students?search=sam&status=ACTIVE&sort=admissionNumber"), 200);
        assertThat(byName.get("totalElements").asInt()).isEqualTo(2);
        assertThat(admin.get(BASE + "/students?sort=password").status()).isEqualTo(400);
    }

    // ---- teachers ----------------------------------------------------------------------------------------

    @Test
    void teacherLifecycleAndAssignmentEligibility() throws Exception {
        JsonNode teacher = ok(admin.post(BASE + "/teachers", teacherBody("T-100", "t-100@test.school")), 201);
        UUID userId = UUID.fromString(teacher.get("userId").asText());
        assertThat(ApiSession.login(mockMvc, objectMapper, "T-100", PASSWORD)).isNotNull();
        assertThat(admin.post(BASE + "/teachers", teacherBody("T-100", "x@test.school")).status()).isEqualTo(409);

        Map<String, Object> sectionUpdate = map("name", "B", "capacity", 5, "classTeacherId", userId, "active", true,
                "version", ok(admin.get(BASE + "/sections/" + section8B), 200).get("version").asLong());
        ok(admin.put(BASE + "/sections/" + section8B, sectionUpdate), 200);

        // A teacher who left can no longer be assigned.
        Map<String, Object> leave = teacherBody("T-100", "t-100@test.school");
        leave.put("employmentStatus", "RESIGNED");
        leave.put("version", teacher.get("version").asLong());
        ok(admin.put(BASE + "/teachers/" + id(teacher), leave), 200);
        sectionUpdate.put("version", ok(admin.get(BASE + "/sections/" + section8B), 200).get("version").asLong());
        assertThat(admin.put(BASE + "/sections/" + section8B, sectionUpdate).status()).isEqualTo(422);

        JsonNode resigned = ok(admin.get(BASE + "/teachers?status=RESIGNED&search=t-100"), 200);
        assertThat(resigned.get("totalElements").asInt()).isEqualTo(1);
    }

    // ---- guardians ---------------------------------------------------------------------------------------

    @Test
    void guardianLinksKeepExactlyOnePrimary() throws Exception {
        UUID studentId = id(createStudent("S-400"));
        UUID mother = id(guardian("Maya"));
        UUID father = id(guardian("Ravi"));
        String path = BASE + "/students/" + studentId + "/guardians";

        // The first guardian becomes primary even if not flagged.
        JsonNode first = ok(admin.post(path, link(false, mother)), 201);
        assertThat(first.get("primaryGuardian").asBoolean()).isTrue();
        assertThat(admin.post(path, link(false, mother)).status()).isEqualTo(409);

        JsonNode second = ok(admin.post(path, link(true, father)), 201);
        assertThat(second.get("primaryGuardian").asBoolean()).isTrue();
        JsonNode links = ok(admin.get(path), 200);
        assertThat(primaryCount(links)).isEqualTo(1);

        assertThat(admin.delete(BASE + "/guardians/" + mother).status()).isEqualTo(409);

        // Removing the primary promotes the remaining guardian.
        assertThat(admin.delete(path + "/" + id(second)).status()).isEqualTo(204);
        JsonNode remaining = ok(admin.get(path), 200);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).get("primaryGuardian").asBoolean()).isTrue();
        assertThat(admin.delete(BASE + "/guardians/" + father).status()).isEqualTo(204);
        assertThat(ok(admin.get(BASE + "/guardians/" + mother), 200).get("studentCount").asInt()).isEqualTo(1);
    }

    private static int primaryCount(JsonNode links) {
        int count = 0;
        for (JsonNode link : links) {
            if (link.get("primaryGuardian").asBoolean()) {
                count++;
            }
        }
        return count;
    }

    // ---- enrollment --------------------------------------------------------------------------------------

    @Test
    void enrollmentRespectsCapacityRollNumbersAndTransfers() throws Exception {
        UUID a = id(createStudent("S-500"));
        UUID b = id(createStudent("S-501"));
        UUID c = id(createStudent("S-502"));

        JsonNode first = enroll(a, section8A, null);
        assertThat(first.get("rollNumber").asInt()).isEqualTo(1);
        assertThat(admin.post(BASE + "/enrollments", map("studentId", b, "sectionId", section8A, "rollNumber", 1))
                .status()).isEqualTo(409);
        assertThat(enroll(b, section8A, null).get("rollNumber").asInt()).isEqualTo(2);

        Reply full = admin.post(BASE + "/enrollments", map("studentId", c, "sectionId", section8A));
        assertThat(full.status()).isEqualTo(422);
        assertThat(full.detail()).contains("full");

        // One active enrollment per year: use transfer instead.
        assertThat(admin.post(BASE + "/enrollments", map("studentId", a, "sectionId", section8B)).status())
                .isEqualTo(409);

        // Capacity cannot drop below the enrolled count, and a used section cannot be deleted.
        long version = ok(admin.get(BASE + "/sections/" + section8A), 200).get("version").asLong();
        assertThat(admin.put(BASE + "/sections/" + section8A, map("name", "A", "capacity", 1, "active", true,
                "version", version)).status()).isEqualTo(422);
        assertThat(admin.delete(BASE + "/sections/" + section8A).status()).isEqualTo(409);

        JsonNode moved = ok(admin.post(BASE + "/enrollments/" + id(first) + "/transfer",
                map("targetSectionId", section8B)), 200);
        assertThat(moved.get("section").get("name").asText()).isEqualTo("B");
        assertThat(moved.get("sourceEnrollmentId").asText()).isEqualTo(id(first).toString());
        // The freed seat can be used again.
        assertThat(enroll(c, section8A, null).get("status").asText()).isEqualTo("ACTIVE");

        JsonNode history = ok(admin.get(BASE + "/students/" + a + "/enrollments"), 200);
        assertThat(history).hasSize(2);
        assertThat(statuses(history)).containsExactlyInAnyOrder("ACTIVE", "TRANSFERRED");

        // Transfers stay inside one year, and ended enrollments cannot be changed again.
        assertThat(admin.post(BASE + "/enrollments/" + id(moved) + "/transfer", map("targetSectionId", section9A))
                .status()).isEqualTo(422);
        assertThat(admin.post(BASE + "/enrollments/" + id(first) + "/withdraw", null).status()).isEqualTo(422);
        assertThat(ok(admin.post(BASE + "/enrollments/" + id(moved) + "/withdraw", null), 200)
                .get("status").asText()).isEqualTo("WITHDRAWN");

        JsonNode filtered = ok(admin.get(BASE + "/enrollments?sectionId=" + section8B + "&status=WITHDRAWN&search=500"), 200);
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
    }

    private static List<String> statuses(JsonNode rows) {
        List<String> result = new ArrayList<>();
        rows.forEach(row -> result.add(row.get("status").asText()));
        return result;
    }

    // ---- promotion ---------------------------------------------------------------------------------------

    @Test
    void promotionMovesStudentsAndReportsFailuresPerStudent() throws Exception {
        UUID a = id(createStudent("S-600"));
        UUID b = id(createStudent("S-601"));
        UUID c = id(createStudent("S-602"));
        UUID d = id(createStudent("S-603"));
        enroll(a, section8B, null);
        enroll(b, section8B, null);
        enroll(c, section8B, null);
        // Student d is already in the target year; the target section 9B only has one seat.
        enroll(d, section8B, null);
        enroll(d, section9A, null);
        UUID section9B = section(year2, grade9, "B", 1);
        Map<String, Object> request = map("sourceAcademicYearId", year1, "targetAcademicYearId", year2, "items", List.of(
                map("studentId", a, "targetSectionId", section9A),
                map("studentId", b, "targetSectionId", section9B),
                map("studentId", c, "targetSectionId", section9B),
                map("studentId", d, "targetSectionId", section9A)));
        JsonNode summary = ok(admin.post(BASE + "/promotions", request), 201);
        assertThat(summary.get("total").asInt()).isEqualTo(4);
        assertThat(summary.get("promoted").asInt()).isEqualTo(2);
        assertThat(summary.get("failed").asInt()).isEqualTo(2);
        JsonNode results = summary.get("results");
        assertThat(results.get(0).get("outcome").asText()).isEqualTo("PROMOTED");
        assertThat(results.get(1).get("outcome").asText()).isEqualTo("PROMOTED");
        assertThat(results.get(2).get("reason").asText()).contains("full");
        assertThat(results.get(3).get("reason").asText()).contains("already enrolled");

        // Promoted students keep their history; the failed one is untouched.
        JsonNode historyA = ok(admin.get(BASE + "/students/" + a + "/enrollments"), 200);
        assertThat(statuses(historyA)).containsExactlyInAnyOrder("ACTIVE", "PROMOTED");
        JsonNode historyC = ok(admin.get(BASE + "/students/" + c + "/enrollments"), 200);
        assertThat(statuses(historyC)).containsExactly("ACTIVE");
        assertThat(ok(admin.get(BASE + "/students/" + a), 200).get("currentEnrollment").get("academicYear").asText())
                .isEqualTo("2027-2028");

        JsonNode batches = ok(admin.get(BASE + "/promotions"), 200);
        assertThat(batches.get("totalElements").asInt()).isEqualTo(1);
        assertThat(batches.get("content").get(0).get("performedBy").asText()).isEqualTo("Ada Admin");
        assertThat(batches.get("content").get(0).get("promoted").asInt()).isEqualTo(2);
    }

    @Test
    void promotionRejectsInvalidRequests() throws Exception {
        UUID a = id(createStudent("S-610"));
        enroll(a, section8B, null);
        Map<String, Object> item = map("studentId", a, "targetSectionId", section9A);

        assertThat(admin.post(BASE + "/promotions", map("sourceAcademicYearId", year1,
                "targetAcademicYearId", year1, "items", List.of(item))).status()).isEqualTo(422);
        assertThat(admin.post(BASE + "/promotions", map("sourceAcademicYearId", year2,
                "targetAcademicYearId", year1, "items", List.of(item))).status()).isEqualTo(422);
        assertThat(admin.post(BASE + "/promotions", map("sourceAcademicYearId", year1,
                "targetAcademicYearId", year2, "items", List.of(item, item))).status()).isEqualTo(422);
        assertThat(admin.post(BASE + "/promotions", map("sourceAcademicYearId", year1,
                "targetAcademicYearId", year2, "items", List.of())).status()).isEqualTo(400);

        // A target section of the wrong year is reported for the student, not applied.
        JsonNode wrongYear = ok(admin.post(BASE + "/promotions", map("sourceAcademicYearId", year1,
                "targetAcademicYearId", year2, "items", List.of(map("studentId", a, "targetSectionId", section8A)))), 201);
        assertThat(wrongYear.get("promoted").asInt()).isZero();
        assertThat(wrongYear.get("results").get(0).get("reason").asText()).contains("does not belong");
    }

    // ---- user management ---------------------------------------------------------------------------------

    @Test
    void userManagementCreatesAdminsAndControlsAccess() throws Exception {
        createStudent("S-700");
        JsonNode students = ok(admin.get(BASE + "/users?role=ROLE_STUDENT"), 200);
        assertThat(students.get("totalElements").asInt()).isEqualTo(1);
        assertThat(students.get("content").get(0).get("roles").get(0).asText()).isEqualTo("ROLE_STUDENT");
        assertThat(ok(admin.get(BASE + "/users?search=admin"), 200).get("totalElements").asInt()).isEqualTo(1);

        JsonNode created = ok(admin.post(BASE + "/users/admins", map("firstName", "Bea", "lastName", "Boss",
                "email", "bea@test.school", "password", PASSWORD)), 201);
        assertThat(created.get("roles").get(0).asText()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.post(BASE + "/users/admins", map("firstName", "Bea", "lastName", "Boss",
                "email", "BEA@test.school", "password", PASSWORD)).status()).isEqualTo(409);
        assertThat(admin.post(BASE + "/users/admins", map("firstName", "Cy", "lastName", "Weak",
                "email", "cy@test.school", "password", "weak")).status()).isEqualTo(400);

        ApiSession bea = ApiSession.login(mockMvc, objectMapper, "bea@test.school", PASSWORD);
        UUID beaId = id(created);
        assertThat(bea.get(BASE + "/users").status()).isEqualTo(200);

        JsonNode suspended = ok(admin.post(BASE + "/users/" + beaId + "/status",
                map("status", "SUSPENDED", "version", created.get("version").asLong())), 200);
        assertThat(suspended.get("accountStatus").asText()).isEqualTo("SUSPENDED");
        // The session ends at once and signing in again is refused.
        assertThat(bea.get(BASE + "/users").status()).isEqualTo(401);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper)
                .post("/api/v1/auth/login", map("identifier", "bea@test.school", "password", PASSWORD)).status())
                .isEqualTo(401);

        // Stale versions, unsupported statuses and self-changes are refused.
        assertThat(admin.post(BASE + "/users/" + beaId + "/status",
                map("status", "ACTIVE", "version", created.get("version").asLong())).status()).isEqualTo(409);
        assertThat(admin.post(BASE + "/users/" + beaId + "/status",
                map("status", "LOCKED", "version", suspended.get("version").asLong())).status()).isEqualTo(422);
        UUID adminId = UUID.fromString(ok(admin.get(BASE + "/users?search=ada"), 200)
                .get("content").get(0).get("id").asText());
        assertThat(admin.post(BASE + "/users/" + adminId + "/status", map("status", "ARCHIVED", "version", 0))
                .status()).isEqualTo(422);

        JsonNode reactivated = ok(admin.post(BASE + "/users/" + beaId + "/status",
                map("status", "ACTIVE", "version", suspended.get("version").asLong())), 200);
        assertThat(reactivated.get("accountStatus").asText()).isEqualTo("ACTIVE");
        assertThat(ok(admin.post(BASE + "/users/" + beaId + "/unlock", null), 200).get("temporarilyLocked")
                .asBoolean()).isFalse();
    }

    // ---- access control ----------------------------------------------------------------------------------

    @Test
    void onlyAdministratorsOfTheSameSchoolHaveAccess() throws Exception {
        UUID studentId = id(createStudent("S-800"));
        provisioner.createActiveUser(schoolId, "T-800", "t800@test.school", PASSWORD, "Tess", "Teacher",
                RoleName.ROLE_TEACHER);
        UUID otherSchool = schools.save(new School("Other", "OTHER", "UTC")).getId();
        provisioner.createActiveUser(otherSchool, "admin2", "admin@other.school", PASSWORD, "Olga", "Other",
                RoleName.ROLE_ADMIN);

        ApiSession teacher = ApiSession.login(mockMvc, objectMapper, "t800@test.school", PASSWORD);
        ApiSession student = ApiSession.login(mockMvc, objectMapper, "S-800", PASSWORD);
        ApiSession anonymous = ApiSession.anonymous(mockMvc, objectMapper);
        ApiSession outsider = ApiSession.login(mockMvc, objectMapper, "admin@other.school", PASSWORD);

        for (String path : List.of("/students", "/teachers", "/guardians", "/enrollments", "/promotions", "/users")) {
            assertThat(teacher.get(BASE + path).status()).as(path).isEqualTo(403);
            assertThat(student.get(BASE + path).status()).as(path).isEqualTo(403);
            assertThat(anonymous.get(BASE + path).status()).as(path).isEqualTo(401);
        }
        assertThat(student.post(BASE + "/students", studentBody("S-801", "s801@test.school")).status()).isEqualTo(403);

        // Another school's administrator sees nothing of this school.
        assertThat(outsider.get(BASE + "/students/" + studentId).status()).isEqualTo(404);
        assertThat(ok(outsider.get(BASE + "/students"), 200).get("totalElements").asInt()).isZero();
        assertThat(outsider.post(BASE + "/enrollments", map("studentId", studentId, "sectionId", section8A))
                .status()).isEqualTo(404);
        assertThat(ok(outsider.get(BASE + "/users"), 200).get("totalElements").asInt()).isEqualTo(1);
    }
}
