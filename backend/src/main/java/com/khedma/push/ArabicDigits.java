package com.khedma.push;

/** Notifications are shown in Arabic, with Arabic-Indic digits like the rest of the app. */
final class ArabicDigits {

    private ArabicDigits() {}

    static String of(int value) {
        StringBuilder out = new StringBuilder();
        for (char c : String.valueOf(Math.abs(value)).toCharArray()) out.append((char) ('٠' + (c - '0')));
        return (value < 0 ? "-" : "") + out;
    }
}
