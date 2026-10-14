package com.khedma.push;

import java.util.List;

import com.khedma.user.Role;

/** Who a broadcast goes to. Only approved accounts are ever included. */
public enum Audience {
    ALL(List.of(Role.USER, Role.ADMIN, Role.SUPER_ADMIN)),
    ADMINS(List.of(Role.ADMIN, Role.SUPER_ADMIN));

    private final List<Role> roles;

    Audience(List<Role> roles) {
        this.roles = roles;
    }

    public List<Role> roles() {
        return roles;
    }
}
