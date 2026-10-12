package com.khedma.auth;

import java.time.Instant;

import com.khedma.user.UserResponse;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 2, max = 150) String displayName) {}

    /** Registration does not sign in; the client must verify the emailed code first. */
    public record RegistrationResponse(String email, Instant codeExpiresAt) {}

    public record VerifyEmailRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record ResendCodeRequest(@NotBlank @Email String email) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {}

    public record GoogleLoginRequest(@NotBlank String idToken) {}

    public record AuthResponse(String accessToken, Instant expiresAt, UserResponse user) {}
}
