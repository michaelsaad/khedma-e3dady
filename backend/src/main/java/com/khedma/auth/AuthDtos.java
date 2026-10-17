package com.khedma.auth;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** {@code deviceToken} is what an earlier sign-in returned on this browser; it skips the emailed code. */
    public record LoginRequest(
            @NotBlank @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password,
            @Size(max = 100) String deviceToken) {}

    /** Password was right but this browser isn't trusted yet: a code was emailed (HTTP 202). */
    public record LoginChallenge(String challengeToken, String email, Instant expiresAt) {}

    public record LoginCodeRequest(
            @NotBlank @Size(max = 2000) String challengeToken,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record LoginResendRequest(@NotBlank @Size(max = 2000) String challengeToken) {}

    public record GoogleLoginRequest(@NotBlank String idToken) {}

    /** {@code deviceToken} is set when this sign-in proved the inbox; the browser keeps it to skip the code later. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(String accessToken, Instant expiresAt, UserResponse user, String deviceToken) {}
}
