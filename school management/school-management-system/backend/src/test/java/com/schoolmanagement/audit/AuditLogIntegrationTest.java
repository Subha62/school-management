package com.schoolmanagement.audit;

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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** The persistent audit trail: what is recorded, what is hidden, who can read it, and that it cannot be changed. */
class AuditLogIntegrationTest extends PostgresIntegrationTest {

    private static final String LOGS = "/api/v1/admin/audit-logs";

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
    private AuditEventPublisher publisher;
    @Autowired
    private TransactionTemplate transaction;

    private SchoolWorld world;

    @BeforeEach
    void setUp() throws Exception {
        SchoolWorld.wipe(jdbc);
        world = new SchoolWorld(mockMvc, objectMapper).build(schools, provisioner, "Test School", "a");
    }

    private JsonNode entries(String query) throws Exception {
        return ok(world.admin.get(LOGS + "?size=100" + query), 200).get("content");
    }

    @Test
    void businessActionsAreRecordedWithWhoWhatAndWhen() throws Exception {
        JsonNode rule = ok(world.admin.post("/api/v1/admin/grade-rules", map("grade", "A", "description", "",
                "minPercent", new BigDecimal("80"), "maxPercent", new BigDecimal("100"))), 201);
        ok(world.admin.put("/api/v1/admin/grade-rules/" + rule.get("id").asText(), map("grade", "A", "description",
                "Top", "minPercent", new BigDecimal("85"), "maxPercent", new BigDecimal("100"), "version",
                rule.get("version").asLong())), 200);

        JsonNode updated = entries("&action=GRADE_RULE_UPDATED").get(0);
        assertThat(updated.get("actorName").asText()).isEqualTo("Ada Admin");
        assertThat(updated.get("entityType").asText()).isEqualTo("GradeRule");
        assertThat(updated.get("entityId").asText()).isEqualTo(rule.get("id").asText());
        assertThat(updated.get("summary").asText()).contains("Grade A");
        assertThat(updated.get("oldValues").get("minPercent").decimalValue()).isEqualByComparingTo("80");
        assertThat(updated.get("newValues").get("minPercent").decimalValue()).isEqualByComparingTo("85");
        assertThat(updated.get("ipAddress").asText()).isNotBlank();
        assertThat(updated.get("createdAt").asText()).isNotBlank();
        assertThat(entries("&action=GRADE_RULE_CREATED")).hasSize(1);
        assertThat(entries("&entityType=GradeRule&entityId=" + rule.get("id").asText())).hasSize(2);
    }

