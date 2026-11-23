package com.schoolmanagement.common.exception;

/** A well-formed request that violates a domain rule. Mapped to HTTP 422. The message is shown to clients. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
