package com.khedma.attendance;

public enum AttendanceKind {
    SERVICE("Service attendance"),
    MASS("Mass attendance");

    private final String label;

    AttendanceKind(String label) {
        this.label = label;
    }

    /** English label used in transaction notes, e.g. "Mass attendance on 2026-10-09". */
    public String label() {
        return label;
    }
}