    @Test
    void signInsAndPasswordChangesAreRecordedWithoutAnySecret() throws Exception {
        ApiSession wrong = ApiSession.anonymous(mockMvc, objectMapper);
        wrong.post("/api/v1/auth/login", map("identifier", "a-t1@test.school", "password", "Wrong-Pass-9!"));
        ok(world.teacher.post("/api/v1/auth/change-password", map("currentPassword", SchoolWorld.PASSWORD,
                "newPassword", "Another-Pass-2!")), 204);

        assertThat(entries("&action=LOGIN_FAILURE")).isNotEmpty();
        assertThat(entries("&action=PASSWORD_CHANGED")).hasSize(1);
        String everything = ok(world.admin.get(LOGS + "?size=100"), 200).toString();
        assertThat(everything).doesNotContain("Wrong-Pass-9!").doesNotContain("Another-Pass-2!")
                .doesNotContain(SchoolWorld.PASSWORD);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where summary ilike '%Wrong-Pass%'",
                Integer.class)).isZero();
    }

    @Test
    void credentialsInOldAndNewValuesAreRedactedBeforeTheyAreStored() {
        publisher.publish(new AuditEvent("TEST_EVENT", null, world.schoolId, "Thing", "1", "A change\nwith a break",
                "127.0.0.1", "agent", Map.of("password", "old-secret", "name", "before"),
                Map.of("password", "new-secret", "nested", Map.of("refreshToken", "t", "name", "after"))));

        String stored = jdbc.queryForObject("select old_values::text || new_values::text from audit_logs "
                + "where action = 'TEST_EVENT'", String.class);
        assertThat(stored).doesNotContain("old-secret").doesNotContain("new-secret").contains("[REDACTED]")
                .contains("before").contains("after");
        assertThat(jdbc.queryForObject("select summary from audit_logs where action = 'TEST_EVENT'", String.class))
                .doesNotContain("\n");
    }

    @Test
    void anEventFromATransactionThatRollsBackLeavesNoTrace() {
        try {
            transaction.executeWithoutResult(status -> {
                publisher.publish(new AuditEvent("ROLLED_BACK", null, world.schoolId, "Thing", "1", "never happened",
                        null, null));
                throw new IllegalStateException("the change failed");
            });
        } catch (IllegalStateException expected) {
            // the point of the test
        }
        transaction.executeWithoutResult(status -> publisher.publish(new AuditEvent("COMMITTED", null,
                world.schoolId, "Thing", "2", "did happen", null, null)));

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ROLLED_BACK'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'COMMITTED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void filtersNarrowTheTrailAndTheListsOfActionsAreAvailable() throws Exception {
        ok(world.admin.post("/api/v1/admin/fee-categories", map("name", "Tuition", "active", true)), 201);
        ok(world.admin.post("/api/v1/admin/grade-rules", map("grade", "A", "minPercent", new BigDecimal("0"),
                "maxPercent", new BigDecimal("100"))), 201);

        assertThat(entries("&search=tuition")).hasSize(1);
        assertThat(entries("&actorId=" + UUID.randomUUID())).isEmpty();
        assertThat(entries("&from=2000-01-01&to=2000-01-02")).isEmpty();
        assertThat(entries("&from=2000-01-01").size()).isGreaterThan(2);
        assertThat(ok(world.admin.get(LOGS + "/actions"), 200).toString()).contains("FEE_CATEGORY_CREATED")
                .contains("GRADE_RULE_CREATED");
        assertThat(ok(world.admin.get(LOGS + "/entity-types"), 200).toString()).contains("FeeCategory");
        JsonNode newest = entries("").get(0);
        assertThat(ok(world.admin.get(LOGS + "/" + newest.get("id").asText()), 200).get("id").asText())
                .isEqualTo(newest.get("id").asText());
        assertThat(world.admin.get(LOGS + "/" + UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(world.admin.get(LOGS + "?size=1000").status()).as("unreasonable page sizes are rejected")
                .isEqualTo(400);
    }

    @Test
    void onlyAdministratorsReadItAndOnlyForTheirOwnSchool() throws Exception {
        ok(world.admin.post("/api/v1/admin/fee-categories", map("name", "Tuition", "active", true)), 201);
        SchoolWorld other = new SchoolWorld(mockMvc, objectMapper).build(schools, provisioner, "Other School", "b");
        ok(other.admin.post("/api/v1/admin/fee-categories", map("name", "Transport", "active", true)), 201);

        assertThat(world.teacher.get(LOGS).status()).isEqualTo(403);
        assertThat(world.student.get(LOGS).status()).isEqualTo(403);
        assertThat(ApiSession.anonymous(mockMvc, objectMapper).get(LOGS).status()).isEqualTo(401);

        assertThat(ok(world.admin.get(LOGS + "?size=100"), 200).toString()).contains("Tuition")
                .doesNotContain("Transport");
        String foreignId = ok(other.admin.get(LOGS + "?search=transport"), 200).get("content").get(0).get("id")
                .asText();
        assertThat(world.admin.get(LOGS + "/" + foreignId).status()).as("another school's entry").isEqualTo(404);
    }

    @Test
    void theTrailCannotBeChangedThroughTheApiOrByHibernate() throws Exception {
        ok(world.admin.post("/api/v1/admin/fee-categories", map("name", "Tuition", "active", true)), 201);
        String id = entries("").get(0).get("id").asText();

        assertThat(world.admin.post(LOGS, map("action", "FAKE")).status()).isEqualTo(405);
        assertThat(world.admin.put(LOGS + "/" + id, map("summary", "edited")).status()).isEqualTo(405);
        assertThat(world.admin.delete(LOGS + "/" + id).status()).isEqualTo(405);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where id = ?::uuid", Integer.class, id))
                .isEqualTo(1);
    }
}
