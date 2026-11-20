package com.vms.service;

import com.vms.dto.LoginResponse;
import com.vms.entity.AppUser;
import com.vms.exception.InvalidCredentialsException;
import com.vms.repository.AppUserRepository;
import com.vms.security.JwtTokenService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies credentials and issues a token. Unknown emails and wrong passwords are indistinguishable
 * to the caller, in both the error returned and (approximately) the time taken: a password hash is
 * checked either way.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    /** BCrypt only uses the first 72 bytes of a password. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final String unknownUserHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.unknownUserHash = passwordEncoder.encode("not-a-real-password");
    }

    public LoginResponse login(String email, String password) {
        Optional<AppUser> user = users.findByEmail(normalizeEmail(email));
        String hash = user.map(AppUser::getPasswordHash).orElse(unknownUserHash);

        boolean passwordMatches = password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES
                && passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        JwtTokenService.IssuedToken token = tokens.issue(user.get());
        return new LoginResponse(token.value(), "Bearer", token.ttl().toSeconds(), user.get().getEmail());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
