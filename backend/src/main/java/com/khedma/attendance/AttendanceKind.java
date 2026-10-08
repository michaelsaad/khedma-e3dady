package com.khedma.attendance;

public enum AttendanceKind {
    SERVICE("حضور خدمة"),
    MASS("حضور قداس");

    private final String label;

    AttendanceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
