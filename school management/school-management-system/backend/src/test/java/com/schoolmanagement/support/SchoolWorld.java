package com.schoolmanagement.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.ApiSession.Reply;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.service.UserProvisioner;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A small school for integration tests: an administrator, a class teacher who also teaches mathematics, a student
 * enrolled in their class, and an active academic year. Everything is created through the real API.
 */
public final class SchoolWorld {

    public static final String PASSWORD = "Correct-Pass-1!";
    public static final String ADMIN_API = "/api/v1/admin";

    public ApiSession admin;
    public ApiSession teacher;
    public ApiSession student;
    public UUID schoolId;
    public UUID teacherId;
    public UUID year;
    public UUID grade;
    public UUID section;
    public UUID math;
    public UUID studentId;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public SchoolWorld(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public static void wipe(JdbcTemplate jdbc) {
        jdbc.execute("truncate table audit_logs, payment_reversals, payments, student_fees, fee_structures, "
                + "fee_categories, receipt_counters, announcement_reads, announcements, exam_results, exam_comments, "
                + "student_marks, exam_subjects, exams, grade_rules, submission_attachments, assignment_submissions, "
                + "assignment_attachments, assignments, attendance_corrections, attendance_records, "
                + "attendance_sessions, timetable_entries, student_enrollments, promotion_batches, student_guardians, "
                + "guardians, student_profiles, teacher_profiles, section_subjects, sections, subjects, grade_levels, "
                + "academic_years, refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
    }

    public SchoolWorld build(SchoolRepository schools, UserProvisioner provisioner, String schoolName,
            String prefix) throws Exception {
        schoolId = schools.save(new School(schoolName, prefix.toUpperCase(), "UTC")).getId();
        provisioner.createActiveUser(schoolId, prefix + "-admin", prefix + "-admin@test.school", PASSWORD, "Ada",
                "Admin", RoleName.ROLE_ADMIN);
        teacherId = provisioner.createActiveUser(schoolId, prefix + "-T1", prefix + "-t1@test.school", PASSWORD,
                "Milo", "Math", RoleName.ROLE_TEACHER).getId();
        admin = ApiSession.login(mockMvc, objectMapper, prefix + "-admin@test.school", PASSWORD);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        year = id(ok(admin.post(ADMIN_API + "/academic-years", map("name", "2026-2027", "startDate",
                today.minusDays(60).toString(), "endDate", today.plusDays(300).toString())), 201));
        ok(admin.post(ADMIN_API + "/academic-years/" + year + "/activate", null), 200);
        grade = id(ok(admin.post(ADMIN_API + "/grade-levels", map("name", "Grade 8", "displayOrder", 8, "active",
                true)), 201));
        math = id(ok(admin.post(ADMIN_API + "/subjects", map("code", "MATH", "name", "Mathematics", "description",
                "", "active", true)), 201));
        section = id(ok(admin.post(ADMIN_API + "/sections", map("academicYearId", year, "gradeLevelId", grade,
                "name", "A", "capacity", 40, "classTeacherId", teacherId, "active", true)), 201));
        ok(admin.post(ADMIN_API + "/section-subjects", map("sectionId", section, "subjectId", math, "teacherId",
                teacherId, "weeklyPeriods", 5, "active", true)), 201);
        studentId = id(ok(admin.post(ADMIN_API + "/students", map("admissionNumber", prefix + "-S1", "password",
                PASSWORD, "firstName", "Ann", "lastName", "Pupil", "email", prefix + "-s1@test.school",
                "dateOfBirth", "2012-04-10", "gender", "MALE", "admissionDate", "2026-06-01", "addressLine1",
                "1 Main Road", "city", "Pune", "emergencyContactName", "Parent", "emergencyContactPhone",
                "+91 98765 00000")), 201));
        ok(admin.post(ADMIN_API + "/enrollments", map("studentId", studentId, "sectionId", section)), 201);
        teacher = ApiSession.login(mockMvc, objectMapper, prefix + "-t1@test.school", PASSWORD);
        student = ApiSession.login(mockMvc, objectMapper, prefix + "-S1", PASSWORD);
        return this;
    }

    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    public static JsonNode ok(Reply reply, int expectedStatus) {
        assertThat(reply.status()).as(reply.raw()).isEqualTo(expectedStatus);
        return reply.body();
    }

    public static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }
}
