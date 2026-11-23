package com.schoolmanagement.support;

import com.schoolmanagement.auth.service.AccessTokenService;
import com.schoolmanagement.common.exception.GlobalExceptionHandler;
import com.schoolmanagement.common.web.TraceIdFilter;
import com.schoolmanagement.config.ApplicationConfig;
import com.schoolmanagement.security.AccessTokenAuthenticationConverter;
import com.schoolmanagement.security.JwtConfig;
import com.schoolmanagement.security.ProblemDetailSecurityHandlers;
import com.schoolmanagement.security.SecurityConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Wires the real security chain, token handling and error handling into a {@code @WebMvcTest}. The test class
 * must declare a {@code @MockitoBean UserRepository}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, SecurityConfig.class, ProblemDetailSecurityHandlers.class,
        TraceIdFilter.class, ApplicationConfig.class, JwtConfig.class, AccessTokenAuthenticationConverter.class,
        AccessTokenService.class})
public @interface WebSecuritySlice {
}
