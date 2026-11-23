package com.schoolmanagement.fee;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Fees end to end: set-up, billing, payments, reversals, waivers, reports and who may see financial data. */
class FeeFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String ADMIN = "/api/v1/admin";
    private static final String STUDENT = "/api/v1/student";

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
    private ApiSession teacher;
    private ApiSession ann;
    private ApiSession bob;
    private UUID year;
    private UUID grade;
    private UUID section;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate table payment_reversals, payments, student_fees, fee_structures, fee_categories, "
                + "receipt_counters, announcement_reads, announcements, exam_results, exam_comments, student_marks, "
                + "exam_subjects, exams, grade_rules, submission_attachments, assignment_submissions, "
                + "assignment_attachments, assignments, attendance_corrections, attendance_records, "
                + "attendance_sessions, timetable_entries, student_enrollments, promotion_batches, "
                + "student_guardians, guardians, student_profiles, teacher_profiles, section_subjects, sections, "
                + "subjects, grade_levels, academic_years, refresh_tokens, password_reset_tokens, user_roles, users, "
                + "schools cascade");
        UUID schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        provisioner.createActiveUser(schoolId, "T-1", "t1@test.school", PASSWORD, "Milo", "Math",
                RoleName.ROLE_TEACHER);
        admin = ApiSession.login(mockMvc, objectMapper, "admin@test.school", PASSWORD);

        today = LocalDate.now(ZoneOffset.UTC);
        year = id(ok(admin.post(ADMIN + "/academic-years", map("name", "2026-2027", "startDate",
                today.minusDays(60).toString(), "endDate", today.plusDays(300).toString())), 201));
        ok(admin.post(ADMIN + "/academic-years/" + year + "/activate", null), 200);
        grade = id(ok(admin.post(ADMIN + "/grade-levels", map("name", "Grade 8", "displayOrder", 8, "active", true)),
                201));
        section = id(ok(admin.post(ADMIN + "/sections", map("academicYearId", year, "gradeLevelId", grade, "name",
                "A", "capacity", 40, "classTeacherId", null, "active", true)), 201));
        enrolled("S-1", "Ann");
        enrolled("S-2", "Bob");
        teacher = ApiSession.login(mockMvc, objectMapper, "t1@test.school", PASSWORD);
        ann = ApiSession.login(mockMvc, objectMapper, "S-1", PASSWORD);
        bob = ApiSession.login(mockMvc, objectMapper, "S-2", PASSWORD);
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private void enrolled(String admission, String first) throws Exception {
        UUID student = id(ok(admin.post(ADMIN + "/students", map("admissionNumber", admission, "password", PASSWORD,
                "firstName", first, "lastName", "Pupil", "email", admission.toLowerCase() + "@test.school",
                "dateOfBirth", "2012-04-10", "gender", "MALE", "admissionDate", "2026-06-01", "addressLine1",
                "1 Main Road", "city", "Pune", "emergencyContactName", "Parent", "emergencyContactPhone",
                "+91 98765 00000")), 201));
        ok(admin.post(ADMIN + "/enrollments", map("studentId", student, "sectionId", section)), 201);
    }

    private UUID category(String name) throws Exception {
        return id(ok(admin.post(ADMIN + "/fee-categories", map("name", name, "description", "", "active", true)),
                201));
    }

    private UUID structure(UUID category, String amount, int dueInDays) throws Exception {
        return id(ok(admin.post(ADMIN + "/fee-structures", map("academicYearId", year, "gradeLevelId", grade,
                "feeCategoryId", category, "description", "", "amount", money(amount), "dueDate",
                today.plusDays(dueInDays).toString(), "active", true)), 201));
    }

    /** Creates a structure, bills the class, and returns the fee of the given student. */
    private UUID billed(String categoryName, String amount, int dueInDays, String admission) throws Exception {
        UUID structure = structure(category(categoryName), amount, dueInDays);
        ok(admin.post(ADMIN + "/fee-structures/" + structure + "/generate", null), 200);
        return feeOf(admission, categoryName);
    }

    private UUID feeOf(String admission, String categoryName) throws Exception {
        for (JsonNode fee : ok(admin.get(ADMIN + "/student-fees?search=" + admission + "&size=100"), 200)
                .get("content")) {
            if (fee.get("categoryName").asText().equals(categoryName)) {
                return id(fee);
            }
        }
        throw new IllegalStateException("no fee");
    }

    private Reply pay(UUID fee, String amount) throws Exception {
        return admin.post(ADMIN + "/student-fees/" + fee + "/payments", map("amount", money(amount), "method", "CASH",
                "reference", "", "paidOn", today.toString()));
    }

    private JsonNode fee(UUID fee) throws Exception {
        return ok(admin.get(ADMIN + "/student-fees/" + fee), 200).get("fee");
    }

    // ---- configuration ---------------------------------------------------------------------------------

    @Test
    void categoriesAndStructuresAreValidatedAndFrozenOnceStudentsAreBilled() throws Exception {
        UUID tuition = category("Tuition");
        assertThat(admin.post(ADMIN + "/fee-categories", map("name", "tuition", "active", true)).status())
                .as("names are unique ignoring case").isEqualTo(409);
        assertThat(admin.post(ADMIN + "/fee-categories", map("name", " ", "active", true)).status()).isEqualTo(400);

        assertThat(admin.post(ADMIN + "/fee-structures", map("academicYearId", year, "gradeLevelId", grade,
                "feeCategoryId", tuition, "amount", money("0"), "dueDate", today.toString(), "active", true))
                .status()).as("amount must be positive").isEqualTo(400);
        assertThat(admin.post(ADMIN + "/fee-structures", map("academicYearId", year, "gradeLevelId", grade,
                "feeCategoryId", tuition, "amount", money("10.005"), "dueDate", today.toString(), "active", true))
                .status()).as("at most two decimals").isEqualTo(400);
        UUID structure = structure(tuition, "1500.50", 30);
        assertThat(admin.post(ADMIN + "/fee-structures", map("academicYearId", year, "gradeLevelId", grade,
                "feeCategoryId", tuition, "amount", money("99"), "dueDate", today.plusDays(30).toString(), "active",
                true)).status()).as("same category, grade and due date").isEqualTo(409);

        JsonNode generated = ok(admin.post(ADMIN + "/fee-structures/" + structure + "/generate", null), 200);
        assertThat(generated.get("created").asInt()).isEqualTo(2);
        JsonNode again = ok(admin.post(ADMIN + "/fee-structures/" + structure + "/generate", null), 200);
        assertThat(again.get("created").asInt()).as("generating twice never bills twice").isZero();
        assertThat(again.get("alreadyBilled").asInt()).isEqualTo(2);
        enrolled("S-9", "Late");
        assertThat(ok(admin.post(ADMIN + "/fee-structures/" + structure + "/generate", null), 200).get("created")
                .asInt()).as("only the student enrolled since").isEqualTo(1);

        JsonNode current = ok(admin.get(ADMIN + "/fee-structures/" + structure), 200);
        assertThat(current.get("billedStudents").asInt()).isEqualTo(3);
        Map<String, Object> edit = map("description", "Term fee", "amount", money("2000"), "dueDate",
                today.plusDays(30).toString(), "active", true, "version", current.get("version").asLong());
        assertThat(admin.put(ADMIN + "/fee-structures/" + structure, edit).status())
                .as("the amount is frozen after billing").isEqualTo(422);
        edit.put("amount", money("1500.50"));
        assertThat(ok(admin.put(ADMIN + "/fee-structures/" + structure, edit), 200).get("description").asText())
                .isEqualTo("Term fee");
        assertThat(admin.delete(ADMIN + "/fee-structures/" + structure).status()).isEqualTo(409);
        assertThat(admin.delete(ADMIN + "/fee-categories/" + tuition).status()).isEqualTo(409);
        assertThat(admin.delete(ADMIN + "/grade-levels/" + grade).status()).isEqualTo(409);

        JsonNode fee = ok(ann.get(STUDENT + "/fees"), 200).get("content").get(0);
        assertThat(fee.get("amount").decimalValue()).as("the bill is a copy").isEqualByComparingTo("1500.50");
        UUID unused = structure(category("Library"), "50", 10);
        assertThat(admin.delete(ADMIN + "/fee-structures/" + unused).status()).isEqualTo(204);
    }

    @Test
    void statusesFollowThePaymentsAndTheDueDate() throws Exception {
        UUID notDue = billed("Annual", "1000", 60, "S-1");
        UUID due = billed("Term", "1000", 5, "S-1");
        UUID overdue = billed("Transport", "400", -3, "S-1");

        assertThat(fee(notDue).get("status").asText()).isEqualTo("NOT_DUE");
        assertThat(fee(due).get("status").asText()).isEqualTo("DUE");
        assertThat(fee(overdue).get("status").asText()).isEqualTo("OVERDUE");

        ok(pay(due, "400"), 201);
        assertThat(fee(due).get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(fee(due).get("balance").decimalValue()).isEqualByComparingTo("600");
        ok(pay(due, "600"), 201);
        assertThat(fee(due).get("status").asText()).isEqualTo("PAID");
        ok(pay(overdue, "100"), 201);
        assertThat(fee(overdue).get("status").asText()).as("still overdue until it is settled").isEqualTo("OVERDUE");

        for (String status : List.of("NOT_DUE", "DUE", "PARTIALLY_PAID", "PAID", "OVERDUE", "WAIVED")) {
            JsonNode listed = ok(admin.get(ADMIN + "/student-fees?status=" + status + "&studentId="
                    + fee(notDue).get("studentId").asText()), 200).get("content");
            for (JsonNode item : listed) {
                assertThat(item.get("status").asText()).as("the filter agrees with the displayed status")
                        .isEqualTo(status);
            }
            assertThat(listed).as(status).hasSize(Map.of("NOT_DUE", 1, "DUE", 0, "PARTIALLY_PAID", 0, "PAID", 1, "OVERDUE", 1, "WAIVED", 0).get(status));
        }
    }

    // ---- payments --------------------------------------------------------------------------------------

    @Test
    void paymentsMustBePositiveWithinTheBalanceAndGetUniqueReceipts() throws Exception {
        UUID tuition = billed("Tuition", "1000", 30, "S-1");
        UUID other = feeOf("S-2", "Tuition");

        assertThat(pay(tuition, "0").status()).isEqualTo(400);
        assertThat(pay(tuition, "-5").status()).isEqualTo(400);
        assertThat(pay(tuition, "10.505").status()).isEqualTo(400);
        assertThat(admin.post(ADMIN + "/student-fees/" + tuition + "/payments", map("amount", money("5"), "method",
                "BITCOIN", "paidOn", today.toString())).status()).isEqualTo(400);
        assertThat(admin.post(ADMIN + "/student-fees/" + tuition + "/payments", map("amount", money("5"), "method",
                "CASH", "paidOn", today.plusDays(1).toString())).status()).as("no future dates").isEqualTo(400);
        Reply over = pay(tuition, "1000.01");
        assertThat(over.status()).isEqualTo(422);
        assertThat(over.detail()).contains("more than the balance");

        JsonNode first = ok(pay(tuition, "250.25"), 201);
        JsonNode second = ok(pay(other, "100"), 201);
        JsonNode third = ok(pay(tuition, "749.75"), 201);
        String prefix = "REC-" + today.getYear() + "-";
        assertThat(first.get("receiptNumber").asText()).isEqualTo(prefix + "000001");
        assertThat(second.get("receiptNumber").asText()).isEqualTo(prefix + "000002");
        assertThat(third.get("receiptNumber").asText()).isEqualTo(prefix + "000003");
        assertThat(fee(tuition).get("balance").decimalValue()).isEqualByComparingTo("0");
        assertThat(pay(tuition, "0.01").status()).as("nothing more can be paid").isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(3);
        assertThat(ok(admin.get(ADMIN + "/payments?search=" + prefix + "000002"), 200).get("content")).hasSize(1);
        assertThat(ok(admin.get(ADMIN + "/payments?search=Ann"), 200).get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void twoCashiersCannotOverpayTheSameFeeAtTheSameTime() throws Exception {
        UUID tuition = billed("Tuition", "100", 30, "S-1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<Integer> task = () -> {
                start.await();
                return pay(tuition, "70").status();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        assertThat(fee(tuition).get("paidAmount").decimalValue()).isEqualByComparingTo("70");
        assertThat(jdbc.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void reversingAPaymentKeepsItAndRestoresTheBalance() throws Exception {
        UUID tuition = billed("Tuition", "1000", 30, "S-1");
        JsonNode payment = ok(pay(tuition, "600"), 201);
        UUID paymentId = id(payment);

        assertThat(admin.post(ADMIN + "/payments/" + paymentId + "/reverse", map("reason", "typo")).status())
                .as("the reason must be 5 characters or more").isEqualTo(400);
        assertThat(teacher.post(ADMIN + "/payments/" + paymentId + "/reverse", map("reason", "Wrong student"))
                .status()).isEqualTo(403);
        JsonNode reversed = ok(admin.post(ADMIN + "/payments/" + paymentId + "/reverse", map("reason",
                "Entered for the wrong student")), 200);
        assertThat(reversed.get("reversed").asBoolean()).isTrue();
        assertThat(reversed.get("reversedByName").asText()).isEqualTo("Ada Admin");
        assertThat(reversed.get("amount").decimalValue()).as("the original is untouched").isEqualByComparingTo("600");
        assertThat(admin.post(ADMIN + "/payments/" + paymentId + "/reverse", map("reason", "Again please")).status())
                .as("only once").isEqualTo(409);

        assertThat(fee(tuition).get("paidAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(fee(tuition).get("status").asText()).isEqualTo("NOT_DUE");
        JsonNode detail = ok(admin.get(ADMIN + "/student-fees/" + tuition), 200);
        assertThat(detail.get("payments")).as("history is kept").hasSize(1);
        assertThat(detail.get("payments").get(0).get("reversalReason").asText()).isEqualTo("Entered for the wrong student");
        assertThat(ok(admin.get(ADMIN + "/payments?reversed=true"), 200).get("content")).hasSize(1);
        assertThat(ok(admin.get(ADMIN + "/payments?reversed=false"), 200).get("content")).isEmpty();

        JsonNode again = ok(pay(tuition, "1000"), 201);
        assertThat(again.get("receiptNumber").asText()).as("receipts are never reused").endsWith("000002");
        assertThat(jdbc.queryForObject("select count(*) from payment_reversals", Integer.class)).isEqualTo(1);
    }

    @Test
    void waivingNeedsAReasonAndNoPaymentsAndStopsPayments() throws Exception {
        UUID tuition = billed("Tuition", "1000", 30, "S-1");
        UUID paid = billed("Transport", "200", 30, "S-1");
        ok(pay(paid, "50"), 201);

        assertThat(admin.post(ADMIN + "/student-fees/" + tuition + "/waive", map("reason", "")).status())
                .isEqualTo(400);
        assertThat(admin.post(ADMIN + "/student-fees/" + paid + "/waive", map("reason", "Scholarship")).status())
                .as("reverse payments first").isEqualTo(422);
        JsonNode waived = ok(admin.post(ADMIN + "/student-fees/" + tuition + "/waive", map("reason",
                "Merit scholarship")), 200);
        assertThat(waived.get("fee").get("status").asText()).isEqualTo("WAIVED");
        assertThat(waived.get("fee").get("waiveReason").asText()).isEqualTo("Merit scholarship");
        assertThat(pay(tuition, "10").status()).isEqualTo(422);
        assertThat(admin.post(ADMIN + "/student-fees/" + tuition + "/waive", map("reason", "Twice over")).status())
                .isEqualTo(422);

        JsonNode back = ok(admin.post(ADMIN + "/student-fees/" + tuition + "/unwaive", map("reason",
                "Entered in error")), 200);
        assertThat(back.get("fee").get("status").asText()).isEqualTo("NOT_DUE");
        assertThat(back.get("fee").get("waiveReason").isNull()).isTrue();
        ok(pay(tuition, "10"), 201);
    }

    // ---- who sees what ---------------------------------------------------------------------------------

    @Test
    void teachersHaveNoAccessToFinancialDataAndStudentsSeeOnlyTheirOwn() throws Exception {
        UUID annFee = billed("Tuition", "1000", 30, "S-1");
        UUID bobFee = feeOf("S-2", "Tuition");
        JsonNode annPayment = ok(pay(annFee, "300"), 201);
        ok(pay(bobFee, "100"), 201);

        for (String path : List.of("/fee-categories", "/fee-structures", "/student-fees", "/payments",
                "/student-fees/" + annFee, "/payments/" + id(annPayment) + "/receipt",
                "/fee-reports/summary?academicYearId=" + year, "/fee-reports/collections")) {
            assertThat(teacher.get(ADMIN + path).status()).as("teacher " + path).isEqualTo(403);
            assertThat(ann.get(ADMIN + path).status()).as("student " + path).isEqualTo(403);
        }
        assertThat(teacher.post(ADMIN + "/student-fees/" + annFee + "/payments", map("amount", money("1"), "method",
                "CASH", "paidOn", today.toString())).status()).isEqualTo(403);
        assertThat(teacher.get(STUDENT + "/fees").status()).isEqualTo(403);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper).get(STUDENT + "/fees").status()).isEqualTo(401);

        JsonNode mine = ok(ann.get(STUDENT + "/fees"), 200).get("content");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("studentName").asText()).isEqualTo("Ann Pupil");
        assertThat(ok(ann.get(STUDENT + "/fees/" + annFee), 200).get("payments")).hasSize(1);
        assertThat(ann.get(STUDENT + "/fees/" + bobFee).status()).as("another student's fee").isEqualTo(404);
        JsonNode receipt = ok(ann.get(STUDENT + "/payments/" + id(annPayment) + "/receipt"), 200);
        assertThat(receipt.get("studentName").asText()).isEqualTo("Ann Pupil");
        assertThat(receipt.get("amount").decimalValue()).isEqualByComparingTo("300");
        assertThat(receipt.get("balance").decimalValue()).isEqualByComparingTo("700");
        assertThat(receipt.get("className").asText()).isEqualTo("Grade 8 - A");
        assertThat(bob.get(STUDENT + "/payments/" + id(annPayment) + "/receipt").status()).isEqualTo(404);
        assertThat(ok(bob.get(STUDENT + "/fees?status=PAID"), 200).get("content")).isEmpty();
    }

    // ---- reports ---------------------------------------------------------------------------------------

    @Test
    void reportsAddUpBilledCollectedOutstandingAndOverdueNetOfReversals() throws Exception {
        UUID tuition = billed("Tuition", "1000", 30, "S-1");
        UUID bobTuition = feeOf("S-2", "Tuition");
        UUID transport = billed("Transport", "200", -2, "S-1");
        UUID bobTransport = feeOf("S-2", "Transport");
        ok(admin.post(ADMIN + "/student-fees/" + bobTransport + "/waive", map("reason", "Walks to school")), 200);
        ok(pay(tuition, "400"), 201);
        JsonNode mistaken = ok(pay(bobTuition, "300"), 201);
        ok(admin.post(ADMIN + "/payments/" + id(mistaken) + "/reverse", map("reason", "Cheque bounced")), 200);
        ok(admin.post(ADMIN + "/student-fees/" + transport + "/payments", map("amount", money("50"), "method", "UPI",
                "reference", "UPI-123", "paidOn", today.toString())), 201);

        JsonNode summary = ok(admin.get(ADMIN + "/fee-reports/summary?academicYearId=" + year), 200);
        assertThat(summary.get("billed").decimalValue()).as("waived fees are not billed").isEqualByComparingTo("2200");
        assertThat(summary.get("collected").decimalValue()).isEqualByComparingTo("450");
        assertThat(summary.get("outstanding").decimalValue()).isEqualByComparingTo("1750");
        assertThat(summary.get("overdue").decimalValue()).isEqualByComparingTo("150");
        assertThat(summary.get("waived").decimalValue()).isEqualByComparingTo("200");
        assertThat(summary.get("countByStatus").get("WAIVED").asInt()).isEqualTo(1);
        assertThat(summary.get("countByStatus").get("OVERDUE").asInt()).isEqualTo(1);
        assertThat(summary.get("countByStatus").get("PARTIALLY_PAID").asInt()).isEqualTo(1);
        assertThat(summary.get("byCategory")).hasSize(2);
        assertThat(ok(admin.get(ADMIN + "/fee-reports/summary?academicYearId=" + year + "&gradeLevelId=" + UUID.randomUUID()),
                200).get("billed").decimalValue()).isEqualByComparingTo("0");
        assertThat(admin.get(ADMIN + "/fee-reports/summary?academicYearId=" + UUID.randomUUID()).status())
                .isEqualTo(404);

        JsonNode collections = ok(admin.get(ADMIN + "/fee-reports/collections?from=" + today.minusDays(1) + "&to="
                + today), 200);
        assertThat(collections.get("payments").asInt()).isEqualTo(3);
        assertThat(collections.get("received").decimalValue()).isEqualByComparingTo("750");
        assertThat(collections.get("reversed").decimalValue()).isEqualByComparingTo("300");
        assertThat(collections.get("net").decimalValue()).isEqualByComparingTo("450");
        assertThat(collections.get("byMethod")).hasSize(2);
        assertThat(admin.get(ADMIN + "/fee-reports/collections?from=" + today + "&to=" + today.minusDays(1)).status())
                .isEqualTo(422);
        assertThat(admin.get(ADMIN + "/fee-reports/collections?from=" + today.minusYears(3) + "&to=" + today)
                .status()).isEqualTo(422);
    }
}
