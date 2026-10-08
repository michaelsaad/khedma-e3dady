package com.khedma.auth;

import java.time.Instant;

import com.khedma.user.UserResponse;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "اكتب الإيميل") @Email(message = "الإيميل غير صحيح") @Size(max = 255) String email,
            @NotBlank(message = "اكتب كلمة السر") @Size(min = 8, max = 72, message = "كلمة السر من 8 إلى 72 حرف") String password,
            @NotBlank(message = "اكتب الاسم") @Size(min = 2, max = 150, message = "الاسم من 2 إلى 150 حرف") String displayName) {}

    public record LoginRequest(
            @NotBlank(message = "اكتب الإيميل") String email,
            @NotBlank(message = "اكتب كلمة السر") String password) {}

    public record GoogleLoginRequest(@NotBlank(message = "رمز جوجل مطلوب") String idToken) {}

    public record AuthResponse(String accessToken, Instant expiresAt, UserResponse user) {}
}
