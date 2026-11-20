package com.vms.controller;

import com.vms.dto.LoginRequest;
import com.vms.dto.LoginResponse;
import com.vms.dto.RegisterRequest;
import com.vms.service.AuthService;
import com.vms.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;
    private final RegistrationService registration;

    public AuthController(AuthService service, RegistrationService registration) {
        this.service = service;
        this.registration = registration;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.login(request.email(), request.password()));
    }

    /** Creates the account and signs it in, so the caller receives a token straight away. */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(registration.register(request.email(), request.password(), request.registrationCode()));
    }
}
