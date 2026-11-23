package com.schoolmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.ApiSession;
import com.schoolmanagement.support.PostgresIntegrationTest;
import com.schoolmanagement.support.SchoolWorld;
import com.schoolmanagement.user.service.UserProvisioner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Authorization by sweep: every business endpoint the application exposes is called anonymously and with the wrong
 * role, so a new controller that forgets its rules fails here instead of reaching production. The endpoint list comes
 * from Spring's own mappings, not from a hand-kept list.
 */
class EndpointSecuritySweepTest extends PostgresIntegrationTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{[^}/]+}");
    private static final List<String> BUSINESS_PREFIXES = List.of("/api/v1/admin/", "/api/v1/teacher/",
            "/api/v1/student/", "/api/v1/announcements");

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
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private SchoolWorld world;

    private record Endpoint(HttpMethod method, String path) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        SchoolWorld.wipe(jdbc);
        world = new SchoolWorld(mockMvc, objectMapper).build(schools, provisioner, "Test School", "a");
    }

    private List<Endpoint> endpoints() {
        Set<Endpoint> found = new TreeSet<>((a, b) -> a.toString().compareTo(b.toString()));
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            Set<String> patterns = entry.getKey().getPathPatternsCondition() == null ? Set.of()
                    : entry.getKey().getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (BUSINESS_PREFIXES.stream().noneMatch(pattern::startsWith)) {
                    continue;
                }
                String path = VARIABLE.matcher(pattern).replaceAll(UUID.randomUUID().toString());
                for (RequestMethod method : methods) {
                    found.add(new Endpoint(HttpMethod.valueOf(method.name()), path));
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static int call(ApiSession session, Endpoint endpoint) throws Exception {
        String path = endpoint.path();
        return switch (endpoint.method().name()) {
            case "GET" -> session.download(path).status();
            case "POST" -> session.post(path, Map.of()).status();
            case "PUT" -> session.put(path, Map.of()).status();
            case "DELETE" -> session.delete(path).status();
            default -> throw new IllegalStateException("Unexpected method " + endpoint);
        };
    }

    /** The role prefix an endpoint belongs to, or null when any signed-in person may call it. */
    private static String roleOf(Endpoint endpoint) {
        if (endpoint.path().startsWith("/api/v1/admin/")) {
            return "admin";
        }
        if (endpoint.path().startsWith("/api/v1/teacher/")) {
            return "teacher";
        }
        return endpoint.path().startsWith("/api/v1/student/") ? "student" : null;
    }

    @Test
    void theSweepActuallyCoversTheApplication() {
        List<Endpoint> all = endpoints();
        assertThat(all.size()).as("endpoints found: %s", all).isGreaterThan(150);
        assertThat(all.stream().map(Endpoint::path)).anyMatch(p -> p.contains("/admin/audit-logs"))
                .anyMatch(p -> p.contains("/teacher/exams")).anyMatch(p -> p.contains("/student/fees"))
                .anyMatch(p -> p.startsWith("/api/v1/announcements"));
    }

    @Test
    void everyBusinessEndpointRefusesAnAnonymousCaller() throws Exception {
        ApiSession anonymous = ApiSession.anonymous(mockMvc, objectMapper);
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            int status = call(anonymous, endpoint);
            if (status != 401) {
                failures.add(endpoint + " answered " + status);
            }
        }
        assertThat(failures).as("endpoints that did not answer 401 to an anonymous caller").isEmpty();
    }

    @Test
    void everyRoleEndpointRefusesPeopleOfTheOtherRoles() throws Exception {
        Map<String, ApiSession> sessions = Map.of("admin", world.admin, "teacher", world.teacher, "student",
                world.student);
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            String owner = roleOf(endpoint);
            if (owner == null) {
                continue;
            }
            for (Map.Entry<String, ApiSession> session : sessions.entrySet()) {
                if (session.getKey().equals(owner)) {
                    continue;
                }
                int status = call(session.getValue(), endpoint);
                if (status != 403) {
                    failures.add(session.getKey() + " on " + endpoint + " answered " + status);
                }
            }
        }
        assertThat(failures).as("endpoints reachable by the wrong role").isEmpty();
    }

    @Test
    void theOwnRoleGetsPastTheSecurityLayerOnEveryEndpoint() throws Exception {
        Map<String, ApiSession> sessions = Map.of("admin", world.admin, "teacher", world.teacher, "student",
                world.student);
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            String owner = roleOf(endpoint);
            ApiSession session = owner == null ? world.student : sessions.get(owner);
            int status = call(session, endpoint);
            if (status == 401 || status == 403 && owner == null) {
                failures.add(endpoint + " refused its own role with " + status);
            }
            assertThat(status).as(endpoint + " must never fail with a server error").isLessThan(500);
        }
        assertThat(failures).isEmpty();
    }
}
