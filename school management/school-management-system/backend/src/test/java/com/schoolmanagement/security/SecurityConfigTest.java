package com.schoolmanagement.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolmanagement.auth.service.AccessTokenService;
import com.schoolmanagement.common.system.SystemController;
import com.schoolmanagement.support.ProbeController;
import com.schoolmanagement.support.RoleProbeController;
import com.schoolmanagement.support.TestFixtures;
import com.schoolmanagement.support.WebSecuritySlice;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ProbeController.class, SystemController.class, RoleProbeController.class})
@WebSecuritySlice
class SecurityConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointRejectsAnonymousUsersWithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/probe/missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/unauthorized"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void csrfCookieIsIssuedEvenOnAnUnauthorizedResponseSoTheLoginPageCanPost() throws Exception {
        mockMvc.perform(get("/api/v1/probe/missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void publicStatusEndpointIsAvailableAndIssuesCsrfCookieAndSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @WithMockUser
    void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/forbidden"));
    }

    @Test
    @WithMockUser
    void stateChangingRequestWithCsrfCookieAndMatchingHeaderIsAccepted() throws Exception {
        // Real SPA flow: read the XSRF-TOKEN cookie from any response, then echo it in X-XSRF-TOKEN.
        Cookie csrfCookie = mockMvc.perform(get("/api/v1/system/info")).andReturn().getResponse()
                .getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/v1/probe/validated")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"));
    }

    @Test
    void corsPreflightIsAllowedForConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/probe/validated")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsPreflightIsRejectedForUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/probe/validated")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @WithMockUser(authorities = "ROLE_STUDENT")
    void studentCanOnlyReachStudentEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/student/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teacher/probe")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/probe")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/forbidden"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_TEACHER")
    void teacherCanOnlyReachTeacherEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/probe")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/student/probe")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminReachesAdminEndpointsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teacher/probe")).andExpect(status().isForbidden());
    }

    @Test
    void validAccessCookieAuthenticatesTheUserLoadedFromTheDatabase() throws Exception {
        User admin = TestFixtures.user(RoleName.ROLE_ADMIN, "hash", AccountStatus.ACTIVE);
        when(userRepository.findWithRolesById(admin.getId())).thenReturn(Optional.of(admin));
        String token = accessTokenService.issue(admin);

        mockMvc.perform(get("/api/v1/admin/probe").cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk());
    }

    @Test
    void accessTokenOfASuspendedAccountStopsWorkingImmediately() throws Exception {
        User teacher = TestFixtures.user(RoleName.ROLE_TEACHER, "hash", AccountStatus.ACTIVE);
        when(userRepository.findWithRolesById(teacher.getId())).thenReturn(Optional.of(teacher));
        String token = accessTokenService.issue(teacher);
        ReflectionTestUtils.setField(teacher, "accountStatus", AccountStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/teacher/probe").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedOrGarbageAccessCookieIsTreatedAsAnonymousAndPublicEndpointsStillWork() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").cookie(new Cookie("access_token", "not.a.jwt")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/probe").cookie(new Cookie("access_token", "not.a.jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
