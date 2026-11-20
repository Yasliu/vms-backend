package com.vms.exception;

/** Sign-up is switched off, or the registration code is wrong. Mapped to HTTP 403. */
public class RegistrationNotAllowedException extends RuntimeException {

    public RegistrationNotAllowedException(String message) {
        super(message);
    }
}
