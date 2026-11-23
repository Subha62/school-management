package com.schoolmanagement.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import com.schoolmanagement.support.ProbeController;
import com.schoolmanagement.support.WebSecuritySlice;
import com.schoolmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProbeController.class)
@WebSecuritySlice
@WithMockUser
class GlobalExceptionHandlerTest {

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationFailureListsFieldErrorsInProblemFormat() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validated").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://school.example/errors/validation"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/probe/validated"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Name is required"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void constraintViolationOnRequestParameterIsReportedAsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/probe/size").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("Size must be at least 1"));
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validated").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/bad-request"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(not(containsString("Jackson"))))
                .andExpect(content().string(not(containsString("com.fasterxml"))));
    }

    @Test
    void unknownResourceMapsToNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/not-found"))
                .andExpect(jsonPath("$.detail").value("Student was not found"));
    }

    @Test
    void unknownRouteMapsToNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance").value("/api/v1/does-not-exist"));
    }

    @Test
    void wrongMethodMapsToMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/probe/missing").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/method-not-allowed"));
    }

    @Test
    void businessRuleViolationMapsToUnprocessableEntity() throws Exception {
        mockMvc.perform(get("/api/v1/probe/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/business-rule-violation"))
                .andExpect(jsonPath("$.detail").value("Marks cannot exceed the maximum marks."));
    }

    @Test
    void optimisticLockFailureMapsToConflict() throws Exception {
        mockMvc.perform(get("/api/v1/probe/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/concurrent-modification"));
    }

    @Test
    void unexpectedFailureNeverLeaksInternalDetails() throws Exception {
        mockMvc.perform(get("/api/v1/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://school.example/errors/internal-server-error"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(not(containsString("select"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
