package com.schoolmanagement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.support.ApiSession;
import com.schoolmanagement.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The generated API description is available in development and test, and describes every module. */
class OpenApiDocumentTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theDocumentListsEveryModuleAndHowToAuthenticate() throws Exception {
        var reply = ApiSession.anonymous(mockMvc, objectMapper).get("/v3/api-docs");
        assertThat(reply.status()).isEqualTo(200);
        JsonNode doc = reply.body();
        assertThat(doc.at("/info/title").asText()).isEqualTo("School Management System API");
        assertThat(doc.at("/components/securitySchemes/cookieAuth/in").asText()).isEqualTo("cookie");
        assertThat(doc.at("/components/securitySchemes/csrfHeader/name").asText()).isEqualTo("X-XSRF-TOKEN");

        String paths = doc.get("paths").toString();
        for (String module : new String[] {"/api/v1/auth/login", "/api/v1/admin/academic-years",
                "/api/v1/admin/students", "/api/v1/teacher/attendance/sessions", "/api/v1/teacher/assignments",
                "/api/v1/admin/exams", "/api/v1/student/results", "/api/v1/announcements", "/api/v1/admin/fee-structures",
                "/api/v1/student/fees", "/api/v1/admin/audit-logs", "/api/v1/admin/dashboard"}) {
            assertThat(paths).as(module).contains(module);
        }
        assertThat(paths).contains("\"Audit logs\"").contains("\"Dashboards\"");
    }
}
