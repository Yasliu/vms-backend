package com.vms.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings for signing and validating access tokens. The secret comes from the JWT_SECRET variable. */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("30m") Duration ttl,
        @DefaultValue("vms-backend") String issuer) {

    /** Never print the signing secret. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, ttl=" + ttl + ", issuer=" + issuer + "]";
    }
}
