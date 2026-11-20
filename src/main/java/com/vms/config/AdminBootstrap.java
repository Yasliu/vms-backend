package com.vms.config;

import com.vms.entity.AppUser;
import com.vms.entity.UserRole;
import com.vms.repository.AppUserRepository;
import com.vms.service.AuthService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from ADMIN_EMAIL and ADMIN_PASSWORD, but only while no user exists. The
 * password is hashed immediately and is never logged. After the first start the two variables can
 * be removed.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap-admin.email:}") String email,
            @Value("${app.security.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No users exist and ADMIN_EMAIL / ADMIN_PASSWORD are not set: nobody can sign in yet.");
            return;
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("ADMIN_EMAIL must be a valid email address.");
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < MIN_PASSWORD_LENGTH || bytes > AuthService.MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("ADMIN_PASSWORD must be between " + MIN_PASSWORD_LENGTH
                    + " characters and " + AuthService.MAX_PASSWORD_BYTES + " bytes long.");
        }
        String normalized = AuthService.normalizeEmail(email);
        users.save(new AppUser(normalized, passwordEncoder.encode(password), UserRole.ADMIN));
        log.info("Created the initial admin account {}.", normalized);
    }
}
