package com.khedma.auth.otp;

/** Why a one-time code was emailed. A code only works for the purpose it was sent for. */
public enum CodePurpose {
    VERIFY_EMAIL("كود تأكيد الإيميل", "استخدم الكود ده عشان تأكد إيميلك وتكمّل إنشاء حسابك.", "أكّد إيميلي"),
    RESET_PASSWORD("كود تغيير كلمة السر", "حد طلب تغيير كلمة السر لحسابك. استخدم الكود ده عشان تختار كلمة سر جديدة.", "غيّر كلمة السر"),
    LOGIN("كود تسجيل الدخول", "فيه تسجيل دخول لحسابك من جهاز جديد. استخدم الكود ده عشان تكمّل الدخول.", "كمّل تسجيل الدخول");

    private final String title;
    private final String intro;
    private final String action;

    CodePurpose(String title, String intro, String action) {
        this.title = title;
        this.intro = intro;
        this.action = action;
    }

    public String title() {
        return title;
    }

    public String intro() {
        return intro;
    }

    /** Label of the one-tap button in the email. */
    public String action() {
        return action;
    }
}
