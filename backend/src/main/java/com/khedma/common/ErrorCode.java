package com.khedma.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. The frontend maps these to localized (Arabic) text;
 * the English message is a developer-facing fallback.
 */
public enum ErrorCode {
    // Generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Data conflicts with existing records"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission for this action"),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email is already registered"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email address is not verified"),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Email address is already verified"),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "Verification code is invalid"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "Verification code has expired"),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, request a new code"),
    RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code"),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "The verification email could not be sent"),
    GOOGLE_LOGIN_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured"),
    GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Google token could not be verified"),
    GOOGLE_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "Google email is not verified"),

    // Users
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    CANNOT_CHANGE_OWN_ACCESS(HttpStatus.BAD_REQUEST, "You cannot change your own role or status"),
    CANNOT_CHANGE_OWNER_ACCESS(HttpStatus.BAD_REQUEST, "The owner account's access cannot be changed"),
    STUDENT_ALREADY_LINKED(HttpStatus.CONFLICT, "Student is linked to another account"),
    ACCOUNT_NOT_LINKED(HttpStatus.NOT_FOUND, "Your account is not linked to a student"),

    // Students & groups
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Student not found"),
    STUDENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A student with the same name and grade already exists"),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Group not found"),
    GROUP_NAME_TAKEN(HttpStatus.CONFLICT, "Group name is already taken"),

    // Attendance
    INVALID_MEETING_DATE(HttpStatus.BAD_REQUEST, "Date is not a past meeting day in the current season"),
    DUPLICATE_ATTENDANCE_ENTRY(HttpStatus.BAD_REQUEST, "The same student appears more than once"),
    MEETING_SKIPPED(HttpStatus.CONFLICT, "This meeting was skipped; restore it before recording attendance"),

    // Points
    AMOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "Amount is required"),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Balance is too low for this deduction"),
    BALANCE_ALREADY_ZERO(HttpStatus.BAD_REQUEST, "Balance is already zero"),
    REASON_NOT_FOUND(HttpStatus.NOT_FOUND, "Reason not found"),
    REASON_ALREADY_EXISTS(HttpStatus.CONFLICT, "A reason with this text already exists"),

    // Settings
    INVALID_SEASON_RANGE(HttpStatus.BAD_REQUEST, "Season end must be after season start");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
