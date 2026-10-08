package com.khedma.user;

import java.time.LocalDate;
import java.util.List;

import com.khedma.student.StudentDtos.StudentResponse;

public final class UserDtos {

    private UserDtos() {}

    /** Either field may be null to leave it unchanged. */
    public record UpdateUserRequest(Role role, UserStatus status) {}

    /** studentId null = unlink. */
    public record LinkStudentRequest(Long studentId) {}

    public record AttendanceDay(LocalDate date, boolean service, boolean mass) {}

    public record MyStudentResponse(StudentResponse student, List<AttendanceDay> attendance) {}
}
