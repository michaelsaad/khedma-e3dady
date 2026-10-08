package com.khedma.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthDtos.AuthResponse;
import com.khedma.auth.AuthDtos.GoogleLoginRequest;
import com.khedma.auth.AuthDtos.LoginRequest;
import com.khedma.auth.AuthDtos.RegisterRequest;
import com.khedma.auth.AuthDtos.RegistrationResponse;
import com.khedma.auth.AuthDtos.ResendCodeRequest;
import com.khedma.auth.AuthDtos.VerifyEmailRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Step 1: create the account and email a 6-digit code. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Step 2: confirm the code; signs the user in. */
    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request.email(), request.code());
    }

    @PostMapping("/resend-code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendCode(@Valid @RequestBody ResendCodeRequest request) {
        authService.resendCode(request.email());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }
}
