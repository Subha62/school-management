package com.schoolmanagement.common.exception;

import java.util.List;

/** Several field-level problems found by a service (for rules Bean Validation cannot express). Mapped to HTTP 400. */
public class FieldValidationException extends RuntimeException {

    private final transient List<FieldError> errors;

    public FieldValidationException(List<FieldError> errors) {
        super("One or more request fields are invalid.");
        this.errors = List.copyOf(errors);
    }

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
