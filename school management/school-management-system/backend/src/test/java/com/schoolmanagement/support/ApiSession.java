package com.schoolmanagement.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** An authenticated (or anonymous) HTTP client for integration tests, using the real cookie and CSRF flow. */
public final class ApiSession {

    private static final String CSRF = "integration-csrf-token";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final String accessToken;

    private ApiSession(MockMvc mockMvc, ObjectMapper objectMapper, String accessToken) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.accessToken = accessToken;
    }

    public static ApiSession anonymous(MockMvc mockMvc, ObjectMapper objectMapper) {
        return new ApiSession(mockMvc, objectMapper, null);
    }

    public static ApiSession login(MockMvc mockMvc, ObjectMapper objectMapper, String identifier, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", identifier, "password", password))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed for " + identifier + ": "
                    + result.getResponse().getStatus());
        }
        Map<String, String> cookies = new HashMap<>();
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            String pair = header.split(";", 2)[0];
            int eq = pair.indexOf('=');
            cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return new ApiSession(mockMvc, objectMapper, cookies.get("access_token"));
    }

    /** The outcome of a call: status plus the parsed JSON body (missing body becomes a null node). */
    public record Reply(int status, JsonNode body, String raw) {

        public String detail() {
            return body.path("detail").asText();
        }

        public String type() {
            return body.path("type").asText();
        }
    }

    public Reply get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path));
    }

    public Reply post(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.request(HttpMethod.POST, path), body));
    }

    public Reply put(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.request(HttpMethod.PUT, path), body));
    }

    public Reply delete(String path) throws Exception {
        return perform(MockMvcRequestBuilders.delete(path));
    }

    /** A multipart POST with plain fields and files, as the browser sends an upload form. */
    public Reply multipart(String path, Map<String, String> fields, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(path);
        fields.forEach(builder::param);
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        return perform(builder);
    }

    /** The raw outcome of a download: status, bytes and the response headers. */
    public record Binary(int status, byte[] body, MockHttpServletResponse response) {
    }

    public Binary download(String path) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(path);
        builder.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
        if (accessToken != null) {
            builder.cookie(new Cookie("access_token", accessToken));
        }
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        return new Binary(response.getStatus(), response.getContentAsByteArray(), response);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body)
            throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        return body == null ? builder : builder.content(objectMapper.writeValueAsString(body));
    }

    private Reply perform(MockHttpServletRequestBuilder builder) throws Exception {
        builder.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
        if (accessToken != null) {
            builder.cookie(new Cookie("access_token", accessToken));
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        String raw = result.getResponse().getContentAsString();
        JsonNode node = raw.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(raw);
        return new Reply(result.getResponse().getStatus(), node, raw);
    }
}
