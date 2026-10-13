package com.khedma.user;

import java.util.List;

public enum Role {
    /** A kid in the service: only sees their own page (points, attendance) once linked to their student record. */
    MAKHDOUM,
    /** A servant: read-only view of the dashboard, and can write follow-up reports about students. */
    SERVANT,
    /** Manages students, groups, attendance, points, follow-ups and app settings. */
    ADMIN,
    /** Everything an ADMIN can do, plus managing users and roles. */
    SUPER_ADMIN;

    /** Roles granted by this role, including itself. MAKHDOUM is deliberately separate from the servant ladder. */
    public List<Role> impliedRoles() {
        return switch (this) {
            case MAKHDOUM -> List.of(MAKHDOUM);
            case SERVANT -> List.of(SERVANT);
            case ADMIN -> List.of(SERVANT, ADMIN);
            case SUPER_ADMIN -> List.of(SERVANT, ADMIN, SUPER_ADMIN);
        };
    }
}
