package com.schoolmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolmanagement.auth.service.TokenSecrets;
import com.schoolmanagement.notification.service.EmailService;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.support.PostgresIntegrationTest;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import com.schoolmanagement.user.service.UserProvisioner;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** The real security chain, JPA mappings and Flyway schema working together over HTTP. */
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Pass-1!";
    private static final String CSRF = "integration-csrf-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SchoolRepository schools;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserProvisioner provisioner;
    @MockitoBean
    private EmailService emailService;

    private UUID schoolId;

    @BeforeEach
    void resetData() {
        jdbc.execute("truncate table refresh_tokens, password_reset_tokens, user_roles, users, schools cascade");
        Mockito.reset(emailService);
        schoolId = schools.save(new School("Test School", "TEST", "UTC")).getId();
        provisioner.createActiveUser(schoolId, "admin", "admin@test.school", PASSWORD, "Ada", "Admin",
                RoleName.ROLE_ADMIN);
        provisioner.createActiveUser(schoolId, "T-1", "teacher@test.school", PASSWORD, "Tara", "Teacher",
                RoleName.ROLE_TEACHER);
        provisioner.createActiveUser(schoolId, "S-1", "student@test.school", PASSWORD, "Sam", "Student",
                RoleName.ROLE_STUDENT);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body,
            Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = builder.contentType(MediaType.APPLICATION_JSON)
                .header("X-XSRF-TOKEN", CSRF).cookie(new Cookie("XSRF-TOKEN", CSRF));
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return body == null ? request : request.content(objectMapper.writeValueAsString(body));
    }

    private MvcResult login(String identifier, String password) throws Exception {
        return mockMvc.perform(json(post("/api/v1/auth/login"),
                Map.of("identifier", identifier, "password", password))).andReturn();
    }

    private static Map<String, String> cookiesOf(MvcResult result) {
        Map<String, String> cookies = new HashMap<>();
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            String pair = header.split(";", 2)[0];
            int eq = pair.indexOf('=');
            cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return cookies;
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(json(post("/api/v1/auth/refresh"), null, new Cookie("refresh_token", refreshToken)))
                .andReturn();
    }

    private int meStatus(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token", accessToken)))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ---- login / me ------------------------------------------------------------------------------------

    @Test
    void loginWithEmailOrUsernameSucceedsCaseInsensitivelyAndReturnsRoles() throws Exception {
        for (String identifier : new String[] {"student@test.school", "STUDENT@Test.School", "s-1"}) {
            MvcResult result = login(identifier, PASSWORD);

            assertThat(result.getResponse().getStatus()).as(identifier).isEqualTo(200);
            assertThat(body(result).get("roles").get(0).asText()).isEqualTo("ROLE_STUDENT");
            assertThat(body(result).has("passwordHash")).isFalse();
        }
    }

    @Test
    void loginIssuesHttpOnlyCookiesAndMeReturnsTheIdentity() throws Exception {
        MvcResult result = login("teacher@test.school", PASSWORD);
        Map<String, String> cookies = cookiesOf(result);

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .filteredOn(h -> h.startsWith("access_token") || h.startsWith("refresh_token"))
                .hasSize(2).allMatch(h -> h.contains("HttpOnly") && h.contains("SameSite=Lax"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain(cookies.get("access_token"));

        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("access_token",
                cookies.get("access_token")))).andReturn();
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(me).get("email").asText()).isEqualTo("teacher@test.school");
        assertThat(meStatus("garbage")).isEqualTo(401);
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        String refreshToken = cookiesOf(login("student@test.school", PASSWORD)).get("refresh_token");

        var stored = jdbc.queryForList("select token_hash from refresh_tokens", String.class);

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0)).isNotEqualTo(refreshToken).isEqualTo(TokenSecrets.hash(refreshToken))
                .hasSize(64);
    }

    @Test
    void unknownAccountAndWrongPasswordAreIndistinguishable() throws Exception {
        MvcResult unknown = login("ghost@test.school", PASSWORD);
        MvcResult wrong = login("student@test.school", "Wrong-Pass-1!");

        assertThat(unknown.getResponse().getStatus()).isEqualTo(401).isEqualTo(wrong.getResponse().getStatus());
        assertThat(body(unknown).get("detail").asText()).isEqualTo(body(wrong).get("detail").asText());
        assertThat(unknown.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(h -> h.startsWith("access_token"));
    }

    @Test
    void accountIsLockedAfterRepeatedFailuresEvenForTheCorrectPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(login("student@test.school", "Wrong-Pass-1!").getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult correct = login("student@test.school", PASSWORD);

        assertThat(correct.getResponse().getStatus()).isEqualTo(401);
        User student = users.findByLoginIdentifier("student@test.school").get(0);
        assertThat(student.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(student.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        login("student@test.school", "Wrong-Pass-1!");
        login("student@test.school", "Wrong-Pass-1!");

        assertThat(login("student@test.school", PASSWORD).getResponse().getStatus()).isEqualTo(200);

        assertThat(users.findByLoginIdentifier("student@test.school").get(0).getFailedLoginAttempts()).isZero();
    }

    @Test
    void suspendedAccountsCannotLogInAndTheirExistingAccessTokensStopWorking() throws Exception {
        String accessToken = cookiesOf(login("student@test.school", PASSWORD)).get("access_token");
        assertThat(meStatus(accessToken)).isEqualTo(200);

        jdbc.update("update users set account_status = 'SUSPENDED' where lower(email) = 'student@test.school'");

        assertThat(meStatus(accessToken)).isEqualTo(401);
        assertThat(login("student@test.school", PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void loginIsRejectedWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"student@test.school\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- refresh rotation / logout ---------------------------------------------------------------------

    @Test
    void refreshRotatesTheTokenAndTheOldOneCannotBeUsedAgain() throws Exception {
        Map<String, String> first = cookiesOf(login("student@test.school", PASSWORD));

        MvcResult rotated = refresh(first.get("refresh_token"));
        Map<String, String> second = cookiesOf(rotated);

        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.get("refresh_token")).isNotEqualTo(first.get("refresh_token"));
        assertThat(meStatus(second.get("access_token"))).isEqualTo(200);
        assertThat(refresh(first.get("refresh_token")).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(second.get("refresh_token")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void replayingAnOldRefreshTokenAfterTheGracePeriodRevokesTheWholeFamily() throws Exception {
        String first = cookiesOf(login("student@test.school", PASSWORD)).get("refresh_token");
        String second = cookiesOf(refresh(first)).get("refresh_token");
        jdbc.update("update refresh_tokens set revoked_at = now() - interval '1 hour' where token_hash = ?",
                TokenSecrets.hash(first));

        assertThat(refresh(first).getResponse().getStatus()).isEqualTo(401);

        assertThat(refresh(second).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_at is null",
                Integer.class)).isZero();
    }

    @Test
    void expiredRefreshTokenIsRejectedAndCookiesAreCleared() throws Exception {
        String token = cookiesOf(login("student@test.school", PASSWORD)).get("refresh_token");
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute'");

        MvcResult result = refresh(token);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(h -> h.contains("Max-Age=0"));
    }

    @Test
    void refreshWithoutACookieIsUnauthorized() throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/refresh"), null)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookies() throws Exception {
        Map<String, String> cookies = cookiesOf(login("student@test.school", PASSWORD));

        MvcResult logout = mockMvc.perform(json(post("/api/v1/auth/logout"), null,
                new Cookie("refresh_token", cookies.get("refresh_token")))).andReturn();

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(h -> h.contains("Max-Age=0"));
        assertThat(refresh(cookies.get("refresh_token")).getResponse().getStatus()).isEqualTo(401);
    }

    // ---- change password -------------------------------------------------------------------------------

    @Test
    void changePasswordEndsOtherSessionsAndKeepsTheCurrentOneAlive() throws Exception {
        Map<String, String> device1 = cookiesOf(login("student@test.school", PASSWORD));
        Map<String, String> device2 = cookiesOf(login("student@test.school", PASSWORD));

        MvcResult change = mockMvc.perform(json(post("/api/v1/auth/change-password"),
                Map.of("currentPassword", PASSWORD, "newPassword", "Brand-New-Pass9!"),
                new Cookie("access_token", device1.get("access_token")))).andReturn();

        assertThat(change.getResponse().getStatus()).isEqualTo(204);
        Map<String, String> renewed = cookiesOf(change);
        assertThat(refresh(device1.get("refresh_token")).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(device2.get("refresh_token")).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(renewed.get("refresh_token")).getResponse().getStatus()).isEqualTo(200);
        assertThat(login("student@test.school", "Brand-New-Pass9!").getResponse().getStatus()).isEqualTo(200);
        assertThat(login("student@test.school", PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndWeakNewPassword() throws Exception {
        String access = cookiesOf(login("student@test.school", PASSWORD)).get("access_token");

        MvcResult wrong = mockMvc.perform(json(post("/api/v1/auth/change-password"),
                Map.of("currentPassword", "Wrong-Pass-1!", "newPassword", "Brand-New-Pass9!"),
                new Cookie("access_token", access))).andReturn();
        MvcResult weak = mockMvc.perform(json(post("/api/v1/auth/change-password"),
                Map.of("currentPassword", PASSWORD, "newPassword", "weak"),
                new Cookie("access_token", access))).andReturn();

        assertThat(wrong.getResponse().getStatus()).isEqualTo(422);
        assertThat(weak.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(weak).get("errors").get(0).get("field").asText()).isEqualTo("newPassword");
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/change-password"),
                Map.of("currentPassword", PASSWORD, "newPassword", "Brand-New-Pass9!"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    // ---- forgot / reset password -----------------------------------------------------------------------

    private String requestResetAndCaptureToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/forgot-password"), Map.of("email", email)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq(email), Mockito.anyString(), link.capture(), anyLong());
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    @Test
    void forgotPasswordGivesTheSameAnswerForKnownAndUnknownEmailsAndMailsOnlyKnownOnes() throws Exception {
        MvcResult unknown = mockMvc.perform(json(post("/api/v1/auth/forgot-password"),
                Map.of("email", "ghost@test.school"))).andReturn();
        MvcResult known = mockMvc.perform(json(post("/api/v1/auth/forgot-password"),
                Map.of("email", "student@test.school"))).andReturn();

        assertThat(unknown.getResponse().getStatus()).isEqualTo(202).isEqualTo(known.getResponse().getStatus());
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(known.getResponse().getContentAsString());
        verify(emailService).sendPasswordReset(eq("student@test.school"), Mockito.anyString(), Mockito.anyString(),
                anyLong());
        verify(emailService, never()).sendPasswordReset(eq("ghost@test.school"), Mockito.anyString(),
                Mockito.anyString(), anyLong());
    }

    @Test
    void resetPasswordWithTheEmailedTokenWorksOnceAndEndsAllSessions() throws Exception {
        String refreshToken = cookiesOf(login("student@test.school", PASSWORD)).get("refresh_token");
        String token = requestResetAndCaptureToken("student@test.school");
        assertThat(jdbc.queryForList("select token_hash from password_reset_tokens", String.class))
                .containsExactly(TokenSecrets.hash(token));

        MvcResult reset = mockMvc.perform(json(post("/api/v1/auth/reset-password"),
                Map.of("token", token, "newPassword", "Reset-Pass-7!x"))).andReturn();
        MvcResult replay = mockMvc.perform(json(post("/api/v1/auth/reset-password"),
                Map.of("token", token, "newPassword", "Another-Pass-8!y"))).andReturn();

        assertThat(reset.getResponse().getStatus()).isEqualTo(204);
        assertThat(replay.getResponse().getStatus()).isEqualTo(400);
        assertThat(refresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("student@test.school", "Reset-Pass-7!x").getResponse().getStatus()).isEqualTo(200);
        assertThat(login("student@test.school", PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aNewResetRequestInvalidatesThePreviousLink() throws Exception {
        String first = requestResetAndCaptureToken("student@test.school");
        Mockito.reset(emailService);
        requestResetAndCaptureToken("student@test.school");

        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/reset-password"),
                Map.of("token", first, "newPassword", "Reset-Pass-7!x"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void expiredAndForgedResetTokensAreRejected() throws Exception {
        String token = requestResetAndCaptureToken("student@test.school");
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 minute'");

        for (String candidate : new String[] {token, "forged-token"}) {
            MvcResult result = mockMvc.perform(json(post("/api/v1/auth/reset-password"),
                    Map.of("token", candidate, "newPassword", "Reset-Pass-7!x"))).andReturn();
            assertThat(result.getResponse().getStatus()).as(candidate).isEqualTo(400);
        }
    }

    @Test
    void resetLinkOfAnAccountSuspendedAfterTheRequestIsRejected() throws Exception {
        String token = requestResetAndCaptureToken("student@test.school");
        jdbc.update("update users set account_status = 'SUSPENDED' where lower(email) = 'student@test.school'");

        MvcResult result = mockMvc.perform(json(post("/api/v1/auth/reset-password"),
                Map.of("token", token, "newPassword", "Reset-Pass-7!x"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    // ---- role boundaries -------------------------------------------------------------------------------

    @Test
    void roleSpecificUrlsAreOnlyReachableByTheMatchingRole() throws Exception {
        Map<String, String> admin = cookiesOf(login("admin@test.school", PASSWORD));
        Map<String, String> teacher = cookiesOf(login("teacher@test.school", PASSWORD));
        Map<String, String> student = cookiesOf(login("student@test.school", PASSWORD));

        assertThat(statusOf("/api/v1/admin/probe", admin)).isEqualTo(200);
        assertThat(statusOf("/api/v1/admin/probe", teacher)).isEqualTo(403);
        assertThat(statusOf("/api/v1/admin/probe", student)).isEqualTo(403);
        assertThat(statusOf("/api/v1/teacher/probe", teacher)).isEqualTo(200);
        assertThat(statusOf("/api/v1/teacher/probe", student)).isEqualTo(403);
        assertThat(statusOf("/api/v1/student/probe", student)).isEqualTo(200);
        assertThat(statusOf("/api/v1/student/probe", admin)).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/v1/admin/probe")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    private int statusOf(String path, Map<String, String> cookies) throws Exception {
        return mockMvc.perform(get(path).cookie(new Cookie("access_token", cookies.get("access_token"))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void duplicateEmailsAreRejectedByTheDatabaseConstraintEvenWithDifferentCase() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (school_id, email, password_hash, first_name, last_name, account_status) "
                        + "values (?, 'STUDENT@test.school', 'x', 'A', 'B', 'ACTIVE')", schoolId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
