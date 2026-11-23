package com.schoolmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolmanagement.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the whole application against PostgreSQL: Flyway migrations, Hibernate validation and wiring. */
class ApplicationStartupIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesAllMigrationsAndSeedsTheRoles() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        var roles = jdbcTemplate.queryForList("select name from roles order by name", String.class);

        assertThat(failed).isZero();
        assertThat(roles).containsExactly("ROLE_ADMIN", "ROLE_STUDENT", "ROLE_TEACHER");
    }

    @Test
    void actuatorHealthIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void apiRoutesRemainProtectedByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/students")).andExpect(status().isUnauthorized());
    }
}
