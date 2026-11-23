package com.schoolmanagement.common.exception;

/** A request that is syntactically valid but uses unacceptable parameters. Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
