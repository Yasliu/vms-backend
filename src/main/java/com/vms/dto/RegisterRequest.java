package com.vms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 200) String registrationCode) {

    /** Keeps the password and the registration code out of logs and debugging output. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, registrationCode=***]";
    }
}
