package com.schoolmanagement.common.exception;

/** The request conflicts with current state, for example a duplicate unique value. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
