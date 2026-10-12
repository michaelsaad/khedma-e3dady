package com.khedma.auth.otp;

/** Why a one-time code was emailed; only changes the email wording. */
public enum CodePurpose {
    VERIFY_EMAIL("Your Khedma verification code", "Your verification code is"),
    RESET_PASSWORD("Your Khedma password reset code", "Your password reset code is");

    private final String subject;
    private final String intro;

    CodePurpose(String subject, String intro) {
        this.subject = subject;
        this.intro = intro;
    }

    public String subject() {
        return subject;
    }

    public String intro() {
        return intro;
    }
}
