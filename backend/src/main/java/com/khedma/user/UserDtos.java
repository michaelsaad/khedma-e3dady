package com.khedma.user;

import com.khedma.student.Grade;

public final class UserDtos {

    private UserDtos() {}

    /** Either field may be null to leave it unchanged. */
    public record UpdateUserRequest(Role role, UserStatus status) {}

    /** studentId null = unlink. */
    public record LinkStudentRequest(Long studentId) {}

    /** An unlinked active student whose name looks like the account's display name; higher score = closer. */
    public record StudentSuggestion(Long id, String name, Grade grade, double score) {}
}
