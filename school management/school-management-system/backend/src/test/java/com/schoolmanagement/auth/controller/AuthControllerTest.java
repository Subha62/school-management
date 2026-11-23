package com.schoolmanagement.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schoolmanagement.auth.dto.CurrentUserResponse;
import com.schoolmanagement.auth.service.AuthService;
import com.schoolmanagement.auth.service.AuthSession;
import com.schoolmanagement.auth.service.PasswordResetService;
import com.schoolmanagement.common.exception.TooManyRequestsException;
import com.schoolmanagement.common.exception.UnauthorizedException;
import com.schoolmanagement.support.WebSecuritySlice;
import com.schoolmanagement.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AuthController.class)
@WebSecuritySlice
@Import(AuthCookies.class)
class AuthControllerTest {

    private static final String CSRF = "csrf-token-value";

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PasswordResetService passwordResetService;

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    private static AuthSession session() {
        CurrentUserResponse user = new CurrentUserResponse(UUID.randomUUID(), UUID.randomUUID(), "S-0001",
                "s@school.test", "Sam", "Student", List.of("ROLE_STUDENT"));
        return new AuthSession(user, "ACCESS.JWT.VALUE", Duration.ofMinutes(15), "REFRESH-VALUE",
                Duration.ofDays(7));
    }

    private static String cookieHeaders(MvcResult result) {
        return String.join("\n", result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginSetsHttpOnlyCookiesScopedByPathAndNeverPutsTokensInTheBody() throws Exception {
        when(authService.login(any(), any())).thenReturn(session());

        var result = mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"S-0001\",\"password\":\"anything\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_STUDENT"))
                .andExpect(content().string(not(containsString("ACCESS.JWT.VALUE"))))
                .andExpect(content().string(not(containsString("REFRESH-VALUE"))))
                .andReturn();

        String cookies = cookieHeaders(result);
        assertCookie(cookies, "access_token=ACCESS.JWT.VALUE", "Path=/api;");
        assertCookie(cookies, "refresh_token=REFRESH-VALUE", "Path=/api/v1/auth;");
        org.assertj.core.api.Assertions.assertThat(cookies).contains("Max-Age=900", "Max-Age=604800");
    }

    private static void assertCookie(String cookies, String valuePart, String pathPart) {
        String line = cookies.lines().filter(l -> l.startsWith(valuePart)).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(line).contains("HttpOnly", "SameSite=Lax", pathPart);
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"S-0001\",\"password\":\"anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginValidationErrorsAreFieldLevelAndDoNotEchoThePassword() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"\",\"password\":\"secret-value\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("identifier"))
                .andExpect(content().string(not(containsString("secret-value"))));
    }

    @Test
    void failedLoginIsAGenericUnauthorizedProblem() throws Exception {
        when(authService.login(any(), any())).thenThrow(new UnauthorizedException("Invalid credentials."));

        mockMvc.perform(withCsrf(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"a@b.co\",\"password\":\"x\"}")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void rateLimitedLoginReturns429WithRetryAfter() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new TooManyRequestsException("Too many attempts. Try again later.", Duration.ofSeconds(90)));

        mockMvc.perform(withCsrf(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"a@b.co\",\"password\":\"x\"}")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"));
    }

    @Test
    void failedRefreshClearsBothCookies() throws Exception {
        when(authService.refresh(any(), any())).thenThrow(new UnauthorizedException("Session expired"));

        var result = mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "stale"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(cookieHeaders(result)).contains("access_token=;", "refresh_token=;",
                "Max-Age=0");
    }

    @Test
    void logoutWorksWithoutAValidAccessTokenAndClearsCookies() throws Exception {
        var result = mockMvc.perform(withCsrf(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "r"))))
                .andExpect(status().isNoContent())
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(cookieHeaders(result)).contains("Max-Age=0");
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"Valid-Passw0rd!\"}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordAlwaysAnswersAcceptedWithTheSameMessage() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anyone@school.test\"}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(containsString("If an account exists")));
    }

    @Test
    void forgotPasswordValidatesTheEmailFormat() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void resetPasswordEnforcesTheStrengthPolicy() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"weak\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    void unknownPropertiesInTheLoginBodyAreRejected() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"a\",\"password\":\"b\",\"role\":\"ROLE_ADMIN\"}")))
                .andExpect(status().isBadRequest());
    }
}
