package com.khedma.user;

public final class UserDtos {

    private UserDtos() {}

    /** Either field may be null to leave it unchanged. */
    public record UpdateUserRequest(Role role, UserStatus status) {}

    /** studentId null = unlink. */
    public record LinkStudentRequest(Long studentId) {}
}
