package com.schoolmanagement.common.exception;

/** Credentials or tokens are missing or wrong. The message is shown to clients, so keep it generic. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
