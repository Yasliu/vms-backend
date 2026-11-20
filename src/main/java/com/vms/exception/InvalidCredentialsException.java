package com.vms.exception;

/** Wrong email or password. Deliberately does not say which, to avoid revealing which accounts exist. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
