package com.vms.exception;

/** A rule Bean Validation cannot express (for example a byte-length limit). Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidRequestException(String field, String reason) {
        super(field + ": " + reason);
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
