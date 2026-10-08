package com.khedma.user;

import java.util.List;

public enum Role {
    /** Regular approved account: sees the dashboard and, if linked, their own student data. */
    USER,
    /** Manages students, groups, attendance, points and app settings. */
    ADMIN,
    /** Everything an ADMIN can do, plus managing users and roles. */
    SUPER_ADMIN;

    /** Roles granted by this role, including itself (simple hierarchy). */
    public List<Role> impliedRoles() {
        return switch (this) {
            case USER -> List.of(USER);
            case ADMIN -> List.of(USER, ADMIN);
            case SUPER_ADMIN -> List.of(USER, ADMIN, SUPER_ADMIN);
        };
    }
}
