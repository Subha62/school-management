package com.schoolmanagement.common.exception;

import com.schoolmanagement.common.web.TraceIdFilter;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 problem documents with the project-wide extension members. */
public final class ProblemDetails {

    public static final String TYPE_BASE = "https://school.example/errors/";

    private ProblemDetails() {
    }

    public static ProblemDetail create(HttpStatusCode status, String slug, String title, String detail,
            String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setInstance(URI.create(instance));
        addCommonMembers(problem);
        return problem;
    }

    public static ProblemDetail create(HttpStatusCode status, String detail, String instance) {
        return create(status, slugFor(status), titleFor(status), detail, instance);
    }

    public static ProblemDetail validation(String instance, List<FieldError> errors) {
        ProblemDetail problem = create(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more request fields are invalid.", instance);
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Adds the shared members to a problem produced by Spring MVC's own exception handling. */
    public static void enrich(ProblemDetail problem, String instance) {
        HttpStatusCode status = HttpStatusCode.valueOf(problem.getStatus());
        if ("about:blank".equals(problem.getType().toString())) {
            problem.setType(URI.create(TYPE_BASE + slugFor(status)));
        }
        if (problem.getTitle() == null) {
            problem.setTitle(titleFor(status));
        }
        if (problem.getInstance() == null) {
            problem.setInstance(URI.create(instance));
        }
        addCommonMembers(problem);
    }

    private static void addCommonMembers(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("traceId", MDC.get(TraceIdFilter.MDC_KEY));
    }

    private static String slugFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "error" : known.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String titleFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "Error" : known.getReasonPhrase();
    }
}
