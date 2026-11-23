package com.schoolmanagement.operations;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Timetable and attendance end to end: security chain, JPA mappings and the Flyway-created schema. */
class OperationsFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String BASE = "/api/v1/admin";
    private static final String T_ATT = "/api/v1/teacher/attendance";

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

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    private UUID schoolId;
    private UUID classTeacherId;
    private UUID mathTeacherId;
    private ApiSession admin;
    private ApiSession classTeacher;
    private ApiSession mathTeacher;
    private UUID year;
    private UUID mathId;
    private UUID englishId;
    private UUID sectionA;
    private UUID sectionB;
    private UUID ann;
    private UUID bob;
    private UUID cat;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table attendance_corrections, attendance_records, attendance_sessions, "
                + "timetable_entries, student_enrollments, promotion_batches, student_guardians, guardians, "
                + "student_profiles, teacher_profiles, section_subjects, sections, subjects, grade_levels, "
                + "academic_years, refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
        schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        classTeacherId = provisioner.createActiveUser(schoolId, "T-1", "t1@test.school", PASSWORD, "Cora",
                "Class", RoleName.ROLE_TEACHER).getId();
        mathTeacherId = provisioner.createActiveUser(schoolId, "T-2", "t2@test.school", PASSWORD, "Milo", "Math",
                RoleName.ROLE_TEACHER).getId();
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);
        classTeacher = ApiSession.login(mockMvc, objectMapper, "t1@test.school", PASSWORD);
        mathTeacher = ApiSession.login(mockMvc, objectMapper, "t2@test.school", PASSWORD);

        year = id(ok(admin.post(BASE + "/academic-years", map("name", "2026-2027", "startDate",
                today.minusDays(60).toString(), "endDate", today.plusDays(120).toString())), 201));
        ok(admin.post(BASE + "/academic-years/" + year + "/activate", null), 200);
        UUID grade = id(ok(admin.post(BASE + "/grade-levels", map("name", "Grade 8", "displayOrder", 8,
                "active", true)), 201));
        mathId = id(ok(admin.post(BASE + "/subjects", map("code", "MATH", "name", "Mathematics",
                "description", "", "active", true)), 201));
        englishId = id(ok(admin.post(BASE + "/subjects", map("code", "ENG", "name", "English",
                "description", "", "active", true)), 201));
        sectionA = id(ok(admin.post(BASE + "/sections", map("academicYearId", year, "gradeLevelId", grade,
                "name", "A", "capacity", 40, "classTeacherId", classTeacherId, "active", true)), 201));
        sectionB = id(ok(admin.post(BASE + "/sections", map("academicYearId", year, "gradeLevelId", grade,
                "name", "B", "capacity", 40, "classTeacherId", null, "active", true)), 201));
        for (UUID section : List.of(sectionA, sectionB)) {
            assign(section, mathId, mathTeacherId);
        }
        assign(sectionA, englishId, classTeacherId);

        ann = student("S-1", "Ann");
        bob = student("S-2", "Bob");
        cat = student("S-3", "Cat");
        for (UUID student : List.of(ann, bob, cat)) {
            ok(admin.post(BASE + "/enrollments", map("studentId", student, "sectionId", sectionA)), 201);
        }
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
        ok(admin.post(BASE + "/section-subjects", map("sectionId", section, "subjectId", subject, "teacherId",
                teacher, "weeklyPeriods", 5, "active", true)), 201);
    }

    private UUID student(String admission, String first) throws Exception {
        return id(ok(admin.post(BASE + "/students", map("admissionNumber", admission, "password", PASSWORD,
                "firstName", first, "lastName", "Pupil", "email", admission.toLowerCase() + "@test.school",
                "dateOfBirth", "2012-04-10", "gender", "MALE", "admissionDate", "2026-06-01", "addressLine1",
                "1 Main Road", "city", "Pune", "emergencyContactName", "Parent", "emergencyContactPhone",
                "+91 98765 00000")), 201));
    }

    private Map<String, Object> slot(UUID section, UUID subject, String day, String start, String end,
            String room) {
        return map("sectionId", section, "subjectId", subject, "teacherId", null, "room", room, "dayOfWeek", day,
                "periodNumber", 1, "startTime", start, "endTime", end, "active", true);
    }

    private Reply addSlot(UUID section, UUID subject, String day, String start, String end, String room)
            throws Exception {
        return admin.post(BASE + "/timetable-entries", slot(section, subject, day, start, end, room));
    }

    private JsonNode open(ApiSession who, UUID section, LocalDate date, UUID subject, int expected)
            throws Exception {
        return ok(who.post(T_ATT + "/sessions", map("sectionId", section, "date", date.toString(), "subjectId",
                subject)), expected);
    }

    private Map<String, Object> mark(UUID student, String status) {
        return map("studentId", student, "status", status, "remarks", null);
    }

    private JsonNode markAll(ApiSession who, UUID session, String annStatus, String bobStatus, String catStatus)
            throws Exception {
        return ok(who.put(T_ATT + "/sessions/" + session + "/records", map("records",
                List.of(mark(ann, annStatus), mark(bob, bobStatus), mark(cat, catStatus)))), 200);
    }

    private UUID finalizedDaily(LocalDate date, String annStatus, String bobStatus, String catStatus)
            throws Exception {
        UUID session = id(open(classTeacher, sectionA, date, null, 201).get("session"));
        markAll(classTeacher, session, annStatus, bobStatus, catStatus);
        ok(classTeacher.post(T_ATT + "/sessions/" + session + "/finalize", null), 200);
        return session;
    }

    private static JsonNode row(JsonNode report, String admission) {
        for (JsonNode row : report.get("rows")) {
            if (row.get("admissionNumber").asText().equals(admission)) {
                return row;
            }
        }
        throw new AssertionError("No report row for " + admission);
    }

    // ---- timetable -------------------------------------------------------------------------------------

    @Test
    void conflictsOfSectionTeacherAndRoomAreRefused() throws Exception {
        JsonNode first = ok(addSlot(sectionA, mathId, "MONDAY", "09:00", "09:45", "R1"), 201);
        assertThat(first.get("teacherName").asText()).isEqualTo("Milo Math");

        // Same class, overlapping time.
        Reply sameSection = addSlot(sectionA, englishId, "MONDAY", "09:30", "10:15", null);
        assertThat(sameSection.status()).isEqualTo(409);
        assertThat(sameSection.detail()).contains("already has Mathematics");

        // Same teacher in another class.
        Reply sameTeacher = addSlot(sectionB, mathId, "MONDAY", "09:15", "10:00", "R2");
        assertThat(sameTeacher.status()).isEqualTo(409);
        assertThat(sameTeacher.detail()).contains("Milo Math is already teaching");

        // Same room (a different teacher teaches English in section A, so use a free slot for section A).
        Reply sameRoom = addSlot(sectionA, englishId, "MONDAY", "09:10", "09:40", "r1");
        assertThat(sameRoom.status()).isEqualTo(409);

        // Touching times, other days and other rooms are fine.
        ok(addSlot(sectionA, englishId, "MONDAY", "09:45", "10:30", "R1"), 201);
        ok(addSlot(sectionB, mathId, "TUESDAY", "09:00", "09:45", "R1"), 201);
        ok(addSlot(sectionB, mathId, "MONDAY", "11:00", "11:45", null), 201);
    }

    @Test
    void entriesWithDifferentEffectiveDatesDoNotClashAndInactiveOnesAreIgnored() throws Exception {
        Map<String, Object> early = slot(sectionA, mathId, "WEDNESDAY", "09:00", "09:45", null);
        early.put("effectiveFrom", today.minusDays(30).toString());
        early.put("effectiveTo", today.minusDays(10).toString());
        ok(admin.post(BASE + "/timetable-entries", early), 201);

        Map<String, Object> later = slot(sectionA, mathId, "WEDNESDAY", "09:00", "09:45", null);
        later.put("effectiveFrom", today.minusDays(9).toString());
        ok(admin.post(BASE + "/timetable-entries", later), 201);

        Map<String, Object> overlapping = slot(sectionA, mathId, "WEDNESDAY", "09:00", "09:45", null);
        overlapping.put("effectiveFrom", today.minusDays(20).toString());
        overlapping.put("effectiveTo", today.minusDays(15).toString());
        assertThat(admin.post(BASE + "/timetable-entries", overlapping).status()).isEqualTo(409);

        // An inactive entry never blocks anything, and cannot block when it is switched on later unless free.
        Map<String, Object> inactive = slot(sectionA, mathId, "WEDNESDAY", "09:00", "09:45", null);
        inactive.put("active", false);
        JsonNode saved = ok(admin.post(BASE + "/timetable-entries", inactive), 201);
        assertThat(saved.get("active").asBoolean()).isFalse();
        Map<String, Object> activate = slot(sectionA, mathId, "WEDNESDAY", "09:00", "09:45", null);
        activate.put("version", saved.get("version").asLong());
        assertThat(admin.put(BASE + "/timetable-entries/" + id(saved), activate).status()).isEqualTo(409);
    }

    @Test
    void entryRulesAreValidated() throws Exception {
        // English is not taught in section B.
        Reply unassigned = addSlot(sectionB, englishId, "MONDAY", "09:00", "09:45", null);
        assertThat(unassigned.status()).isEqualTo(422);
        assertThat(unassigned.detail()).contains("not assigned");

        assertThat(addSlot(sectionA, mathId, "MONDAY", "10:00", "09:00", null).status()).isEqualTo(400);

        Map<String, Object> outside = slot(sectionA, mathId, "MONDAY", "09:00", "09:45", null);
        outside.put("effectiveFrom", today.minusDays(200).toString());
        assertThat(admin.post(BASE + "/timetable-entries", outside).status()).isEqualTo(400);

        // An update carries the version and keeps its own slot free of self-conflict.
        JsonNode saved = ok(addSlot(sectionA, mathId, "MONDAY", "09:00", "09:45", "R1"), 201);
        Map<String, Object> change = slot(sectionA, mathId, "MONDAY", "09:00", "10:00", "R1");
        change.put("version", saved.get("version").asLong());
        ok(admin.put(BASE + "/timetable-entries/" + id(saved), change), 200);
        assertThat(admin.put(BASE + "/timetable-entries/" + id(saved), change).status()).isEqualTo(409);

        // A used subject assignment and section cannot be removed.
        JsonNode assignments = ok(admin.get(BASE + "/section-subjects?sectionId=" + sectionA + "&subjectId="
                + mathId), 200);
        UUID assignmentId = id(assignments.get("content").get(0));
        assertThat(admin.delete(BASE + "/section-subjects/" + assignmentId).status()).isEqualTo(409);
        assertThat(admin.delete(BASE + "/sections/" + sectionA).status()).isEqualTo(409);

        assertThat(admin.delete(BASE + "/timetable-entries/" + id(saved)).status()).isEqualTo(204);
        assertThat(admin.delete(BASE + "/section-subjects/" + assignmentId).status()).isEqualTo(204);
    }

    @Test
    void weeklyViewsShowTheRightPeopleTheirOwnEntries() throws Exception {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ok(addSlot(sectionA, mathId, "MONDAY", "09:00", "09:45", "R1"), 201);
        ok(addSlot(sectionB, mathId, "TUESDAY", "09:00", "09:45", "R1"), 201);
        ok(addSlot(sectionA, englishId, "FRIDAY", "10:00", "10:45", null), 201);

        JsonNode sectionWeek = ok(admin.get(BASE + "/timetable/sections/" + sectionA + "?date=" + monday), 200);
        assertThat(sectionWeek.get("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(sectionWeek.get("entries")).hasSize(2);
        assertThat(sectionWeek.get("entries").get(0).get("date").asText()).isEqualTo(monday.toString());

        // The week may be any day in it.
        JsonNode sameWeek = ok(admin.get(BASE + "/timetable/sections/" + sectionA + "?date="
                + monday.plusDays(4)), 200);
        assertThat(sameWeek.get("weekStart").asText()).isEqualTo(monday.toString());

        JsonNode teacherWeek = ok(admin.get(BASE + "/timetable/teachers/" + mathTeacherId + "?date=" + monday), 200);
        assertThat(teacherWeek.get("entries")).hasSize(2);

        // A teacher sees their own week only, and a student the week of their section.
        assertThat(ok(mathTeacher.get("/api/v1/teacher/timetable?date=" + monday), 200).get("entries")).hasSize(2);
        assertThat(ok(classTeacher.get("/api/v1/teacher/timetable?date=" + monday), 200).get("entries"))
                .hasSize(1);
        ApiSession ann = ApiSession.login(mockMvc, objectMapper, "S-1", PASSWORD);
        assertThat(ok(ann.get("/api/v1/student/timetable?date=" + monday), 200).get("entries")).hasSize(2);

        // Entries that start in the middle of the week only show from that date on.
        Map<String, Object> thursdayStart = slot(sectionA, mathId, "THURSDAY", "11:00", "11:45", null);
        thursdayStart.put("effectiveFrom", monday.plusDays(3).toString());
        ok(admin.post(BASE + "/timetable-entries", thursdayStart), 201);
        Map<String, Object> mondayOnlyFromNextWeek = slot(sectionA, mathId, "MONDAY", "13:00", "13:45", null);
        mondayOnlyFromNextWeek.put("effectiveFrom", monday.plusDays(7).toString());
        ok(admin.post(BASE + "/timetable-entries", mondayOnlyFromNextWeek), 201);
        assertThat(ok(admin.get(BASE + "/timetable/sections/" + sectionA + "?date=" + monday), 200)
                .get("entries")).hasSize(3);

        // Boundaries of access.
        assertThat(ann.get("/api/v1/teacher/timetable").status()).isEqualTo(403);
        assertThat(mathTeacher.get("/api/v1/student/timetable").status()).isEqualTo(403);
        assertThat(mathTeacher.get(BASE + "/timetable-entries").status()).isEqualTo(403);
        assertThat(admin.get(BASE + "/timetable/teachers/" + UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(admin.get(BASE + "/timetable/sections/" + sectionA + "?date=nonsense").status()).isEqualTo(400);
    }

    // ---- attendance ------------------------------------------------------------------------------------

    @Test
    void onlyTheResponsibleTeacherCanOpenASessionAndOpeningIsIdempotent() throws Exception {
        JsonNode created = open(classTeacher, sectionA, today, null, 201);
        UUID sessionId = id(created.get("session"));
        assertThat(created.get("session").get("type").asText()).isEqualTo("DAILY");
        assertThat(created.get("entries")).hasSize(3);
        assertThat(created.get("entries").get(0).get("status").isNull()).isTrue();

        JsonNode again = open(classTeacher, sectionA, today, null, 200);
        assertThat(id(again.get("session"))).isEqualTo(sessionId);

        // Daily attendance belongs to the class teacher; a subject teacher takes only their subject.
        open(mathTeacher, sectionA, today, null, 403);
        UUID mathSession = id(open(mathTeacher, sectionA, today, mathId, 201).get("session"));
        assertThat(mathSession).isNotEqualTo(sessionId);
        open(mathTeacher, sectionA, today, englishId, 403);
        open(classTeacher, sectionB, today, null, 403);

        // Another teacher cannot read or change the session either.
        assertThat(mathTeacher.get(T_ATT + "/sessions/" + sessionId).status()).isEqualTo(403);
        assertThat(mathTeacher.put(T_ATT + "/sessions/" + sessionId + "/records",
                map("records", List.of(mark(ann, "PRESENT")))).status()).isEqualTo(403);

        assertThat(ok(classTeacher.get(T_ATT + "/classes"), 200).get(0).get("daily").asBoolean()).isTrue();
        JsonNode mathClasses = ok(mathTeacher.get(T_ATT + "/classes"), 200);
        assertThat(mathClasses).hasSize(2);
        assertThat(mathClasses.get(0).get("daily").asBoolean()).isFalse();
        assertThat(mathClasses.get(0).get("subjects").get(0).get("name").asText()).isEqualTo("Mathematics");
    }

    @Test
    void sessionDatesAreLimited() throws Exception {
        assertThat(classTeacher.post(T_ATT + "/sessions", map("sectionId", sectionA, "date",
                today.plusDays(1).toString())).status()).isEqualTo(400);
        Reply old = classTeacher.post(T_ATT + "/sessions", map("sectionId", sectionA, "date",
                today.minusDays(8).toString()));
        assertThat(old.status()).isEqualTo(422);
        assertThat(old.detail()).contains("last 7 days");
        // Before the academic year started is a validation problem, not a back-dating limit.
        assertThat(classTeacher.post(T_ATT + "/sessions", map("sectionId", sectionA, "date",
                today.minusDays(90).toString())).status()).isEqualTo(400);
        // A period only makes sense for subject attendance.
        assertThat(classTeacher.post(T_ATT + "/sessions", map("sectionId", sectionA, "date", today.toString(),
                "periodNumber", 2)).status()).isEqualTo(400);
        open(classTeacher, sectionA, today.minusDays(7), null, 201);
    }

    @Test
    void markingNeedsEnrolledStudentsAndFinalizingNeedsEveryoneMarked() throws Exception {
        UUID session = id(open(classTeacher, sectionA, today, null, 201).get("session"));
        UUID outsider = student("S-9", "Zed");

        Reply notEnrolled = classTeacher.put(T_ATT + "/sessions/" + session + "/records",
                map("records", List.of(mark(ann, "PRESENT"), mark(outsider, "PRESENT"))));
        assertThat(notEnrolled.status()).isEqualTo(422);
        assertThat(notEnrolled.detail()).contains("not enrolled");
        assertThat(classTeacher.put(T_ATT + "/sessions/" + session + "/records",
                map("records", List.of(mark(ann, "PRESENT"), mark(ann, "ABSENT")))).status()).isEqualTo(422);
        assertThat(classTeacher.put(T_ATT + "/sessions/" + session + "/records",
                map("records", List.of(map("studentId", ann, "status", "SLEEPING")))).status()).isEqualTo(400);

        ok(classTeacher.put(T_ATT + "/sessions/" + session + "/records",
                map("records", List.of(mark(ann, "PRESENT"), mark(bob, "ABSENT")))), 200);
        Reply early = classTeacher.post(T_ATT + "/sessions/" + session + "/finalize", null);
        assertThat(early.status()).isEqualTo(422);
        assertThat(early.detail()).contains("1 student has not been marked");

        // Marking again replaces the earlier mark (one record per student and session).
        JsonNode detail = ok(classTeacher.put(T_ATT + "/sessions/" + session + "/records", map("records",
                List.of(mark(bob, "LATE"), mark(cat, "HALF_DAY")))), 200);
        assertThat(detail.get("session").get("counts").get("late").asInt()).isEqualTo(1);
        assertThat(detail.get("session").get("counts").get("absent").asInt()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attendance_records where session_id = ?::uuid",
                Integer.class, session.toString())).isEqualTo(3);

        JsonNode done = ok(classTeacher.post(T_ATT + "/sessions/" + session + "/finalize", null), 200);
        assertThat(done.get("session").get("status").asText()).isEqualTo("FINALIZED");

        // Finalized attendance is closed to teachers.
        Reply change = classTeacher.put(T_ATT + "/sessions/" + session + "/records",
                map("records", List.of(mark(ann, "ABSENT"))));
        assertThat(change.status()).isEqualTo(422);
        assertThat(change.detail()).contains("administrator");
        assertThat(classTeacher.post(T_ATT + "/sessions/" + session + "/finalize", null).status()).isEqualTo(422);
        assertThat(classTeacher.delete(T_ATT + "/sessions/" + session).status()).isEqualTo(422);
    }

    @Test
    void anOpenSessionCanBeDeletedAndReopened() throws Exception {
        UUID session = id(open(classTeacher, sectionA, today, null, 201).get("session"));
        markAll(classTeacher, session, "PRESENT", "PRESENT", "PRESENT");

        assertThat(classTeacher.delete(T_ATT + "/sessions/" + session).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from attendance_records", Integer.class)).isZero();
        open(classTeacher, sectionA, today, null, 201);
        JsonNode mine = ok(classTeacher.get(T_ATT + "/sessions"), 200);
        assertThat(mine.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void administratorsCorrectFinalizedAttendanceWithAReasonAndAHistory() throws Exception {
        UUID session = finalizedDaily(today, "PRESENT", "ABSENT", "PRESENT");
        String path = BASE + "/attendance/sessions/" + session;

        assertThat(admin.post(path + "/corrections", map("studentId", bob, "status", "EXCUSED")).status())
                .isEqualTo(400);
        assertThat(admin.post(path + "/corrections", map("studentId", bob, "status", "EXCUSED", "reason", "ok"))
                .status()).isEqualTo(400);

        JsonNode corrected = ok(admin.post(path + "/corrections", map("studentId", bob, "status", "EXCUSED",
                "remarks", "Doctor's note", "reason", "Medical certificate received")), 200);
        assertThat(corrected.get("session").get("counts").get("excused").asInt()).isEqualTo(1);
        assertThat(corrected.get("session").get("status").asText()).isEqualTo("FINALIZED");

        JsonNode history = ok(admin.get(path + "/corrections"), 200);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("previousStatus").asText()).isEqualTo("ABSENT");
        assertThat(history.get(0).get("newStatus").asText()).isEqualTo("EXCUSED");
        assertThat(history.get(0).get("correctedBy").asText()).isEqualTo("Ada Admin");
        assertThat(history.get(0).get("reason").asText()).isEqualTo("Medical certificate received");

        // Repeating the same value is refused, and so is a student who is not in the section.
        assertThat(admin.post(path + "/corrections", map("studentId", bob, "status", "EXCUSED", "remarks",
                "Doctor's note", "reason", "Again for no reason")).status()).isEqualTo(422);
        UUID outsider = student("S-9", "Zed");
        assertThat(admin.post(path + "/corrections", map("studentId", outsider, "status", "PRESENT", "reason",
                "Not in this class")).status()).isEqualTo(422);

        // Teachers have no access to corrections.
        assertThat(classTeacher.post(path + "/corrections", map("studentId", bob, "status", "PRESENT", "reason",
                "Trying my luck")).status()).isEqualTo(403);
        assertThat(classTeacher.get(path + "/corrections").status()).isEqualTo(403);

        JsonNode sessions = ok(admin.get(BASE + "/attendance/sessions?sectionId=" + sectionA + "&status=FINALIZED"),
                200);
        assertThat(sessions.get("totalElements").asInt()).isEqualTo(1);
        assertThat(sessions.get("content").get(0).get("enrolled").asInt()).isEqualTo(3);
    }

    @Test
    void reportsCountOnlyFinalizedSessionsAndFindStudentsBelowTheThreshold() throws Exception {
        finalizedDaily(today, "PRESENT", "ABSENT", "LATE");
        finalizedDaily(today.minusDays(1), "PRESENT", "ABSENT", "ABSENT");
        // An open session does not count.
        UUID open = id(open(classTeacher, sectionA, today.minusDays(2), null, 201).get("session"));
        markAll(classTeacher, open, "ABSENT", "ABSENT", "ABSENT");

        String range = "from=" + today.minusDays(3) + "&to=" + today;
        JsonNode low = ok(admin.get(BASE + "/attendance/reports/below-threshold?" + range), 200);
        assertThat(low.get("threshold").asInt()).isEqualTo(75);
        assertThat(low.get("rows")).hasSize(2);
        assertThat(low.get("rows").get(0).get("admissionNumber").asText()).isEqualTo("S-2");
        assertThat(low.get("rows").get(0).get("percentage").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(low.get("rows").get(1).get("admissionNumber").asText()).isEqualTo("S-3");
        assertThat(low.get("rows").get(1).get("percentage").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(low.get("rows").get(1).get("sectionName").asText()).isEqualTo("Grade 8 - A");

        JsonNode stricter = ok(admin.get(BASE + "/attendance/reports/below-threshold?" + range + "&threshold=40"),
                200);
        assertThat(stricter.get("rows")).hasSize(1);
        assertThat(admin.get(BASE + "/attendance/reports/below-threshold?threshold=0").status()).isEqualTo(400);
        assertThat(admin.get(BASE + "/attendance/reports/below-threshold?from=" + today + "&to="
                + today.minusDays(1)).status()).isEqualTo(400);

        // The monthly report lists every enrolled student of the section for the month of the given date.
        String month = YearMonth.from(today).toString();
        JsonNode monthly = ok(admin.get(BASE + "/attendance/reports/monthly?sectionId=" + sectionA + "&month="
                + month), 200);
        assertThat(monthly.get("rows")).hasSize(3);
        JsonNode annRow = row(monthly, "S-1");
        assertThat(annRow.get("present").asInt()).isPositive();
        assertThat(annRow.get("percentage").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(annRow.get("belowThreshold").asBoolean()).isFalse();
        assertThat(admin.get(BASE + "/attendance/reports/monthly?sectionId=" + sectionA + "&month=2026-13")
                .status()).isEqualTo(400);

        // Students with no finalized session have no percentage rather than 0%.
        JsonNode empty = ok(admin.get(BASE + "/attendance/reports/monthly?sectionId=" + sectionB), 200);
        assertThat(empty.get("rows")).isEmpty();
    }

    @Test
    void subjectAttendanceIsReportedSeparatelyAndTeachersSeeOnlyTheirClasses() throws Exception {
        UUID math = id(open(mathTeacher, sectionA, today, mathId, 201).get("session"));
        markAll(mathTeacher, math, "PRESENT", "ABSENT", "PRESENT");
        ok(mathTeacher.post(T_ATT + "/sessions/" + math + "/finalize", null), 200);
        // A second period of the same subject on the same day is a separate session.
        UUID period2 = id(ok(mathTeacher.post(T_ATT + "/sessions", map("sectionId", sectionA, "date",
                today.toString(), "subjectId", mathId, "periodNumber", 2)), 201).get("session"));
        assertThat(period2).isNotEqualTo(math);

        String query = "sectionId=" + sectionA + "&type=SUBJECT&subjectId=" + mathId;
        JsonNode subject = ok(mathTeacher.get(T_ATT + "/reports/monthly?" + query), 200);
        assertThat(row(subject, "S-2").get("percentage").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(ok(admin.get(BASE + "/attendance/reports/monthly?" + query), 200).get("type").asText())
                .isEqualTo("SUBJECT");
        // Daily report is empty because only subject attendance exists.
        assertThat(row(ok(admin.get(BASE + "/attendance/reports/monthly?sectionId=" + sectionA), 200), "S-2")
                .get("percentage").isNull()).isTrue();

        // The subject teacher cannot see the daily report; the class teacher can.
        assertThat(mathTeacher.get(T_ATT + "/reports/monthly?sectionId=" + sectionA).status()).isEqualTo(403);
        assertThat(mathTeacher.get(T_ATT + "/reports/monthly?sectionId=" + sectionB + "&type=SUBJECT&subjectId="
                + englishId).status()).isEqualTo(403);
        assertThat(classTeacher.get(T_ATT + "/reports/monthly?sectionId=" + sectionA).status()).isEqualTo(200);
    }

    @Test
    void studentsSeeOnlyTheirOwnFinalizedAttendance() throws Exception {
        finalizedDaily(today, "PRESENT", "ABSENT", "PRESENT");
        UUID openSession = id(open(classTeacher, sectionA, today.minusDays(1), null, 201).get("session"));
        markAll(classTeacher, openSession, "ABSENT", "ABSENT", "ABSENT");

        ApiSession bobSession = ApiSession.login(mockMvc, objectMapper, "S-2", PASSWORD);
        JsonNode month = ok(bobSession.get("/api/v1/student/attendance?month=" + YearMonth.from(today)), 200);
        assertThat(month.get("threshold").asInt()).isEqualTo(75);
        assertThat(month.get("days")).hasSize(1);
        assertThat(month.get("days").get(0).get("status").asText()).isEqualTo("ABSENT");
        assertThat(month.get("daily").get("percentage").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(month.get("subjectWise").get("percentage").isNull()).isTrue();

        ApiSession annSession = ApiSession.login(mockMvc, objectMapper, "S-1", PASSWORD);
        assertThat(ok(annSession.get("/api/v1/student/attendance"), 200).get("days").get(0).get("status").asText())
                .isEqualTo("PRESENT");
        assertThat(annSession.get("/api/v1/student/attendance?month=bad").status()).isEqualTo(400);
        assertThat(mathTeacher.get("/api/v1/student/attendance").status()).isEqualTo(403);
    }

    @Test
    void accessIsLimitedByRoleAndSchool() throws Exception {
        UUID session = finalizedDaily(today, "PRESENT", "PRESENT", "PRESENT");
        UUID otherSchool = schools.save(new School("Other", "OTHER", "UTC")).getId();
        provisioner.createActiveUser(otherSchool, "admin2", "admin@other.school", PASSWORD, "Olga", "Other",
                RoleName.ROLE_ADMIN);
        provisioner.createActiveUser(otherSchool, "T-9", "t9@other.school", PASSWORD, "Otto", "Other",
                RoleName.ROLE_TEACHER);
        ApiSession outsider = ApiSession.login(mockMvc, objectMapper, "admin@other.school", PASSWORD);
        ApiSession outsideTeacher = ApiSession.login(mockMvc, objectMapper, "t9@other.school", PASSWORD);
        ApiSession anonymous = ApiSession.anonymous(mockMvc, objectMapper);
        ApiSession pupil = ApiSession.login(mockMvc, objectMapper, "S-1", PASSWORD);

        for (String path : List.of(BASE + "/attendance/sessions", BASE + "/timetable-entries",
                BASE + "/attendance/reports/below-threshold")) {
            assertThat(anonymous.get(path).status()).as(path).isEqualTo(401);
            assertThat(pupil.get(path).status()).as(path).isEqualTo(403);
            assertThat(classTeacher.get(path).status()).as(path).isEqualTo(403);
        }
        assertThat(pupil.get(T_ATT + "/classes").status()).isEqualTo(403);

        assertThat(outsider.get(BASE + "/attendance/sessions/" + session).status()).isEqualTo(404);
        assertThat(ok(outsider.get(BASE + "/attendance/sessions"), 200).get("totalElements").asInt()).isZero();
        assertThat(outsider.get(BASE + "/attendance/reports/monthly?sectionId=" + sectionA).status()).isEqualTo(404);
        assertThat(outsider.post(BASE + "/timetable-entries", slot(sectionA, mathId, "MONDAY", "09:00", "09:45",
                null)).status()).isEqualTo(404);
        assertThat(outsideTeacher.post(T_ATT + "/sessions", map("sectionId", sectionA, "date", today.toString()))
                .status()).isEqualTo(404);
        assertThat(outsideTeacher.get(T_ATT + "/sessions/" + session).status()).isEqualTo(404);
    }
}
