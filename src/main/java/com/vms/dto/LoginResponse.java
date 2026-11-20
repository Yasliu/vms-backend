package com.vms.dto;

/** {@code expiresIn} is the token lifetime in seconds. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String email) {
}
