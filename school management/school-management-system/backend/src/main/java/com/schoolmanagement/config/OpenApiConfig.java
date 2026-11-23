package com.schoolmanagement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for Swagger UI and the generated OpenAPI document (both are disabled in production). Sign in with
 * {@code POST /api/v1/auth/login}; the browser keeps the session in HttpOnly cookies, and state-changing requests
 * repeat the {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header.
 */
@Configuration
class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";
    private static final String CSRF_HEADER = "csrfHeader";

    @Bean
    OpenAPI schoolManagementApi() {
        return new OpenAPI()
                .info(new Info().title("School Management System API").version("1.0")
                        .description("Role-based API for administrators, teachers and students. Paths under "
                                + "/api/v1/admin, /api/v1/teacher and /api/v1/student are limited to that role; "
                                + "every record is scoped to the caller's school. Errors are RFC 7807 problem "
                                + "details."))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("access_token")
                                .description("HttpOnly access-token cookie set by POST /api/v1/auth/login."))
                        .addSecuritySchemes(CSRF_HEADER, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")
                                .description("Copy of the XSRF-TOKEN cookie, required on POST, PUT and DELETE.")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH).addList(CSRF_HEADER));
    }
}
