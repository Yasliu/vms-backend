package com.vms.service;

import com.vms.dto.LoginResponse;
import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import com.vms.exception.DuplicateEmailException;
import com.vms.exception.InvalidRequestException;
import com.vms.exception.RegistrationNotAllowedException;
import com.vms.repository.AppUserRepository;
import com.vms.security.JwtTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service sign-up, gated by a shared registration code (REGISTRATION_CODE). With no code
 * configured, sign-up is switched off. The code is checked first, so callers without it learn
 * nothing about which emails already have accounts. New accounts get the ADMIN role, the same
 * access every signed-in user has today.
 */
@Service
@Transactional
public class RegistrationService {

    static final int MIN_CODE_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final byte[] registrationCode;

    public RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens,
            @Value("${app.security.registration-code:}") String registrationCode) {
        if (!registrationCode.isBlank() && registrationCode.length() < MIN_CODE_LENGTH) {
            // The value itself is never included in the message.
            throw new IllegalStateException("REGISTRATION_CODE is too short: use at least " + MIN_CODE_LENGTH
                    + " characters, or leave it unset to turn sign-up off.");
        }
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.registrationCode = registrationCode.getBytes(StandardCharsets.UTF_8);
    }

    public LoginResponse register(String email, String password, String code) {
        if (registrationCode.length == 0) {
            throw new RegistrationNotAllowedException("Sign-up is not available right now.");
        }
        if (!MessageDigest.isEqual(registrationCode, code.getBytes(StandardCharsets.UTF_8))) {
            throw new RegistrationNotAllowedException("That registration code is not valid.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > AuthService.MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException("password", "size must be between 12 and 72");
        }

        String normalized = AuthService.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new DuplicateEmailException();
        }

        AppUser saved;
        try {
            saved = users.saveAndFlush(new AppUser(normalized, passwordEncoder.encode(password), UserRole.ADMIN));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent sign-up with the same email won the race; the unique constraint is the final guard.
            throw new DuplicateEmailException(ex);
        }

        JwtTokenService.IssuedToken token = tokens.issue(saved);
        return new LoginResponse(token.value(), "Bearer", token.ttl().toSeconds(), saved.getEmail());
    }
}
