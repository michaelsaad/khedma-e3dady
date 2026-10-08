package com.khedma.student;

public enum Grade {
    FIRST("الأول"),
    SECOND("الثاني"),
    THIRD("الثالث");

    private final String arabicName;

    Grade(String arabicName) {
        this.arabicName = arabicName;
    }

    public String arabicName() {
        return arabicName;
    }
}
