package com.khedma.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.khedma.auth.AuthDtos.AuthResponse;
import com.khedma.auth.AuthDtos.ForgotPasswordRequest;
import com.khedma.auth.AuthDtos.GoogleLoginRequest;
import com.khedma.auth.AuthDtos.LoginCodeRequest;
import com.khedma.auth.AuthDtos.LoginRequest;
import com.khedma.auth.AuthDtos.LoginResendRequest;
import com.khedma.auth.AuthDtos.RegisterRequest;
import com.khedma.auth.AuthDtos.RegistrationResponse;
import com.khedma.auth.AuthDtos.ResendCodeRequest;
import com.khedma.auth.AuthDtos.ResetPasswordRequest;
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
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                    @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.verifyEmail(request.email(), request.code(), userAgent);
    }

    @PostMapping("/resend-code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendCode(@Valid @RequestBody ResendCodeRequest request) {
        authService.resendCode(request.email());
    }

    /** Emails a 6-digit reset code (same response whether or not the account exists). */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
    }

    /** Checks the code, sets the new password and signs the user in (other sessions are signed out). */
    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.resetPassword(request.email(), request.code(), request.newPassword(), userAgent);
    }

    /** 200 with a token on a trusted browser; otherwise 202 with a challenge, and a code is emailed. */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        Object result = authService.login(request);
        return ResponseEntity.status(result instanceof AuthResponse ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result);
    }

    /** Second step of a sign-in from a new browser. */
    @PostMapping("/login/verify")
    public AuthResponse verifyLogin(@Valid @RequestBody LoginCodeRequest request,
                                    @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.verifyLoginCode(request.challengeToken(), request.code(), userAgent);
    }

    @PostMapping("/login/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendLoginCode(@Valid @RequestBody LoginResendRequest request) {
        authService.resendLoginCode(request.challengeToken());
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }
}
