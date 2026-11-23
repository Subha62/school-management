package com.schoolmanagement.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception into an RFC 7807 problem document. Internal details such as class names,
 * SQL and stack traces are logged with the trace id but never sent to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
                ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation",
                "Business rule violated", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<ProblemDetail> handleFieldValidation(FieldValidationException ex, HttpServletRequest request) {
        return respond(ProblemDetails.validation(request.getRequestURI(), ex.getErrors()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.create(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(),
                request.getRequestURI());
        long seconds = Math.max(1, ex.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds)).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        log.warn("Optimistic locking failure", ex);
        return respond(ProblemDetails.create(HttpStatus.CONFLICT, "concurrent-modification", "Conflict",
                "The record was modified by someone else. Reload it and try again.", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Data integrity violation", ex);
        return respond(ProblemDetails.create(HttpStatus.CONFLICT, "data-conflict", "Conflict",
                "The request conflicts with existing data.", request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldError(lastPathNode(violation.getPropertyPath().toString()),
                        violation.getMessage()))
                .toList();
        return respond(ProblemDetails.validation(request.getRequestURI(), errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.",
                request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ProblemDetails.create(HttpStatus.UNAUTHORIZED, "Authentication is required.",
                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ProblemDetails.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Quote the trace id when contacting support.",
                request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationResponse(ex, bindingErrors(ex), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors
                    .add(new FieldError(name == null ? "request" : name, error.getDefaultMessage())));
        });
        return validationResponse(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ProblemDetails.enrich(problem, requestUri(request));
        }
        return response;
    }

    private ResponseEntity<Object> validationResponse(Exception ex, List<FieldError> errors, HttpHeaders headers,
            WebRequest request) {
        ProblemDetail problem = ProblemDetails.validation(requestUri(request), errors);
        return super.handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static List<FieldError> bindingErrors(BindException ex) {
        List<FieldError> errors = new ArrayList<>();
        ex.getFieldErrors().forEach(error -> errors.add(new FieldError(error.getField(), error.getDefaultMessage())));
        for (ObjectError error : ex.getGlobalErrors()) {
            errors.add(new FieldError(error.getObjectName(), error.getDefaultMessage()));
        }
        return errors;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String requestUri(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "/";
    }

    private static String lastPathNode(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? path : path.substring(index + 1);
    }
}
