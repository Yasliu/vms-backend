package com.vms.exception;

/** An account with this email already exists. Mapped to HTTP 409. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with this email already exists.");
    }

    public DuplicateEmailException(Throwable cause) {
        this();
        initCause(cause);
    }
}
