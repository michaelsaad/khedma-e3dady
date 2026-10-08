package com.khedma.user;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl,
        Role role,
        UserStatus status,
        boolean hasPassword,
        boolean hasGoogle,
        Long studentId,
        Instant createdAt,
        Instant lastLoginAt) {

    public static UserResponse of(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getPasswordHash() != null,
                user.getGoogleSubject() != null,
                user.getStudent() == null ? null : user.getStudent().getId(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
