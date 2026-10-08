package com.khedma.auth;

import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserStatus;

/** Authenticated principal, loaded fresh from the database on every request. */
public record AuthUser(Long id, String email, String displayName, Role role, UserStatus status) {

    public static AuthUser of(AppUser user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getStatus());
    }
}
