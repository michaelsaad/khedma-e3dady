package com.khedma.student;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StudentDtos {

    private StudentDtos() {}

    public record StudentRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotNull Grade grade,
            @Size(max = 30) String phone,
            /** null = no group. */
            Long groupId) {}

    public record StudentResponse(
            Long id,
            String name,
            Grade grade,
            String phone,
            Long groupId,
            String groupName,
            int pointsBalance,
            Instant createdAt) {

        public static StudentResponse of(Student s) {
            return new StudentResponse(
                    s.getId(),
                    s.getName(),
                    s.getGrade(),
                    s.getPhone(),
                    s.getGroup() == null ? null : s.getGroup().getId(),
                    s.getGroup() == null ? null : s.getGroup().getName(),
                    s.getPointsBalance(),
                    s.getCreatedAt());
        }
    }
}
